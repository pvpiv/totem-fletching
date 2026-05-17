package com.github.therealguru.totemfletching.overlay;

import com.github.therealguru.totemfletching.TotemFletchingConfig;
import com.github.therealguru.totemfletching.TotemFletchingPlugin;
import com.github.therealguru.totemfletching.model.TotemRegion;
import com.github.therealguru.totemfletching.service.DecorationTrackerService;
import com.github.therealguru.totemfletching.service.TotemService;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.ComponentOrientation;
import net.runelite.client.ui.overlay.components.ImageComponent;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

public class DecorationTrackerOverlay extends OverlayPanel {

    private static final Color COLOR_READY = Color.decode("#9CF575");
    private static final Color COLOR_WARN = Color.decode("#FFD700");
    private static final Color COLOR_DANGER = Color.decode("#E45F5F");

    private final Client client;
    private final TotemFletchingConfig config;
    private final DecorationTrackerService trackerService;
    private final TotemService totemService;
    private final ItemManager itemManager;

    @Inject
    public DecorationTrackerOverlay(
            TotemFletchingPlugin plugin,
            TotemFletchingConfig config,
            DecorationTrackerService trackerService,
            TotemService totemService,
            ItemManager itemManager,
            Client client) {
        super(plugin);
        this.client = client;
        this.config = config;
        this.trackerService = trackerService;
        this.totemService = totemService;
        this.itemManager = itemManager;
        setPosition(OverlayPosition.TOP_RIGHT);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!config.showDecorationTracker()) {
            return null;
        }

        if (!TotemRegion.isInsideAuburnvale(client.getLocalPlayer().getWorldLocation())) {
            return null;
        }

        int inInventory = trackerService.getInventoryDecorationCount();
        int toFletch = trackerService.getItemsToFletch(totemService.getTotems());

        // Knife icon header row
        BufferedImage knifeIcon = itemManager.getImage(ItemID.KNIFE);
        if (knifeIcon != null) {
            PanelComponent iconRow = new PanelComponent();
            iconRow.setOrientation(ComponentOrientation.HORIZONTAL);
            iconRow.getChildren().add(new ImageComponent(knifeIcon));
            iconRow.setBackgroundColor(new Color(0, 0, 0, 0));
            panelComponent.getChildren().add(iconRow);
        }

        panelComponent.getChildren().add(
                TitleComponent.builder().text("Decorations").build());

        panelComponent.getChildren().add(
                LineComponent.builder()
                        .left("In inventory:")
                        .right(String.valueOf(inInventory))
                        .rightColor(inInventory >= 4 ? COLOR_READY : COLOR_WARN)
                        .build());

        Color toFletchColor;
        if (toFletch == 0) {
            toFletchColor = COLOR_READY;
        } else if (toFletch <= 4) {
            toFletchColor = COLOR_WARN;
        } else {
            toFletchColor = COLOR_DANGER;
        }

        panelComponent.getChildren().add(
                LineComponent.builder()
                        .left("Still to fletch:")
                        .right(String.valueOf(toFletch))
                        .rightColor(toFletchColor)
                        .build());

        return super.render(graphics);
    }
}
