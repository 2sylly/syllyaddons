package net.syllyaddons.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.syllyaddons.config.OptimizerConfig;
import net.syllyaddons.config.SyllyConfigService;
import net.syllyaddons.optimizer.OptimizationCandidate;
import net.syllyaddons.optimizer.OptimizationMetrics;
import net.syllyaddons.optimizer.OptimizationObjective;
import net.syllyaddons.optimizer.OptimizationResult;
import net.syllyaddons.optimizer.OptimizerRunStatus;
import net.syllyaddons.optimizer.OptimizerService;
import net.syllyaddons.optimizer.OptimizerView;
import net.syllyaddons.optimizer.UpgradeChange;

/** Read-only Track 10 results and manual downgrade checklist. Deliberately contains no Apply control. */
public final class DefenceOptimizerScreen extends Screen {
    private static final int BACKGROUND = 0xF00E1420;
    private static final int PANEL = 0xFF171F2D;
    private static final int BORDER = 0xFF344158;
    private static final int TEXT = 0xFFF1F4FA;
    private static final int MUTED = 0xFFA5B1C7;
    private static final int GOOD = 0xFF9DDEB2;
    private static final int WARNING = 0xFFFFD166;

    private final Screen parent;
    private final OptimizerService optimizer;
    private final SyllyConfigService settings;
    private int scroll;
    private long lastAutoRunRevision = Long.MIN_VALUE;

    public DefenceOptimizerScreen(Screen parent, OptimizerService optimizer, SyllyConfigService settings) {
        super(Component.literal("Defence Sustainability Optimizer"));
        this.parent = java.util.Objects.requireNonNull(parent, "parent");
        this.optimizer = java.util.Objects.requireNonNull(optimizer, "optimizer");
        this.settings = java.util.Objects.requireNonNull(settings, "settings");
    }

