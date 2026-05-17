package com.github.therealguru.totemfletching.service;

import com.github.therealguru.totemfletching.model.Totem;
import java.util.List;
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

    /** Total decorations needed for a complete 8-totem run. */
    static final int DECORATIONS_PER_TOTEM = 4;

    @Getter
    private int inventoryDecorationCount = 0;

    /**
     * Called when the inventory container changes. Counts decoration items in inventory.
     */
    public void onItemContainerChanged(ItemContainerChanged event) {
        ItemContainer container = event.getItemContainer();
        if (event.getContainerId() != InventoryID.INVENTORY.getId()) {
            return;
        }

        int count = 0;
        for (Item item : container.getItems()) {
            if (item != null && DECORATION_ITEM_IDS.contains(item.getId())) {
                count += item.getQuantity();
            }
        }
        inventoryDecorationCount = count;
        log.debug("Decoration items in inventory: {}", inventoryDecorationCount);
    }

    /**
     * Returns how many decoration items still need to be fletched to finish all
     * remaining totems. Accounts for partial decorations already on each totem.
     *
     * <p>Formula: max(0, Σ(4 - totem.decoration) for all totems - inventory_count)
     */
    public int getItemsToFletch(List<Totem> totems) {
        int totalNeeded = totems.stream()
                .mapToInt(t -> DECORATIONS_PER_TOTEM - Math.min(DECORATIONS_PER_TOTEM, t.getDecoration()))
                .sum();
        return Math.max(0, totalNeeded - inventoryDecorationCount);
    }
}
