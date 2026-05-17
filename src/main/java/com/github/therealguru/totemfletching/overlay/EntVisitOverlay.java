package com.github.therealguru.totemfletching.overlay;

import com.github.therealguru.totemfletching.TotemFletchingConfig;
import com.github.therealguru.totemfletching.TotemFletchingPlugin;
import com.github.therealguru.totemfletching.model.Totem;
import com.github.therealguru.totemfletching.model.TotemRegion;
import com.github.therealguru.totemfletching.service.EntVisitService;
import java.awt.*;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

public class EntVisitOverlay extends Overlay {

    /** Diameter of the circular timer in pixels. */
    private static final int TIMER_DIAMETER = 30;

    /** Offset in pixels above the totem canvas text location to draw the timer. */
    private static final int TIMER_CANVAS_OFFSET = 220;

    private static final Color COLOR_FULL    = Color.decode("#9CF575"); // green  (plenty of time)
    private static final Color COLOR_MID     = Color.decode("#FFD700"); // yellow (mid)
    private static final Color COLOR_LOW     = Color.decode("#E45F5F"); // red    (almost out)
    private static final Color COLOR_BG      = new Color(0, 0, 0, 160);
    private static final Color COLOR_BORDER  = new Color(255, 255, 255, 180);

    private final Client client;
    private final TotemFletchingConfig config;
    private final EntVisitService entVisitService;

    @Inject
    public EntVisitOverlay(
            TotemFletchingPlugin plugin,
            TotemFletchingConfig config,
            EntVisitService entVisitService,
            Client client) {
        super(plugin);
        this.client = client;
        this.config = config;
        this.entVisitService = entVisitService;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!config.showEntVisitTimer()) {
            return null;
        }

        if (!TotemRegion.isInsideAuburnvale(client.getLocalPlayer().getWorldLocation())) {
            return null;
        }

        Map<Totem, Integer> visits = entVisitService.getActiveEntVisits();
        for (Map.Entry<Totem, Integer> entry : visits.entrySet()) {
            renderEntTimer(graphics, entry.getKey(), entry.getValue());
        }

        return null;
    }

    private void renderEntTimer(Graphics2D graphics, Totem totem, int ticksRemaining) {
        if (totem.getTotemGameObject() == null) return;

        // Get canvas position above the totem
        Point canvasPoint = totem.getTotemGameObject()
                .getCanvasTextLocation(graphics, "", TIMER_CANVAS_OFFSET);
        if (canvasPoint == null) return;

        int cx = canvasPoint.getX() - TIMER_DIAMETER / 2;
        int cy = canvasPoint.getY() - TIMER_DIAMETER / 2;

        int total = EntVisitService.ENT_VISIT_DURATION_TICKS;
        int remaining = Math.max(0, ticksRemaining);

        // Fraction of the circle still remaining (1.0 = full, 0.0 = done)
        float fraction = (float) remaining / total;

        // Background circle
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(COLOR_BG);
        graphics.fillOval(cx, cy, TIMER_DIAMETER, TIMER_DIAMETER);

        // Coloured arc — starts at 12 o'clock (90° in Java coords).
        // Positive arcAngle = counterclockwise in Java, so the arc occupies the LEFT side first.
        // This means the RIGHT side drains first = clockwise drain from the viewer's perspective.
        int sweepDegrees = Math.round(360 * fraction);
        graphics.setColor(getTimerColor(fraction));
        graphics.fillArc(cx, cy, TIMER_DIAMETER, TIMER_DIAMETER,
                90, sweepDegrees);  // positive = clockwise drain effect

        // White border
        graphics.setColor(COLOR_BORDER);
        graphics.setStroke(new BasicStroke(1.5f));
        graphics.drawOval(cx, cy, TIMER_DIAMETER, TIMER_DIAMETER);

        // Seconds remaining, centred inside the circle. 100 ticks/min = 0.6 s/tick.
        int secondsRemaining = (int) Math.ceil(remaining / EntVisitService.TICKS_PER_SECOND);
        String text = secondsRemaining + "s";
        FontMetrics fm = graphics.getFontMetrics();
        int textX = cx + (TIMER_DIAMETER - fm.stringWidth(text)) / 2;
        int textY = cy + (TIMER_DIAMETER + fm.getAscent() - fm.getDescent()) / 2;

        graphics.setColor(Color.BLACK);
        graphics.drawString(text, textX + 1, textY + 1); // shadow
        graphics.setColor(Color.WHITE);
        graphics.drawString(text, textX, textY);
    }

    /**
     * Returns green → yellow → red based on how much time is left.
     * > 50%: green, 25–50%: yellow, < 25%: red.
     */
    private Color getTimerColor(float fraction) {
        if (fraction > 0.5f) return COLOR_FULL;
        if (fraction > 0.25f) return COLOR_MID;
        return COLOR_LOW;
    }
}
