package net.syllyaddons.client.gui;

import com.wynntils.screens.maps.GuildMapScreen;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.util.FormattedCharSequence;
import net.syllyaddons.domain.ResourceType;
import net.syllyaddons.economy.ResourceProvenance;
import net.syllyaddons.economy.SpendingAllocation;
import net.syllyaddons.economy.TaxLedgerStep;
import net.syllyaddons.snapshot.ImportedSnapshotContext;
import net.syllyaddons.snapshot.ResourceDrillDown;
import net.syllyaddons.snapshot.ResourceDrillDownService;
import net.syllyaddons.snapshot.ResourceTotalDelta;
import net.syllyaddons.snapshot.SnapshotComparison;
import net.syllyaddons.snapshot.SnapshotFileInfo;
import net.syllyaddons.snapshot.SnapshotFormatException;
import net.syllyaddons.snapshot.SnapshotManagerService;

public final class SnapshotManagerScreen extends Screen {
    private static final int BACKGROUND = 0xF00E1420;
    private static final int HEADER = 0xFF172131;
    private static final int PANEL = 0xFF171F2D;
    private static final int PANEL_BORDER = 0xFF344158;
    private static final int SELECTED = 0xFF2E4770;
    private static final int TEXT = 0xFFF1F4FA;
    private static final int MUTED = 0xFFA5B1C7;
    private static final int GOOD = 0xFF9DDEB2;
    private static final int WARNING = 0xFFFFD166;
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final Screen parent;
    private final SnapshotManagerService snapshots;
    private final ResourceDrillDownService drillDownService = new ResourceDrillDownService();
    private List<SnapshotFileInfo> files = List.of();
    private ImportedSnapshotContext imported;
    private SnapshotComparison comparison;
    private ResourceType selectedResource = ResourceType.EMERALDS;
    private ResourceDrillDown drillDown;
    private int selectedProvenance;
    private int fileScroll;
    private int detailScroll;
    private int maxDetailScroll;
    private String status = "Select a .tnsreco file to inspect it read-only.";

    public SnapshotManagerScreen(Screen parent, SnapshotManagerService snapshots) {
        super(Component.literal("Sylly Addons Snapshots"));
        this.parent = parent;
        this.snapshots = java.util.Objects.requireNonNull(snapshots, "snapshots");
    }

