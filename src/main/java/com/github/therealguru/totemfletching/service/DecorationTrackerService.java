package com.github.therealguru.totemfletching.service;

import com.github.therealguru.totemfletching.model.Totem;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.ItemContainerChanged;

@Slf4j
@Singleton
public class DecorationTrackerService {

    /**
     * All item IDs that count as valid totem decorations.
     * Includes all log tiers for: stocks, unstrung longbows, strung longbows,
     * and the two redwood-tier decorations.
     */
    static final Set<Integer> DECORATION_ITEM_IDS = Set.of(
            // Stocks (crossbow stocks double as Vale Totem decorations)
            9442,  // Oak stock
            9444,  // Willow stock
            9448,  // Maple stock
            9452,  // Yew stock
            21952, // Magic stock

            // Unstrung longbows
            56,  // Oak longbow (u)
            58,  // Willow longbow (u)
            62,  // Maple longbow (u)
            66,  // Yew longbow (u)
            70,  // Magic longbow (u)

            // Strung longbows
            845, // Oak longbow
            847, // Willow longbow
            851, // Maple longbow
            855, // Yew longbow
            859, // Magic longbow

            // Redwood tier
            31049, // Redwood hiking staff
            22266  // Redwood shield
    );

    private static final int DECORATIONS_PER_TOTEM = 4;
    private static final int TOTEM_COUNT = 8;

    @Inject
    private TotemService totemService;

    /** Decoration items in the player's main inventory. */
    @Getter
    private int inventoryDecorationCount = 0;

    /** Decoration items currently equipped (e.g. redwood hiking staff, shield). */
    private int equippedDecorationCount = 0;

    /**
     * IDs of totems fully decorated (decoration == 4) during this run.
     * Populated by detecting varbit transitions to 4 each game tick.
     * Formula: to_fletch = (8 - size) * 4 - inHand
     */
    private final Set<Integer> totemsDoneThisRun = new HashSet<>();

    /**
     * Per-totem decoration count from the previous game tick.
     * Used to detect the exact tick a totem reaches decoration == 4.
     */
    private final Map<Integer, Integer> lastDecorationState = new HashMap<>();

    public void onItemContainerChanged(ItemContainerChanged event) {
        int containerId = event.getContainerId();
        if (containerId == InventoryID.INVENTORY.getId()) {
            inventoryDecorationCount = countDecorations(event.getItemContainer());
            log.debug("Decoration items in inventory: {}", inventoryDecorationCount);
        } else if (containerId == InventoryID.EQUIPMENT.getId()) {
            equippedDecorationCount = countDecorations(event.getItemContainer());
            log.debug("Decoration items equipped: {}", equippedDecorationCount);
        }
    }

    /**
     * Called every game tick. Detects when a totem transitions to full decoration (4)
     * and records it in {@code totemsDoneThisRun}.
     *
     * <p>When all 8 totems are detected as done and a totem hits 4 again (via decay + rebuild),
     * the run counter auto-resets so the new run is tracked correctly.
     */
    public void onGameTick() {
        for (Totem totem : totemService.getTotems()) {
            int totemId = totem.getTotemId();
            int current = totem.getDecoration();
            Integer last = lastDecorationState.get(totemId);

            if (last != null && last < DECORATIONS_PER_TOTEM && current == DECORATIONS_PER_TOTEM) {
                if (totemsDoneThisRun.size() == TOTEM_COUNT && totemsDoneThisRun.contains(totemId)) {
                    // This totem was done in run 1, now done again → new run starting
                    log.debug("New run detected at totem {} — auto-resetting", totemId);
                    totemsDoneThisRun.clear();
                }
                if (!totemsDoneThisRun.contains(totemId)) {
                    totemsDoneThisRun.add(totemId);
                    log.debug("Totem {} fully decorated — done this run: {}/{}",
                            totemId, totemsDoneThisRun.size(), TOTEM_COUNT);
                }
            }
            lastDecorationState.put(totemId, current);
        }
    }

    /**
     * Returns how many more items need to be fletched to finish the run.
     * Formula: (totems remaining × 4) − items in hand.
     *
     * <p>Banking, equipping, and totem decay do not affect this value — only
     * actually placing decorations (completing a totem) decrements it.
     */
    public int getItemsToFletch() {
        int remaining = TOTEM_COUNT - totemsDoneThisRun.size();
        int inHand = inventoryDecorationCount + equippedDecorationCount;
        int result = Math.max(0, remaining * DECORATIONS_PER_TOTEM - inHand);
        log.debug("To fletch: {} (totemsRemaining={}, inHand={})", result, remaining, inHand);
        return result;
    }

    /**
     * Manual reset via right-click on the overlay.
     * Syncs {@code totemsDoneThisRun} to the set of totems currently at full decoration,
     * so the formula correctly reflects mid-run state after a login or unexpected count drift.
     */
    public void resetRun() {
        totemsDoneThisRun.clear();
        for (Totem totem : totemService.getTotems()) {
            if (totem.getDecoration() == DECORATIONS_PER_TOTEM) {
                totemsDoneThisRun.add(totem.getTotemId());
            }
        }
        lastDecorationState.clear();
        log.debug("Run reset — synced to current state: {}/{} totems done",
                totemsDoneThisRun.size(), TOTEM_COUNT);
    }

    private int countDecorations(ItemContainer container) {
        int count = 0;
        for (Item item : container.getItems()) {
            if (item != null && DECORATION_ITEM_IDS.contains(item.getId())) {
                count += item.getQuantity();
            }
        }
        return count;
    }
}
