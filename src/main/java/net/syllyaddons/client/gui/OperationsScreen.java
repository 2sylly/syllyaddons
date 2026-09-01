package net.syllyaddons.client.gui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.syllyaddons.diagnostics.DebugBundleService;
import net.syllyaddons.diagnostics.OperationsHealthService;
import net.syllyaddons.diagnostics.SubsystemHealth;
import net.syllyaddons.diagnostics.SubsystemHealthReport;
import net.syllyaddons.diagnostics.SubsystemHealthStatus;
import net.syllyaddons.observation.ObservedStateRepository;

/** Track 11 operations hub: compact subsystem status plus local redacted export. */
public final class OperationsScreen extends Screen {
    private static final int LINE_HEIGHT = 12;
    private static final int CONTENT_TOP = 58;
    private static final int CONTENT_BOTTOM = 28;

    private final Screen parent;
    private final ObservedStateRepository repository;
    private final OperationsHealthService healthService;
    private final DebugBundleService debugBundles;
    private List<RenderedLine> lines = List.of();
    private long renderedAtSecond = -1;
    private int scrollLine;
    private volatile String exportStatus = "Bundles stay local and are redacted; review before sharing.";
    private volatile boolean exporting;

    public OperationsScreen(
            Screen parent,
            ObservedStateRepository repository,
            OperationsHealthService healthService,
            DebugBundleService debugBundles) {
        super(Component.literal("Sylly Addons operations"));
        this.parent = parent;
        this.repository = java.util.Objects.requireNonNull(repository, "repository");
        this.healthService = java.util.Objects.requireNonNull(healthService, "healthService");
        this.debugBundles = java.util.Objects.requireNonNull(debugBundles, "debugBundles");
    }

    @Override
    protected void init() {
        int buttonWidth = Math.min(126, Math.max(84, (width - 32) / 3));
        int totalWidth = buttonWidth * 3 + 8;
        int startX = Math.max(8, (width - totalWidth) / 2);
        addButton(
                "Export debug bundle",
                startX,
                28,
                buttonWidth,
                "Create a local ZIP without raw logs, configs, character IDs, guild identities, or profile names.",
                this::exportBundle);
        addButton(
                "Raw data",
                startX + buttonWidth + 4,
                28,
                buttonWidth,
                "Open the detailed observed-value and provenance inspector.",
                () -> {
                    if (minecraft != null) minecraft.setScreen(new ObservedStateDebugScreen(this, repository));
                });
        addButton("Done", startX + buttonWidth * 2 + 8, 28, buttonWidth, "Return to settings.", this::onClose);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xF00E1420);
        graphics.drawCenteredString(font, title, width / 2, 9, 0xFFF1F4FA);
        refresh(System.currentTimeMillis());
        int visible = visibleLineCount();
        scrollLine = Math.min(scrollLine, maxScroll(visible));
        graphics.enableScissor(8, CONTENT_TOP, Math.max(9, width - 8), Math.max(CONTENT_TOP + 1, height - CONTENT_BOTTOM));
        int end = Math.min(lines.size(), scrollLine + visible);
        for (int index = scrollLine; index < end; index++) {
            RenderedLine line = lines.get(index);
            graphics.drawString(font, line.text(), 12, CONTENT_TOP + (index - scrollLine) * LINE_HEIGHT, line.color(), false);
        }
        graphics.disableScissor();
        graphics.drawCenteredString(
                font,
                font.plainSubstrByWidth(exportStatus, Math.max(40, width - 24)),
                width / 2,
                Math.max(CONTENT_TOP, height - 16),
                exportStatus.startsWith("Could not") ? 0xFFFFAA55 : 0xFFA5B1C7);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int direction = (int) Math.signum(verticalAmount);
        if (direction == 0) return false;
        scrollLine = Math.max(0, Math.min(maxScroll(visibleLineCount()), scrollLine - direction * 3));
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

    private void refresh(long nowEpochMillis) {
        long second = nowEpochMillis / 1_000;
        if (second == renderedAtSecond) return;
        renderedAtSecond = second;
        SubsystemHealthReport report = healthService.assess(nowEpochMillis);
        ArrayList<RenderedLine> rebuilt = new ArrayList<>();
        rebuilt.add(new RenderedLine(
                "State revision " + report.stateRevision() + " | " + report.failedCount() + " failed | "
                        + report.degradedCount() + " degraded",
                report.failedCount() > 0 ? 0xFFFF7777 : report.degradedCount() > 0 ? 0xFFFFD166 : 0xFF9DDEB2));
        rebuilt.add(new RenderedLine("", 0xFFFFFFFF));
        for (SubsystemHealth subsystem : report.subsystems()) {
            rebuilt.add(new RenderedLine(
                    subsystem.subsystem().label() + " — " + subsystem.status() + " / " + subsystem.category(),
                    color(subsystem.status())));
            rebuilt.add(new RenderedLine("  " + subsystem.summary(), 0xFFD5DCE8));
            subsystem.diagnostics().stream().limit(3).forEach(value ->
                    rebuilt.add(new RenderedLine("    • " + value, 0xFFA5B1C7)));
            rebuilt.add(new RenderedLine("", 0xFFFFFFFF));
        }
        lines = List.copyOf(rebuilt);
    }

    private void exportBundle() {
        if (exporting) return;
        exporting = true;
        exportStatus = "Creating redacted local bundle...";
        CompletableFuture.supplyAsync(() -> {
            try {
                return debugBundles.export(System.currentTimeMillis());
            } catch (IOException | RuntimeException exception) {
                throw new java.util.concurrent.CompletionException(exception);
            }
        }).whenComplete((result, failure) -> {
            if (minecraft == null) return;
            minecraft.execute(() -> {
                exporting = false;
                if (failure == null) {
                    exportStatus = "Saved " + result.path().getFileName() + " (" + result.sizeBytes() + " bytes).";
                } else {
                    Throwable root = failure;
                    while (root.getCause() != null) root = root.getCause();
                    exportStatus = "Could not create debug bundle: "
                            + (root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage());
                }
            });
        });
    }

    private Button addButton(String label, int x, int y, int width, String tooltip, Runnable action) {
        return addRenderableWidget(Button.builder(Component.literal(label), ignored -> action.run())
                .bounds(x, y, width, 20)
                .tooltip(Tooltip.create(Component.literal(tooltip)))
                .build());
    }

    private int visibleLineCount() {
        return Math.max(1, (height - CONTENT_TOP - CONTENT_BOTTOM) / LINE_HEIGHT);
    }

    private int maxScroll(int visible) {
        return Math.max(0, lines.size() - visible);
    }

    private static int color(SubsystemHealthStatus status) {
        return switch (status) {
            case HEALTHY -> 0xFF9DDEB2;
            case DEGRADED -> 0xFFFFD166;
            case WAITING -> 0xFF9CC9FF;
            case DISABLED -> 0xFF8D98AB;
            case FAILED -> 0xFFFF7777;
        };
    }

    private record RenderedLine(String text, int color) {}
}
