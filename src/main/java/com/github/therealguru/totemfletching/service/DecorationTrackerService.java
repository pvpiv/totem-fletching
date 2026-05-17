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

    /** Current decoration item count in the player's inventory. */
    @Getter
    private int inventoryDecorationCount = 0;

    /**
     * How many items the player still needs to fletch to finish the run.
     * Calculated on run-start as max(0, 32 - startingInventory), then decremented
     * each time the inventory count rises (i.e. an item was fletched).
     */
    private int runFletchTarget = 8;

    /** Cumulative count of decoration items fletched since the run started. */
    private int itemsFletched = 0;

    /**
     * Last inventory count snapshot. Used to detect increases (fletching)
     * vs. decreases (placing at totems, banking).
     * -1 means "not yet seen".
     */
    private int lastInventoryCount = -1;

    /** Used to detect the moment the player enters Auburnvale. */
    private boolean wasInAuburnvale = false;

    public void onItemContainerChanged(ItemContainerChanged event) {
        if (event.getContainerId() != InventoryID.INVENTORY.getId()) {
            return;
        }

        int newCount = countDecorations(event.getItemContainer());

        // Only count increases — those represent items just fletched.
        // Decreases happen when placing decorations at totems (expected) or banking.
        if (lastInventoryCount >= 0 && newCount > lastInventoryCount) {
            itemsFletched += (newCount - lastInventoryCount);
            log.debug("Fletched +{}, total this run: {}", newCount - lastInventoryCount, itemsFletched);
        }

        lastInventoryCount = newCount;
        inventoryDecorationCount = newCount;
        log.debug("Decoration items in inventory: {}", inventoryDecorationCount);
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
        return Math.max(0, runFletchTarget - itemsFletched);
    }

    private void startRun() {
        runFletchTarget = Math.max(0, TOTAL_DECORATIONS_NEEDED - inventoryDecorationCount);
        itemsFletched = 0;
        log.debug("Run started — inventory: {}, target to fletch: {}", inventoryDecorationCount, runFletchTarget);
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
