package com.github.therealguru.totemfletching.service;

import java.util.Set;
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

    /** Decorations required to fully deck all 8 totems (8 totems × 4 decorations). */
    static final int TOTAL_DECORATIONS_NEEDED = 32;

    /** Decoration items in the player's main inventory (shown as "In bag"). */
    @Getter
    private int inventoryDecorationCount = 0;

    /** Decoration items currently equipped (e.g. redwood hiking staff, shield). */
    private int equippedDecorationCount = 0;

    /**
     * How many items the player still needs to fletch to finish the run.
     * Calculated on run-start as max(0, 32 - startingTotal), then decremented
     * each time the combined total (inventory + equipped) rises.
     */
    private int runFletchTarget = 8;

    /** Cumulative count of new decoration items obtained since the run started. */
    private int itemsObtained = 0;

    /**
     * Last known combined total (inventory + equipped).
     * Only increases in this total count as "new items obtained" (fletched, traded, picked up).
     * Equipping/unequipping moves items between containers — total unchanged, no count change.
     * -1 means "not yet seen".
     */
    private int lastTotalCount = -1;

    /** Used to detect the moment the player enters Auburnvale. */
    private boolean wasInAuburnvale = false;

    public void onItemContainerChanged(ItemContainerChanged event) {
        int containerId = event.getContainerId();
        if (containerId == InventoryID.INVENTORY.getId()) {
            inventoryDecorationCount = countDecorations(event.getItemContainer());
            log.debug("Decoration items in inventory: {}", inventoryDecorationCount);
        } else if (containerId == InventoryID.EQUIPMENT.getId()) {
            equippedDecorationCount = countDecorations(event.getItemContainer());
            log.debug("Decoration items equipped: {}", equippedDecorationCount);
        } else {
            return;
        }

        int newTotal = inventoryDecorationCount + equippedDecorationCount;

        // Only count increases in the combined total.
        // Equipping/unequipping shifts between containers but leaves total unchanged.
        if (lastTotalCount >= 0 && newTotal > lastTotalCount) {
            itemsObtained += (newTotal - lastTotalCount);
            log.debug("New decorations obtained +{}, total this run: {}", newTotal - lastTotalCount, itemsObtained);
        }

        lastTotalCount = newTotal;
    }

    /**
     * Called each game tick with the player's current Auburnvale status.
     * Automatically starts a new run when the player enters the area.
     */
    public void updateAuburnvaleState(boolean isInAuburnvale) {
        if (isInAuburnvale && !wasInAuburnvale) {
            startRun();
        }
        wasInAuburnvale = isInAuburnvale;
    }

    /** Manually reset the run counter (e.g. via right-click on the overlay). */
    public void resetRun() {
        startRun();
    }

    /** How many more items the player needs to fletch to complete the current run. */
    public int getItemsToFletch() {
        return Math.max(0, runFletchTarget - itemsObtained);
    }

    private void startRun() {
        int startingTotal = inventoryDecorationCount + equippedDecorationCount;
        runFletchTarget = Math.max(0, TOTAL_DECORATIONS_NEEDED - startingTotal);
        itemsObtained = 0;
        log.debug("Run started — total decorations: {}, target to fletch: {}", startingTotal, runFletchTarget);
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
