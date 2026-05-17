package com.github.therealguru.totemfletching.service;

import com.github.therealguru.totemfletching.model.Totem;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.NPC;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.NpcChanged;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;

@Slf4j
@Singleton
public class EntVisitService {

    /** NPC IDs for the Ent (Vale Totems). Normal = 14634, Buffed = 14635. */
    private static final List<Integer> ENT_NPC_IDS = List.of(14634, 14635);

    /**
     * How close (in tiles) the Ent must be to a totem to trigger the visit timer.
     * The Ent stops directly on or next to the totem game object.
     */
    private static final int TOTEM_PROXIMITY_TILES = 5;

    /**
     * Duration of an Ent offering visit in game ticks.
     * The Ent stays at the totem for approximately 14 ticks before moving on.
     * Adjust this constant if in-game testing shows a different value.
     */
    public static final int ENT_VISIT_DURATION_TICKS = 14;

    private final TotemService totemService;

    /**
     * Maps each active Ent NPC ID → the totem it is visiting and ticks remaining.
     */
    private final Map<Integer, EntVisit> activeVisits = new HashMap<>();

    /**
     * Tracks which NPCs are Ents currently near totems, keyed by NPC hash code.
     */
    private final Map<Integer, NPC> trackedEnts = new HashMap<>();

    @Inject
    public EntVisitService(TotemService totemService) {
        this.totemService = totemService;
    }

    /**
     * Returns current active Ent visits: maps each visited Totem → ticks remaining.
     */
    public Map<Totem, Integer> getActiveEntVisits() {
        Map<Totem, Integer> result = new HashMap<>();
        for (EntVisit visit : activeVisits.values()) {
            result.put(visit.totem, visit.ticksRemaining);
        }
        return result;
    }

    public void onNpcSpawned(NpcSpawned event) {
        NPC npc = event.getNpc();
        if (!isEnt(npc)) return;

        trackedEnts.put(System.identityHashCode(npc), npc);
        log.debug("[EntVisit] Ent spawned: id={} animId={}",
                npc.getId(), npc.getAnimation());
    }

    public void onNpcChanged(NpcChanged event) {
        NPC npc = event.getNpc();
        if (!isEnt(npc)) return;

        log.debug("[EntVisit] Ent changed: id={} animId={}",
                npc.getId(), npc.getAnimation());
    }

    public void onNpcDespawned(NpcDespawned event) {
        NPC npc = event.getNpc();
        if (!isEnt(npc)) return;

        int key = System.identityHashCode(npc);
        trackedEnts.remove(key);
        activeVisits.remove(key);
        log.debug("[EntVisit] Ent despawned: id={}", npc.getId());
    }

    /**
     * Called every game tick. Updates timers and detects new Ent visits via proximity.
     */
    public void onGameTick(GameTick event) {
        List<Totem> totems = totemService.getTotems();

        for (Map.Entry<Integer, NPC> entry : trackedEnts.entrySet()) {
            int entKey = entry.getKey();
            NPC ent = entry.getValue();

            if (ent.getWorldLocation() == null) continue;

            Optional<Totem> nearbyTotem = findNearbyTotem(ent, totems);

            if (nearbyTotem.isPresent()) {
                Totem totem = nearbyTotem.get();
                EntVisit existing = activeVisits.get(entKey);

                if (existing == null || !existing.totem.equals(totem)) {
                    // Ent just arrived at this totem — start a fresh countdown
                    activeVisits.put(entKey, new EntVisit(totem, ENT_VISIT_DURATION_TICKS));
                    log.debug("[EntVisit] Ent {} started visiting totem {} — {} ticks",
                            ent.getId(), totem.getTotemId(), ENT_VISIT_DURATION_TICKS);
                } else {
                    // Ent is still at the same totem — count down
                    existing.ticksRemaining--;
                    if (existing.ticksRemaining <= 0) {
                        activeVisits.remove(entKey);
                        log.debug("[EntVisit] Ent {} finished visiting totem {}",
                                ent.getId(), totem.getTotemId());
                    }
                }
            } else {
                // Ent moved away — clear any active visit for it
                if (activeVisits.containsKey(entKey)) {
                    log.debug("[EntVisit] Ent {} left totem area early",
                            ent.getId());
                    activeVisits.remove(entKey);
                }
            }
        }
    }

    public void clearVisits() {
        activeVisits.clear();
        trackedEnts.clear();
    }

    private Optional<Totem> findNearbyTotem(NPC ent, List<Totem> totems) {
        return totems.stream()
                .filter(t -> t.getTotemGameObject() != null)
                .filter(t -> t.getTotemGameObject()
                        .getWorldLocation()
                        .distanceTo(ent.getWorldLocation()) <= TOTEM_PROXIMITY_TILES)
                .findFirst();
    }

    private boolean isEnt(NPC npc) {
        return ENT_NPC_IDS.contains(npc.getId());
    }

    /** Simple data holder for an active Ent visit. */
    private static class EntVisit {
        final Totem totem;
        int ticksRemaining;

        EntVisit(Totem totem, int ticksRemaining) {
            this.totem = totem;
            this.ticksRemaining = ticksRemaining;
        }
    }
}