    @Override
    protected void init() {
        OptimizerConfig config = settings.snapshot().optimizer();
        int backX = width - 76;
        int runX = Math.max(52, backX - 122);
        int objectiveWidth = Math.max(40, runX - 24);
        CycleButton<OptimizationObjective> objective = CycleButton.<OptimizationObjective>builder(
                        value -> Component.literal(value.label()), config.objective())
                .withValues(OptimizationObjective.values())
                .displayOnlyValue()
                .create(12, 27, objectiveWidth, 20,
                        Component.literal("Objective"), (button, value) -> {
                            if (settings.update(current -> current.withOptimizer(
                                    current.optimizer().withObjective(value)))) {
                                scroll = 0;
                                request();
                            }
                        });
        objective.setTooltip(Tooltip.create(Component.literal(
                "Changes the deterministic candidate ordering; all hard reserve/deficit constraints still apply.")));
        addRenderableWidget(objective);
        Button run = Button.builder(Component.literal("Run optimizer"), ignored -> request())
                .bounds(runX, 27, 116, 20)
                .tooltip(Tooltip.create(Component.literal("Rebuild from the latest observed territory state.")))
                .build();
        run.active = settings.snapshot().optimizerEnabled();
        addRenderableWidget(run);
        addRenderableWidget(Button.builder(Component.literal("Back"), ignored -> onClose())
                .bounds(backX, 27, 64, 20)
                .build());

        OptimizerView view = optimizer.view();
        if (settings.snapshot().optimizerEnabled()
                && (view.status() == OptimizerRunStatus.IDLE || view.status() == OptimizerRunStatus.STALE)
                && lastAutoRunRevision != view.sourceRevision()) {
            lastAutoRunRevision = view.sourceRevision();
            request();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, BACKGROUND);
        graphics.drawString(font, title, 12, 9, TEXT, false);
        drawPanel(graphics, 8, 54, Math.max(120, width - 16), Math.max(40, height - 64));
        OptimizerView view = optimizer.view();
        List<DisplayLine> lines = displayLines(view);
        int top = 62;
        int bottom = height - 16;
        int visible = Math.max(1, (bottom - top) / 11);
        scroll = Math.clamp(scroll, 0, Math.max(0, lines.size() - visible));
        int end = Math.min(lines.size(), scroll + visible);
        for (int index = scroll; index < end; index++) {
            DisplayLine line = lines.get(index);
            graphics.drawString(font, trim(line.text(), width - 34), 17, top + (index - scroll) * 11,
                    line.color(), false);
        }
        if (lines.size() > visible) {
            graphics.drawString(font,
                    "Lines " + (scroll + 1) + "-" + end + " / " + lines.size(),
                    width - 112, height - 13, MUTED, false);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        scroll = Math.max(0, scroll - (int) Math.signum(vertical) * 3);
        return true;
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void request() {
        if (!settings.snapshot().optimizerEnabled()) return;
        optimizer.request(settings.snapshot().optimizer());
    }

    private List<DisplayLine> displayLines(OptimizerView view) {
        List<DisplayLine> lines = new ArrayList<>();
        lines.add(new DisplayLine("Status: " + pretty(view.status()) + " · source revision " + view.sourceRevision(),
                statusColor(view.status())));
        OptimizerConfig config = settings.snapshot().optimizer();
        lines.add(new DisplayLine(
                "Objective " + config.objective().label() + " · reserve floor " + config.reserveFloorPercent()
                        + "% of observed HQ reserves · no deficits " + yesNo(config.requireNoDeficits()), MUTED));
        lines.add(new DisplayLine(
                "Bounds: " + config.nodeLimit() + " nodes / " + config.timeLimitMillis()
                        + "ms · tower and strategic bonuses fixed", MUTED));
        lines.add(new DisplayLine("Read-only: use the checklist manually; there is intentionally no Apply action.", WARNING));
        if (view.status() == OptimizerRunStatus.RUNNING) {
            lines.add(new DisplayLine("Calculating off the render thread...", TEXT));
            return lines;
        }
        for (String diagnostic : view.diagnostics()) lines.add(new DisplayLine("Input: " + diagnostic, MUTED));
        if (view.result().isEmpty()) return lines;

        OptimizationResult result = view.result().orElseThrow();
        lines.add(new DisplayLine("", TEXT));
        lines.add(new DisplayLine(
                "Search: " + pretty(result.termination()) + " · " + result.evaluatedNodes() + " candidates · "
                        + result.elapsedMillis() + "ms · optimality " + yesNo(result.optimalityProven())
                        + " · revalidated " + yesNo(result.independentlyVerified()),
                result.independentlyVerified() ? GOOD : WARNING));
        lines.add(new DisplayLine("Current baseline", TEXT));
        appendMetrics(lines, result.baseline().metrics());
        if (!result.baseline().violations().isEmpty()) {
            result.baseline().violations().forEach(value -> lines.add(new DisplayLine("  baseline constraint: " + value, WARNING)));
        }

        if (result.recommendation().isPresent()) {
            OptimizationCandidate recommendation = result.recommendation().orElseThrow();
            lines.add(new DisplayLine("", TEXT));
            lines.add(new DisplayLine("Verified recommendation", GOOD));
            appendMetrics(lines, recommendation.metrics());
            lines.add(new DisplayLine(
                    "  expense change " + signed(recommendation.metrics().totalExpensePerHour()
                            - result.baseline().metrics().totalExpensePerHour()) + "/h · storage change "
                            + signed(recommendation.metrics().totalEndingStorage()
                                    - result.baseline().metrics().totalEndingStorage()), MUTED));
            lines.add(new DisplayLine("Manual checklist (" + recommendation.changes().size() + " changes)", TEXT));
            if (recommendation.changes().isEmpty()) {
                lines.add(new DisplayLine("  Keep the current configuration; it is the best feasible baseline.", MUTED));
            }
            for (UpgradeChange change : recommendation.changes()) {
                lines.add(new DisplayLine(
                        "  " + change.coordinate().territory() + ": " + change.displayName() + " "
                                + change.beforeLevel() + " → " + change.afterLevel() + " · saves "
                                + change.hourlySaving() + " " + change.savedResource() + "/h",
                        TEXT));
            }
        } else {
            lines.add(new DisplayLine("", TEXT));
            lines.add(new DisplayLine("No feasible recommendation", WARNING));
            result.diagnostics().forEach(value -> lines.add(new DisplayLine("  " + value, WARNING)));
            result.bestEffort().ifPresent(candidate -> {
                lines.add(new DisplayLine("Closest checked candidate (not a recommendation)", MUTED));
                appendMetrics(lines, candidate.metrics());
            });
        }
        return lines;
    }

    private static void appendMetrics(List<DisplayLine> lines, OptimizationMetrics metrics) {
        lines.add(new DisplayLine(
                "  expense " + number(metrics.totalExpensePerHour()) + "/h · deficit "
                        + number(metrics.totalDeficitPerHour()) + "/h · min buffer "
                        + number(metrics.minimumEndingBuffer()) + " · total stored "
                        + number(metrics.totalEndingStorage()), MUTED));
    }

    private String trim(String value, int maximumWidth) {
        if (font.width(value) <= maximumWidth) return value;
        return font.plainSubstrByWidth(value, Math.max(1, maximumWidth - font.width("..."))) + "...";
    }

    private static void drawPanel(GuiGraphics graphics, int x, int y, int panelWidth, int panelHeight) {
        graphics.fill(x, y, x + panelWidth, y + panelHeight, BORDER);
        graphics.fill(x + 1, y + 1, x + panelWidth - 1, y + panelHeight - 1, PANEL);
    }

    private static int statusColor(OptimizerRunStatus status) {
        return switch (status) {
            case COMPLETE -> GOOD;
            case UNAVAILABLE, FAILED, STALE -> WARNING;
            case IDLE, RUNNING -> TEXT;
        };
    }

    private static String number(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String signed(double value) {
        return (value >= 0 ? "+" : "") + number(value);
    }

    private static String yesNo(boolean value) {
        return value ? "yes" : "no";
    }

    private static String pretty(Enum<?> value) {
        String lower = value.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private record DisplayLine(String text, int color) {}
}
