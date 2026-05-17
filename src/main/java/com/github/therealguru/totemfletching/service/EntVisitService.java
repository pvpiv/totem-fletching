package com.github.therealguru.totemfletching.service;

import com.github.therealguru.totemfletching.model.Totem;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.NPC;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.NpcChanged;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;

@Slf4j
@Singleton
public class EntVisitService {

    /** NPC IDs for the Ent (Vale Totems). Normal = 14634, Buffed = 14635. */
    private static final List<Integer> ENT_NPC_IDS = List.of(14634, 14635);

    /** How close (in tiles) the Ent must be to a totem to trigger the visit timer. */
    private static final int TOTEM_PROXIMITY_TILES = 3;

    /**
     * Duration of an Ent offering visit in game ticks.
     * Observed: departure animation (animId=12511) fires ~9 seconds after proximity
     * detection, which is 15 ticks at 0.6 s/tick.
     * The countdown hitting 0 aligns with the offerings being granted and the Ent leaving.
     */
    public static final int ENT_VISIT_DURATION_TICKS = 15;

    /** Game ticks per minute — 100 ticks/min = 0.6 s/tick. */
    public static final double TICKS_PER_SECOND = 100.0 / 60.0;

    /** Animation ID the Ent plays when it begins to leave after granting offerings. */
    private static final int ENT_DEPARTURE_ANIM = 12511;

    /**
     * After the departure animation fires, suppress a new visit for this many ticks.
     * The Ent lingers ~8 ticks after the departure anim before leaving the area;
     * 20 ticks gives comfortable clearance so the timer never restarts.
     */
    private static final int POST_VISIT_COOLDOWN_TICKS = 20;

    private final TotemService totemService;

    /** Active visit countdowns: entKey → EntVisit */
    private final Map<Integer, EntVisit> activeVisits = new HashMap<>();

    /** All Ent NPCs currently in render range: entKey → NPC */
    private final Map<Integer, NPC> trackedEnts = new HashMap<>();

    /**
     * Per-Ent cooldown after a visit naturally ends (reaches 0).
     * Prevents the timer restarting immediately while the Ent lingers nearby.
     * entKey → ticks of cooldown remaining
     */
    private final Map<Integer, Integer> postVisitCooldown = new HashMap<>();

    /**
     * Last known position of each tracked Ent.
     * Used to detect when the Ent has stopped moving (i.e. arrived at the totem platform).
     * The timer only starts once the Ent is stationary — prevents false-triggers
     * from the Ent walking past within proximity while on the approach ramp.
     */
    private final Map<Integer, WorldPoint> lastEntPositions = new HashMap<>();

    @Inject
    public EntVisitService(TotemService totemService) {
        this.totemService = totemService;
    }

    /** Returns active Ent visits as Totem → ticks remaining. */
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

        int key = System.identityHashCode(npc);
        trackedEnts.put(key, npc);
        log.debug("[EntVisit] Ent spawned: id={} animId={}", npc.getId(), npc.getAnimation());
    }

    public void onNpcChanged(NpcChanged event) {
        NPC npc = event.getNpc();
        if (!isEnt(npc)) return;

        log.debug("[EntVisit] Ent changed: id={} animId={}", npc.getId(), npc.getAnimation());

        if (npc.getAnimation() == ENT_DEPARTURE_ANIM) {
            int key = System.identityHashCode(npc);
            boolean hadActiveVisit = activeVisits.remove(key) != null;
            postVisitCooldown.put(key, POST_VISIT_COOLDOWN_TICKS);
            log.debug("[EntVisit] Ent {} departure anim detected — hadActiveVisit={}, cooldown {} ticks",
                    npc.getId(), hadActiveVisit, POST_VISIT_COOLDOWN_TICKS);
        }
    }

    public void onNpcDespawned(NpcDespawned event) {
        NPC npc = event.getNpc();
        if (!isEnt(npc)) return;

        int key = System.identityHashCode(npc);
        trackedEnts.remove(key);
        activeVisits.remove(key);
        postVisitCooldown.remove(key);
        lastEntPositions.remove(key);
        log.debug("[EntVisit] Ent despawned: id={}", npc.getId());
    }

    /** Called every game tick. Updates all timers and detects new Ent visits via proximity. */
    public void onGameTick(GameTick event) {
        List<Totem> totems = totemService.getTotems();

        // Tick down all post-visit cooldowns; remove expired ones
        Set<Integer> expiredCooldowns = new HashSet<>();
        for (Map.Entry<Integer, Integer> entry : postVisitCooldown.entrySet()) {
            int remaining = entry.getValue() - 1;
            if (remaining <= 0) {
                expiredCooldowns.add(entry.getKey());
            } else {
                entry.setValue(remaining);
            }
        }
        expiredCooldowns.forEach(postVisitCooldown::remove);

        for (Map.Entry<Integer, NPC> entry : trackedEnts.entrySet()) {
            int entKey = entry.getKey();
            NPC ent = entry.getValue();

            WorldPoint currentPos = ent.getWorldLocation();
            if (currentPos == null) continue;

            // Track whether the Ent moved this tick
            WorldPoint lastPos = lastEntPositions.get(entKey);
            lastEntPositions.put(entKey, currentPos);
            boolean isStationary = lastPos != null && lastPos.equals(currentPos);

            Optional<Totem> nearbyTotem = findNearbyTotem(ent, totems);

            if (nearbyTotem.isPresent()) {
                Totem totem = nearbyTotem.get();
                EntVisit existing = activeVisits.get(entKey);

                if (existing == null || !existing.totem.equals(totem)) {
                    // Only start the timer once the Ent has stopped moving.
                    // While on the ramp/approach the Ent moves each tick, so isStationary=false.
                    if (isStationary && !postVisitCooldown.containsKey(entKey)) {
                        activeVisits.put(entKey, new EntVisit(totem, ENT_VISIT_DURATION_TICKS));
                        log.debug("[EntVisit] Ent {} started visiting totem {} — {} ticks (animId={})",
                                ent.getId(), totem.getTotemId(), ENT_VISIT_DURATION_TICKS,
                                ent.getAnimation());
                    } else if (!isStationary) {
                        log.debug("[EntVisit] Ent {} near totem {} but still moving — waiting",
                                ent.getId(), totem.getTotemId());
                    }
                } else {
                    log.debug("[EntVisit] Ent {} tick {} remaining, animId={}",
                            ent.getId(), existing.ticksRemaining, ent.getAnimation());
                    existing.ticksRemaining--;
                    if (existing.ticksRemaining <= 0) {
                        activeVisits.remove(entKey);
                        postVisitCooldown.put(entKey, POST_VISIT_COOLDOWN_TICKS);
                        log.debug("[EntVisit] Ent {} finished visiting totem {} — cooldown started",
                                ent.getId(), totem.getTotemId());
                    }
                }
            } else {
                // Ent moved away — clear any active visit for it (left early)
                if (activeVisits.containsKey(entKey)) {
                    activeVisits.remove(entKey);
                    postVisitCooldown.put(entKey, POST_VISIT_COOLDOWN_TICKS);
                    log.debug("[EntVisit] Ent {} left totem area early", ent.getId());
                }
            }
        }
    }

    public void clearVisits() {
        activeVisits.clear();
        trackedEnts.clear();
        postVisitCooldown.clear();
        lastEntPositions.clear();
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
