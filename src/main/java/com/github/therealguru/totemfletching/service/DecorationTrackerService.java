package com.github.therealguru.totemfletching.service;

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

    /** Decorations required per totem site (always 4). */
    private static final int DECORATIONS_PER_TOTEM = 4;

    @Inject
    private TotemService totemService;

    /** Decoration items in the player's main inventory (shown as "In bag"). */
    @Getter
    private int inventoryDecorationCount = 0;

    /** Decoration items currently equipped (e.g. redwood hiking staff, shield). */
    private int equippedDecorationCount = 0;

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
     * Returns how many more decoration items need to be fletched to fully decorate all totems.
     *
     * <p>Formula: Σ(4 - totem.decoration) for all 8 totems, minus items already in hand
     * (inventory + equipped). This stays accurate at all times — during banking, equipping,
     * unequipping, and totem decay — because it is derived entirely from live game state.
     */
    public int getItemsToFletch() {
        int totalNeeded = totemService.getTotems().stream()
                .mapToInt(t -> Math.max(0, DECORATIONS_PER_TOTEM - t.getDecoration()))
                .sum();
        int inHand = inventoryDecorationCount + equippedDecorationCount;
        int result = Math.max(0, totalNeeded - inHand);
        log.debug("To fletch: {} (totalNeeded={}, inHand={})", result, totalNeeded, inHand);
        return result;
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
