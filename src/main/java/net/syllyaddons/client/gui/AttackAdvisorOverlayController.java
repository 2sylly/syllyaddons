package net.syllyaddons.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.syllyaddons.advisor.AttackAdvisorService;
import net.syllyaddons.advisor.AttackAdvisorView;
import net.syllyaddons.advisor.AttackRouteEstimate;
import net.syllyaddons.advisor.QueueTimerValidation;
import net.syllyaddons.config.SyllyConfig;
import net.syllyaddons.config.SyllyConfigService;

/** Read-only Track 9 panel. It contains no controls and cannot perform guild actions. */
public final class AttackAdvisorOverlayController {
    private static final long QUEUE_VALIDATION_MILLIS = 10_000;
    private static final long PASSIVE_HISTORY_MILLIS = 30_000;
    private static Supplier<AttackAdvisorService> serviceSupplier;
    private static Supplier<SyllyConfigService> settingsSupplier;
    private static boolean registered;

    private AttackAdvisorOverlayController() {}

    public static synchronized void register(
            Supplier<AttackAdvisorService> service,
            Supplier<SyllyConfigService> settings) {
        serviceSupplier = Objects.requireNonNull(service, "service");
        settingsSupplier = Objects.requireNonNull(settings, "settings");
        if (registered) return;
        registered = true;
        ScreenEvents.BEFORE_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!isAttackScreen(screen)) return;
            ScreenEvents.afterRender(screen).register((ignored, graphics, mouseX, mouseY, tickDelta) ->
                    render(graphics, true));
        });
        HudRenderCallback.EVENT.register((graphics, tickCounter) -> {
            if (Minecraft.getInstance().screen == null) render(graphics, false);
        });
    }

    private static void render(GuiGraphics graphics, boolean attackScreen) {
        AttackAdvisorService service = serviceSupplier.get();
        SyllyConfigService settings = settingsSupplier.get();
        if (service == null || settings == null) return;
        SyllyConfig config = settings.snapshot();
        if (!config.routingAdvisorEnabled()) return;
        AttackAdvisorView view = service.latest().orElse(null);
        if (view == null) return;
        long now = System.currentTimeMillis();
        QueueTimerValidation validation = view.queueValidation();
        boolean recentQueue = validation != null && now - validation.observedAtEpochMillis() <= QUEUE_VALIDATION_MILLIS;
        if (!attackScreen && !recentQueue) {
            if (config.routingAdvisor().activeOperationsOnly()) return;
            if (now - view.updatedAtEpochMillis() > PASSIVE_HISTORY_MILLIS) return;
        }
        drawPanel(graphics, view, recentQueue);
    }

    private static void drawPanel(GuiGraphics graphics, AttackAdvisorView view, boolean showValidation) {
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        int width = Math.min(316, Math.max(190, screenWidth - 16));
        List<Line> lines = lines(view, showValidation);
        int height = 15 + lines.size() * 11;
        int x = Math.max(8, screenWidth - width - 8);
        int y = Math.min(8, Math.max(0, screenHeight - height));
        graphics.fill(x, y, x + width, y + height, 0xE5101622);
        graphics.fill(x, y, x + 3, y + height, 0xFF79A9F5);
        graphics.fill(x + 3, y, x + width, y + 1, 0xFF344158);
        int textY = y + 6;
        for (Line line : lines) {
            graphics.drawString(font, trim(font, line.text(), width - 16), x + 9, textY, line.color(), false);
            textY += 11;
        }
    }

    private static List<Line> lines(AttackAdvisorView view, boolean showValidation) {
        List<Line> lines = new ArrayList<>();
        var advice = view.advice();
        lines.add(new Line("SyllyAddons · Attack Routing · " + advice.target(), 0xFFF1F4FA));
        lines.add(new Line(advice.decision().label(), advice.available() ? 0xFF9DDEB2 : 0xFFFFD166));
        if (advice.available()) {
            lines.add(new Line(estimate("Fastest", advice.fastest()), 0xFFE2E8F3));
            lines.add(new Line(estimate("Cheapest", advice.cheapest()), 0xFFE2E8F3));
            String cost = advice.additionalCostEmeralds() >= 0
                    ? "+" + advice.additionalCostEmeralds() + " emeralds"
                    : advice.additionalCostEmeralds() + " emeralds";
            lines.add(new Line("Fastest saves " + duration(advice.timeSavedSeconds()) + " · " + cost, 0xFFB9C8DF));
            lines.add(new Line("Displayed mode = observed · other mode = 70% tax estimate", 0xFF7F8BA3));
        } else if (!advice.diagnostics().isEmpty()) {
            lines.add(new Line(advice.diagnostics().getLast(), 0xFFFFD166));
        }
        if (showValidation && view.queueValidation() != null) {
            QueueTimerValidation validation = view.queueValidation();
            lines.add(new Line(
                    validation.matches()
                            ? "Queued timer matched the displayed estimate."
                            : "Queued timer differed; future advice remains guarded.",
                    validation.matches() ? 0xFF9DDEB2 : 0xFFFF8D8D));
        }
        lines.add(new Line("Read-only advice · no clicks, commands, or packets", 0xFF7F8BA3));
        return lines;
    }

    private static String estimate(String label, AttackRouteEstimate estimate) {
        String timer = duration(estimate.comparisonTimerSeconds());
        String cost = Long.toString(estimate.comparisonCostEmeralds());
        String observed = estimate.observedCostEmeralds().isPresent() ? "observed" : "estimated";
        return label + ": " + timer + " · " + cost + " emeralds · " + observed
                + " · " + estimate.route().steps().size() + " hops";
    }

    private static String duration(int seconds) {
        return seconds / 60 + "m " + seconds % 60 + "s";
    }

    private static boolean isAttackScreen(Screen screen) {
        return screen != null && screen.getTitle().getString().startsWith("Attacking: ");
    }

    private static String trim(Font font, String value, int width) {
        if (font.width(value) <= width) return value;
        return font.plainSubstrByWidth(value, Math.max(1, width - font.width("..."))) + "...";
    }

    private record Line(String text, int color) {}
}