    @Override
    protected void init() {
        if (files.isEmpty()) refreshFiles(false);
        addButton("Back", 10, 14, 48, "Return to settings.", this::onClose);
        addButton("Export current", 64, 14, 98, "Write a checksummed snapshot atomically.", this::exportCurrent);
        addButton("Open folder", 168, 14, 82, "Open the portable snapshot folder.", this::openFolder);
        addButton("Refresh", 256, 14, 60, "Rescan the snapshot folder.", () -> refreshFiles(true));

        Layout layout = layout();
        int rowsVisible = visibleFileRows(layout);
        int maxScroll = Math.max(0, files.size() - rowsVisible);
        fileScroll = Math.clamp(fileScroll, 0, maxScroll);
        int end = Math.min(files.size(), fileScroll + rowsVisible);
        for (int index = fileScroll; index < end; index++) {
            SnapshotFileInfo file = files.get(index);
            int rowY = layout.top() + 27 + (index - fileScroll) * 25;
            String marker = imported != null && imported.sourcePath().equals(file.path()) ? "> " : "";
            Button button = addButton(
                    marker + trimFileName(file.path().getFileName().toString()),
                    layout.leftX() + 7,
                    rowY,
                    layout.leftWidth() - 14,
                    file.path() + "\n" + humanBytes(file.sizeBytes()),
                    () -> importFile(file));
            if (marker.isEmpty()) button.active = true;
        }

        if (imported != null) {
            int controlsY = layout.top() + 7;
            int right = layout.rightX();
            boolean compactControls = layout.rightWidth() < 350;
            int compareWidth = compactControls ? 80 : 96;
            int resourceWidth = compactControls ? 90 : Math.max(80, Math.min(110, layout.rightWidth() - 300));
            addButton("Compare", right + 8, controlsY, compareWidth,
                    "Compare normalized current observations with this read-only snapshot.", this::compareCurrent);
            CycleButton<ResourceType> resources = CycleButton.<ResourceType>builder(
                            value -> Component.literal(resourceName(value)), selectedResource)
                    .withValues(ResourceType.values())
                    .displayOnlyValue()
                    .create(
                            right + 12 + compareWidth,
                            controlsY,
                            resourceWidth,
                            20,
                            Component.literal("Resource"),
                            (button, value) -> selectResource(value));
            resources.setTooltip(Tooltip.create(Component.literal("Choose the resource provenance to inspect.")));
            addRenderableWidget(resources);

            ResourceProvenance selected = selectedProvenance();
            int actionRight = right + layout.rightWidth() - 8;
            if (selected != null) {
                int actionY = compactControls ? controlsY + 23 : controlsY;
                int actionStart = compactControls ? right + 8 : actionRight - 142;
                addButton("<", actionStart, actionY, 24, "Previous production source.", () -> moveSource(-1));
                addButton(">", actionStart + 28, actionY, 24, "Next production source.", () -> moveSource(1));
                Button highlight = addButton(
                        "Show on map",
                        actionStart + 56,
                        actionY,
                        86,
                        completeRouteTooltip(selected),
                        this::showOnMap);
                highlight.active = !selected.route().isEmpty();
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, BACKGROUND);
        graphics.fill(0, 0, width, 45, HEADER);
        if (width >= 500) graphics.drawString(font, "Snapshots and provenance", 326, 20, TEXT, false);

        Layout layout = layout();
        drawPanel(graphics, layout.leftX(), layout.top(), layout.leftWidth(), layout.height());
        drawPanel(graphics, layout.rightX(), layout.top(), layout.rightWidth(), layout.height());
        graphics.drawString(font, "Local .tnsreco files", layout.leftX() + 8, layout.top() + 10, TEXT, false);
        if (files.isEmpty()) {
            graphics.drawString(font, "No snapshots yet.", layout.leftX() + 8, layout.top() + 38, MUTED, false);
        }
        renderFileScrollbar(graphics, layout);

        int contentTop = layout.top() + (imported != null && layout.rightWidth() < 350 ? 57 : 34);
        int contentBottom = layout.top() + layout.height() - 22;
        graphics.enableScissor(layout.rightX() + 2, contentTop, layout.rightX() + layout.rightWidth() - 2, contentBottom);
        int endY;
        try {
            endY = renderDetails(graphics, layout.rightX() + 10, contentTop + 4 - detailScroll, layout.rightWidth() - 20);
        } finally {
            graphics.disableScissor();
        }
        maxDetailScroll = Math.max(0, endY + detailScroll - contentBottom + 8);
        detailScroll = Math.clamp(detailScroll, 0, maxDetailScroll);
        renderDetailScrollbar(graphics, layout, contentTop, contentBottom);

        graphics.drawString(font, trim(status, width - 20), 10, height - 14, statusColor(), false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount == 0) return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        Layout layout = layout();
        if (mouseX >= layout.leftX() && mouseX < layout.leftX() + layout.leftWidth()) {
            int max = Math.max(0, files.size() - visibleFileRows(layout));
            int next = Math.clamp(fileScroll + (verticalAmount > 0 ? -1 : 1), 0, max);
            if (next != fileScroll) {
                fileScroll = next;
                rebuildWidgets();
            }
            return true;
        }
        if (mouseX >= layout.rightX() && maxDetailScroll > 0) {
            detailScroll = Math.clamp(detailScroll + (verticalAmount > 0 ? -24 : 24), 0, maxDetailScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int renderDetails(GuiGraphics graphics, int x, int y, int availableWidth) {
        if (imported == null) {
            graphics.drawString(font, "Read-only inspector", x, y, TEXT, false);
            y += 16;
            y = drawWrapped(
                    graphics,
                    "Select a snapshot on the left. Importing validates its version, checksum, topology, enums, and numeric bounds without changing live state.",
                    x,
                    y,
                    availableWidth,
                    MUTED);
            return y;
        }

        var content = imported.archive().content();
        graphics.drawString(font, imported.sourcePath().getFileName().toString(), x, y, TEXT, false);
        y += 13;
        graphics.drawString(font, "READ-ONLY · revision " + content.payload().observed().sourceRevision(), x, y, GOOD, false);
        y += 13;
        graphics.drawString(font, "Created " + TIME.format(Instant.ofEpochMilli(content.createdAtEpochMillis())), x, y, MUTED, false);
        y += 13;
        graphics.drawString(font, "SHA-256 " + imported.archive().checksumSha256(), x, y, MUTED, false);
        y += 17;

        if (comparison != null) {
            graphics.drawString(font, "Current comparison", x, y, TEXT, false);
            y += 13;
            graphics.drawString(
                    font,
                    comparison.territoryDeltas().size() + " changed territories · HQ "
                            + comparison.baselineHq() + " -> " + comparison.currentHq(),
                    x,
                    y,
                    MUTED,
                    false);
            y += 13;
            for (ResourceType resource : ResourceType.values()) {
                ResourceTotalDelta delta = comparison.resourceDeltas().get(resource);
                graphics.drawString(
                        font,
                        resourceName(resource) + " generation " + signed(delta.generationChange())
                                + "/h · stored " + signed(delta.storedChange()),
                        x + 8,
                        y,
                        delta.generationChange() == 0 && delta.storedChange() == 0 ? MUTED : TEXT,
                        false);
                y += 12;
            }
            y += 5;
        }

        if (drillDown == null || drillDown.totals() == null) {
            graphics.drawString(font, resourceName(selectedResource) + " provenance unavailable", x, y, WARNING, false);
            y += 14;
            for (var diagnostic : drillDown == null ? List.<net.syllyaddons.routing.RouteDiagnostic>of() : drillDown.diagnostics()) {
                y = drawWrapped(graphics, diagnostic.code() + ": " + diagnostic.message(), x + 8, y, availableWidth - 8, MUTED);
            }
            return y;
        }

        var totals = drillDown.totals();
        graphics.drawString(
                font,
                resourceName(selectedResource) + " totals · " + (drillDown.exact() ? "exact" : "estimated"),
                x,
                y,
                drillDown.exact() ? GOOD : WARNING,
                false);
        y += 13;
        graphics.drawString(
                font,
                "Produced " + number(totals.grossProduction()) + "/h · delivered "
                        + number(totals.deliveredProduction()) + "/h · tax loss " + number(totals.taxLoss()),
                x + 8,
                y,
                TEXT,
                false);
        y += 12;
        graphics.drawString(
                font,
                "Expenses " + number(totals.expenses()) + "/h · deficit " + number(totals.deficit())
                        + " · HQ stored " + number(totals.endingStorage()),
                x + 8,
                y,
                TEXT,
                false);
        y += 17;

        ResourceProvenance source = selectedProvenance();
        if (source == null) {
            graphics.drawString(font, "No production sources for this resource.", x, y, MUTED, false);
            return y + 12;
        }
        graphics.drawString(
                font,
                "Source " + (selectedProvenance + 1) + "/" + drillDown.production().size() + ": "
                        + source.sourceTerritory(),
                x,
                y,
                TEXT,
                false);
        y += 13;
        String destination = source.route().isEmpty() ? "Undelivered" : source.route().getLast();
        graphics.drawString(
                font,
                "Gross " + number(source.sourceAmount()) + " · delivered " + number(source.deliveredToHq())
                        + " · destination " + destination + " · time " + source.deliverySeconds() + "s",
                x + 8,
                y,
                TEXT,
                false);
        y += 14;
        y = drawWrapped(graphics, "Complete route: " + routeText(source), x + 8, y, availableWidth - 8, MUTED);
        graphics.drawString(font, "Taxes", x + 8, y, TEXT, false);
        y += 12;
        if (source.taxSteps().isEmpty()) {
            graphics.drawString(font, "None", x + 16, y, MUTED, false);
            y += 12;
        } else {
            for (TaxLedgerStep tax : source.taxSteps()) {
                y = drawWrapped(
                        graphics,
                        tax.from() + " -> " + tax.to() + ": " + percent(tax.taxRate()) + " = "
                                + number(tax.taxLoss()) + " lost, " + number(tax.amountAfter()) + " remains",
                        x + 16,
                        y,
                        availableWidth - 16,
                        MUTED);
            }
        }
        graphics.drawString(font, "Consumers", x + 8, y, TEXT, false);
        y += 12;
        if (source.spending().isEmpty()) {
            graphics.drawString(font, "None observed", x + 16, y, MUTED, false);
            y += 12;
        } else {
            for (SpendingAllocation spending : source.spending()) {
                graphics.drawString(
                        font,
                        spending.consumerTerritory() + ": " + number(spending.amount()),
                        x + 16,
                        y,
                        MUTED,
                        false);
                y += 12;
            }
        }
        graphics.drawString(
                font,
                "Stored " + number(source.storedAtHq()) + " · overflow " + number(source.overflowLoss())
                        + " · undelivered " + number(source.undelivered()),
                x + 8,
                y,
                TEXT,
                false);
        y += 18;
        graphics.drawString(font, "Diagnostics", x, y, TEXT, false);
        y += 12;
        for (var diagnostic : drillDown.diagnostics()) {
            y = drawWrapped(graphics, diagnostic.code() + ": " + diagnostic.message(), x + 8, y, availableWidth - 8, MUTED);
        }
        return y;
    }

    private void importFile(SnapshotFileInfo file) {
        try {
            imported = snapshots.importReadOnly(file.path(), System.currentTimeMillis());
            comparison = null;
            if (imported.payload().economy() != null) {
                selectedResource = imported.payload().economy().summaries().entrySet().stream()
                        .max(java.util.Comparator.comparingDouble(entry -> entry.getValue().grossProduction()))
                        .map(java.util.Map.Entry::getKey)
                        .orElse(selectedResource);
            }
            selectedProvenance = 0;
            detailScroll = 0;
            drillDown = drillDownService.build(imported.payload(), selectedResource);
            status = "Imported read-only; checksum and schema are valid.";
        } catch (IOException | SnapshotFormatException | RuntimeException exception) {
            imported = null;
            comparison = null;
            drillDown = null;
            status = "Could not import: " + safeMessage(exception);
        }
        rebuildWidgets();
    }

    private void exportCurrent() {
        try {
            var path = snapshots.exportCurrent(System.currentTimeMillis());
            refreshFiles(false);
            status = "Exported " + path.getFileName() + ".";
            files.stream().filter(file -> file.path().equals(path)).findFirst().ifPresent(this::importFile);
        } catch (IOException | SnapshotFormatException | RuntimeException exception) {
            status = "Could not export: " + safeMessage(exception);
        }
        rebuildWidgets();
    }

    private void refreshFiles(boolean showStatus) {
        try {
            files = snapshots.listSnapshots();
            if (showStatus) status = "Found " + files.size() + " snapshot(s).";
        } catch (IOException exception) {
            files = List.of();
            status = "Could not list snapshots: " + safeMessage(exception);
        }
        fileScroll = Math.clamp(fileScroll, 0, Math.max(0, files.size() - 1));
    }

    private void openFolder() {
        try {
            Files.createDirectories(snapshots.snapshotDirectory());
            Util.getPlatform().openPath(snapshots.snapshotDirectory());
            status = "Opened the snapshot folder.";
        } catch (IOException | RuntimeException exception) {
            status = "Could not open folder: " + safeMessage(exception);
        }
    }

    private void compareCurrent() {
        if (imported == null) return;
        comparison = snapshots.compareWithCurrent(imported, System.currentTimeMillis());
        detailScroll = 0;
        status = "Compared imported revision " + comparison.baselineRevision() + " with live revision "
                + comparison.currentRevision() + ".";
    }

    private void selectResource(ResourceType resource) {
        selectedResource = resource;
        selectedProvenance = 0;
        detailScroll = 0;
        drillDown = imported == null ? null : drillDownService.build(imported.payload(), resource);
        rebuildWidgets();
    }

    private void moveSource(int amount) {
        if (drillDown == null || drillDown.production().isEmpty()) return;
        selectedProvenance = Math.floorMod(selectedProvenance + amount, drillDown.production().size());
        detailScroll = 0;
        rebuildWidgets();
    }

    private void showOnMap() {
        ResourceProvenance source = selectedProvenance();
        if (source == null || source.route().isEmpty() || minecraft == null || imported == null) return;
        RouteHighlightController.highlight(
                imported.payload().observed(),
                source.route(),
                resourceName(source.resource()) + " from " + source.sourceTerritory());
        minecraft.setScreen(GuildMapScreen.create(this));
    }

    private ResourceProvenance selectedProvenance() {
        if (drillDown == null || drillDown.production().isEmpty()) return null;
        selectedProvenance = Math.clamp(selectedProvenance, 0, drillDown.production().size() - 1);
        return drillDown.production().get(selectedProvenance);
    }

    private void renderFileScrollbar(GuiGraphics graphics, Layout layout) {
        int visible = visibleFileRows(layout);
        if (files.size() <= visible) return;
        int trackX = layout.leftX() + layout.leftWidth() - 4;
        int trackY = layout.top() + 27;
        int trackHeight = visible * 25 - 2;
        int thumbHeight = Math.max(12, trackHeight * visible / files.size());
        int maxScroll = files.size() - visible;
        int thumbY = trackY + (trackHeight - thumbHeight) * fileScroll / maxScroll;
        graphics.fill(trackX, trackY, trackX + 2, trackY + trackHeight, 0xFF253146);
        graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, 0xFF70A8FF);
    }

    private void renderDetailScrollbar(GuiGraphics graphics, Layout layout, int top, int bottom) {
        if (maxDetailScroll <= 0) return;
        int trackX = layout.rightX() + layout.rightWidth() - 4;
        int trackHeight = bottom - top;
        int thumbHeight = Math.max(12, trackHeight * trackHeight / (trackHeight + maxDetailScroll));
        int thumbY = top + (trackHeight - thumbHeight) * detailScroll / maxDetailScroll;
        graphics.fill(trackX, top, trackX + 2, bottom, 0xFF253146);
        graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, 0xFF70A8FF);
    }

    private int drawWrapped(GuiGraphics graphics, String text, int x, int y, int availableWidth, int color) {
        List<FormattedCharSequence> lines = font.split(Component.literal(text), Math.max(30, availableWidth));
        for (FormattedCharSequence line : lines) {
            graphics.drawString(font, line, x, y, color, false);
            y += 11;
        }
        return y;
    }

    private Layout layout() {
        int leftX = 8;
        int top = 49;
        int bottom = height - 19;
        int leftWidth = Math.min(224, Math.max(150, width / 3));
        int rightX = leftX + leftWidth + 7;
        return new Layout(leftX, leftWidth, rightX, Math.max(120, width - rightX - 8), top, bottom - top);
    }

    private int visibleFileRows(Layout layout) {
        return Math.max(1, (layout.height() - 34) / 25);
    }

    private Button addButton(String label, int x, int y, int width, String tooltip, Runnable action) {
        Button button = Button.builder(Component.literal(label), ignored -> action.run())
                .bounds(x, y, Math.max(20, width), 20)
                .tooltip(Tooltip.create(Component.literal(tooltip)))
                .build();
        return addRenderableWidget(button);
    }

    private String trim(String value, int maximumWidth) {
        if (font.width(value) <= maximumWidth) return value;
        return font.plainSubstrByWidth(value, Math.max(1, maximumWidth - font.width("..."))) + "...";
    }

    private static String trimFileName(String name) {
        return name.length() <= 30 ? name : name.substring(0, 27) + "...";
    }

    private static String routeText(ResourceProvenance provenance) {
        return provenance.route().isEmpty() ? "No route" : String.join(" -> ", provenance.route());
    }

    private static String completeRouteTooltip(ResourceProvenance provenance) {
        return routeText(provenance) + "\n" + provenance.deliverySeconds() + " seconds · tax loss "
                + number(provenance.taxLoss());
    }

    private static String resourceName(ResourceType resource) {
        String lower = resource.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static String number(double value) {
        return Math.abs(value - Math.rint(value)) < 1.0e-9
                ? Long.toString(Math.round(value))
                : String.format(Locale.ROOT, "%.2f", value);
    }

    private static String signed(double value) {
        return (value > 0 ? "+" : "") + number(value);
    }

    private static String percent(double value) {
        return String.format(Locale.ROOT, "%.1f%%", value * 100.0);
    }

    private static String humanBytes(long bytes) {
        if (bytes < 1_024) return bytes + " B";
        if (bytes < 1_048_576) return String.format(Locale.ROOT, "%.1f KiB", bytes / 1_024.0);
        return String.format(Locale.ROOT, "%.1f MiB", bytes / 1_048_576.0);
    }

    private int statusColor() {
        String lower = status.toLowerCase(Locale.ROOT);
        return lower.contains("could not") || lower.contains("invalid") ? WARNING : GOOD;
    }

    private static String safeMessage(Exception exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    private static void drawPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, PANEL_BORDER);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, PANEL);
    }

    private record Layout(int leftX, int leftWidth, int rightX, int rightWidth, int top, int height) {}
}
