package net.syllyaddons.client.gui;

import com.wynntils.screens.maps.GuildMapScreen;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.syllyaddons.domain.EcoSnapshot;
import net.syllyaddons.domain.ResourceType;
import net.syllyaddons.domain.RoutingMode;
import net.syllyaddons.impact.ImpactCacheStatus;
import net.syllyaddons.impact.ImpactCacheView;
import net.syllyaddons.impact.ImpactCertainty;
import net.syllyaddons.impact.ImpactScore;
import net.syllyaddons.impact.ImpactSeverity;
import net.syllyaddons.impact.ResourceImpactDelta;
import net.syllyaddons.impact.RouteChangeKind;
import net.syllyaddons.impact.RoutingModeImpact;
import net.syllyaddons.impact.TerritoryImpactCache;
import net.syllyaddons.impact.TerritoryImpactReport;
import net.syllyaddons.impact.TerritoryRouteImpact;
import net.syllyaddons.observation.ObservedStateRepository;

/** Track 7 inspector. Rendering reads completed immutable cache entries only. */
public final class TerritoryImpactScreen extends Screen {
    private static final int BACKGROUND = 0xF00E1420;
    private static final int HEADER = 0xFF172131;
    private static final int PANEL = 0xFF171F2D;
    private static final int PANEL_BORDER = 0xFF344158;
    private static final int TEXT = 0xFFF1F4FA;
    private static final int MUTED = 0xFFA5B1C7;
    private static final int GOOD = 0xFF9DDEB2;
    private static final int WARNING = 0xFFFFD166;
    private static final int CRITICAL = 0xFFFF7B86;
    private static final int CATASTROPHIC = 0xFFC58CFF;

    private final Screen parent;
    private final ObservedStateRepository repository;
    private final TerritoryImpactCache cache;
    private ImpactCacheView cacheView = ImpactCacheView.empty();
    private List<TerritoryImpactReport> reports = List.of();
    private String selectedTerritory = "";
    private RoutingMode selectedMode;
    private int territoryScroll;
    private int detailScroll;
    private int maxDetailScroll;
    private int ticks;
    private String lastViewSignature = "";

    public TerritoryImpactScreen(
            Screen parent, ObservedStateRepository repository, TerritoryImpactCache cache) {
        super(Component.literal("Sylly Addons Territory Impact"));
        this.parent = parent;
        this.repository = java.util.Objects.requireNonNull(repository, "repository");
        this.cache = java.util.Objects.requireNonNull(cache, "cache");
    }

    @Override
    protected void init() {
        refreshView();
        addButton("Back", 10, 14, 48, "Return to settings.", this::onClose);
        addButton("Rebuild", 64, 14, 64, "Rebuild from the latest observation.", () -> {
            cache.request(repository.snapshot(), System.currentTimeMillis(), true);
            detailScroll = 0;
            refreshAndRebuild();
        });

        Layout layout = layout();
        int visible = visibleRows(layout);
        territoryScroll = Math.clamp(territoryScroll, 0, Math.max(0, reports.size() - visible));
        int end = Math.min(reports.size(), territoryScroll + visible);
        for (int index = territoryScroll; index < end; index++) {
            TerritoryImpactReport report = reports.get(index);
            int rowY = layout.top() + 29 + (index - territoryScroll) * 27;
            String marker = report.removedTerritory().equals(selectedTerritory) ? "> " : "";
            String label = marker + severityMark(report.maximumSeverity()) + " "
                    + trim(report.removedTerritory(), layout.leftWidth() - 31);
            addButton(
                    label,
                    layout.leftX() + 7,
                    rowY,
                    layout.leftWidth() - 14,
                    "Remove " + report.removedTerritory() + " · " + report.ownerRelation() + " · "
                            + report.maximumSeverity(),
                    () -> select(report.removedTerritory()));
        }

        TerritoryImpactReport selected = selectedReport();
        if (selected != null) {
            List<RoutingMode> modes = selected.modes().keySet().stream().sorted().toList();
            if (selectedMode == null || !selected.modes().containsKey(selectedMode)) selectedMode = modes.getFirst();
            if (modes.size() > 1) {
                CycleButton<RoutingMode> modeButton = CycleButton.<RoutingMode>builder(
                                value -> Component.literal(pretty(value)), selectedMode)
                        .withValues(modes)
                        .displayOnlyValue()
                        .create(
                                layout.rightX() + 8,
                                layout.top() + 7,
                                92,
                                20,
                                Component.literal("Routing mode"),
                                (button, value) -> {
                                    selectedMode = value;
                                    detailScroll = 0;
                                });
                modeButton.setTooltip(Tooltip.create(Component.literal(
                        "The live mode was unknown, so both branches were calculated independently.")));
                addRenderableWidget(modeButton);
            }
            TerritoryRouteImpact highlight = highlightImpact(selected.modes().get(selectedMode));
            Button showRoute = addButton(
                    "Show affected route",
                    layout.rightX() + layout.rightWidth() - 135,
                    layout.top() + 7,
                    127,
                    highlight == null ? "No changed route is available."
                            : String.join(" -> ", highlight.baselineRoute()),
                    this::showAffectedRoute);
            showRoute.active = highlight != null;
        }
    }

    @Override
    public void tick() {
        super.tick();
        ticks++;
        if (ticks % 5 != 0) return;
        ImpactCacheView latest = cache.view();
        String signature = latest.generation() + ":" + latest.status() + ":" + (latest.completedTargets() / 10)
                + ":" + latest.completedReports().size();
        if (!signature.equals(lastViewSignature)) refreshAndRebuild();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, BACKGROUND);
        graphics.fill(0, 0, width, 45, HEADER);
        if (width >= 350) graphics.drawString(font, "Cached removal consequences", 139, 20, TEXT, false);

        Layout layout = layout();
        drawPanel(graphics, layout.leftX(), layout.top(), layout.leftWidth(), layout.height());
        drawPanel(graphics, layout.rightX(), layout.top(), layout.rightWidth(), layout.height());
        graphics.drawString(font, reports.size() + " completed territories", layout.leftX() + 8, layout.top() + 10, TEXT, false);
        renderTerritoryScrollbar(graphics, layout);

        int contentTop = layout.top() + 34;
        int contentBottom = layout.top() + layout.height() - 5;
        graphics.enableScissor(layout.rightX() + 2, contentTop, layout.rightX() + layout.rightWidth() - 2, contentBottom);
        int endY;
        try {
            endY = renderDetails(graphics, layout.rightX() + 10, contentTop + 3 - detailScroll, layout.rightWidth() - 20);
        } finally {
            graphics.disableScissor();
        }
        maxDetailScroll = Math.max(0, endY + detailScroll - contentBottom + 8);
        detailScroll = Math.clamp(detailScroll, 0, maxDetailScroll);
        renderDetailScrollbar(graphics, layout, contentTop, contentBottom);

        graphics.drawString(font, trim(cacheStatus(), width - 20), 10, height - 14, cacheStatusColor(), false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount == 0) return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        Layout layout = layout();
        if (mouseX < layout.rightX()) {
            int maximum = Math.max(0, reports.size() - visibleRows(layout));
            int next = Math.clamp(territoryScroll + (verticalAmount > 0 ? -1 : 1), 0, maximum);
            if (next != territoryScroll) {
                territoryScroll = next;
                rebuildWidgets();
            }
            return true;
        }
        detailScroll = Math.clamp(detailScroll + (verticalAmount > 0 ? -24 : 24), 0, maxDetailScroll);
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

    private int renderDetails(GuiGraphics graphics, int x, int y, int availableWidth) {
        TerritoryImpactReport report = selectedReport();
        if (report == null) {
            graphics.drawString(font, cacheView.status() == ImpactCacheStatus.FAILED
                    ? "Impact cache unavailable" : "Building impact cache", x, y,
                    cacheView.status() == ImpactCacheStatus.FAILED ? CRITICAL : WARNING, false);
            y += 16;
            return drawWrapped(graphics, cacheView.message(), x, y, availableWidth, MUTED);
        }
        RoutingModeImpact mode = report.modes().get(selectedMode);
        if (mode == null) {
            selectedMode = report.modes().keySet().stream().sorted().findFirst().orElseThrow();
            mode = report.modes().get(selectedMode);
        }

        graphics.drawString(font, "Remove " + report.removedTerritory(), x, y, severityColor(report.maximumSeverity()), false);
        y += 14;
        y = drawWrapped(graphics, report.ownerRelation() + (report.headquartersRemoved() ? " · HEADQUARTERS" : "")
                + " · revision " + report.sourceRevision(), x, y, availableWidth, MUTED);
        y = drawWrapped(graphics, "Cache key " + report.cacheKey().substring(0, Math.min(16, report.cacheKey().length()))
                + "…", x, y, availableWidth, MUTED);
        if (cacheView.reportsAreStale()) {
            y = drawWrapped(graphics, "STALE: a newer cache generation is still building.", x, y + 2, availableWidth, WARNING);
        }

        graphics.drawString(font, pretty(selectedMode) + " evidence boundary", x, y + 4, TEXT, false);
        y += 17;
        y = drawWrapped(graphics, "Connectivity/chokepoints: " + certainty(mode.topologyCertainty())
                + " · selected routes: " + certainty(mode.selectedRouteCertainty())
                + " · resources/towers: " + certainty(mode.economyCertainty()), x + 8, y, availableWidth - 8, MUTED);

        y = renderScore(graphics, "Defensive severity", mode.defensiveScore(), x, y + 5, availableWidth);
        y = renderScore(graphics, "Offensive severity", mode.offensiveScore(), x, y + 5, availableWidth);

        long disconnected = count(mode, RouteChangeKind.DISCONNECTED);
        long rerouted = count(mode, RouteChangeKind.REROUTED);
        long unchanged = count(mode, RouteChangeKind.UNCHANGED);
        long newlyCritical = mode.routeImpacts().stream()
                .flatMap(value -> value.newlyCriticalTerritories().stream()).distinct().count();
        graphics.drawString(font, "Route diff", x, y + 5, TEXT, false);
        y += 18;
        y = drawWrapped(graphics, disconnected + " disconnected · " + rerouted + " rerouted · "
                + unchanged + " unchanged · " + newlyCritical + " newly critical", x + 8, y, availableWidth - 8, MUTED);
        List<TerritoryRouteImpact> changedRoutes = mode.routeImpacts().stream()
                .filter(TerritoryRouteImpact::changed)
                .sorted(Comparator.comparing(TerritoryRouteImpact::sourceTerritory))
                .toList();
        if (changedRoutes.isEmpty()) {
            graphics.drawString(font, "No owned route changes.", x + 8, y, GOOD, false);
            y += 12;
        }
        for (TerritoryRouteImpact route : changedRoutes) {
            y = drawWrapped(graphics, route.sourceTerritory() + " · " + route.changes(), x + 8, y, availableWidth - 8, TEXT);
            y = drawWrapped(graphics, "Before: " + routeText(route.baselineRoute()) + " ("
                    + route.baselineDeliverySeconds() + "s)", x + 16, y, availableWidth - 16, MUTED);
            y = drawWrapped(graphics, "After: " + routeText(route.simulatedRoute()) + " ("
                    + route.simulatedDeliverySeconds() + "s, delta " + signed(route.deliveryDeltaSeconds()) + "s)",
                    x + 16, y, availableWidth - 16, MUTED);
            y = drawWrapped(graphics, "Selection cost: " + number(route.baselineSelectionCost()) + " -> "
                    + number(route.simulatedSelectionCost()) + " (delta "
                    + signed(route.simulatedSelectionCost() - route.baselineSelectionCost()) + ")",
                    x + 16, y, availableWidth - 16, MUTED);
            if (!route.newlyCriticalTerritories().isEmpty()) {
                y = drawWrapped(graphics, "Newly critical: " + String.join(", ", route.newlyCriticalTerritories()),
                        x + 16, y, availableWidth - 16, WARNING);
            }
        }

        graphics.drawString(font, "Estimated economy delta", x, y + 5, TEXT, false);
        y += 18;
        for (ResourceType resource : ResourceType.values()) {
            ResourceImpactDelta delta = mode.resourceDeltas().get(resource);
            y = drawWrapped(
                    graphics,
                    pretty(resource) + ": delivery " + number(delta.baselineDeliveredPerHour()) + " -> "
                            + number(delta.simulatedDeliveredPerHour()) + " (" + signed(delta.deliveredDeltaPerHour())
                            + "/h) · tax " + number(delta.baselineTaxLossPerHour()) + " -> "
                            + number(delta.simulatedTaxLossPerHour()) + " (" + signed(delta.taxLossDeltaPerHour())
                            + "/h) · tower " + number(delta.baselineTowerSupplyPerHour()) + " -> "
                            + number(delta.simulatedTowerSupplyPerHour()) + " (" + signed(delta.towerSupplyDeltaPerHour())
                            + "/h) · deficit " + number(delta.baselineDeficitPerHour()) + " -> "
                            + number(delta.simulatedDeficitPerHour()) + " (" + signed(delta.deficitDeltaPerHour())
                            + "/h) · storage " + number(delta.baselineEndingStorage()) + " -> "
                            + number(delta.simulatedEndingStorage()) + " (" + signed(delta.endingStorageDelta()) + ")",
                    x + 8,
                    y,
                    availableWidth - 8,
                    allZero(delta) ? MUTED : TEXT);
        }

        List<String> missing = new ArrayList<>(report.missingInputs());
        missing.addAll(mode.defensiveScore().missingInputs());
        missing.addAll(mode.offensiveScore().missingInputs());
        graphics.drawString(font, "Missing inputs / limits", x, y + 5, TEXT, false);
        y += 18;
        for (String value : missing.stream().distinct().toList()) {
            y = drawWrapped(graphics, "• " + value, x + 8, y, availableWidth - 8, WARNING);
        }
        graphics.drawString(font, "Diagnostics", x, y + 5, TEXT, false);
        y += 18;
        for (var diagnostic : mode.diagnostics().stream().limit(20).toList()) {
            y = drawWrapped(graphics, diagnostic.code() + ": " + diagnostic.message(), x + 8, y, availableWidth - 8, MUTED);
        }
        return y;
    }

    private int renderScore(
            GuiGraphics graphics, String label, ImpactScore score, int x, int y, int availableWidth) {
        graphics.drawString(font, label + ": " + number(score.score()) + "/100 · " + score.severity(),
                x, y, severityColor(score.severity()), false);
        y += 13;
        for (var factor : score.factors()) {
            y = drawWrapped(graphics, factor.label() + ": input " + number(factor.input()) + " × "
                    + number(factor.weight()) + " -> +" + number(factor.contribution())
                    + " (" + factor.formula() + ")", x + 8, y, availableWidth - 8, MUTED);
        }
        return y;
    }

    private void select(String territory) {
        selectedTerritory = territory;
        selectedMode = null;
        detailScroll = 0;
        rebuildWidgets();
    }

    private void showAffectedRoute() {
        TerritoryImpactReport report = selectedReport();
        if (report == null || minecraft == null) return;
        RoutingModeImpact mode = report.modes().get(selectedMode);
        TerritoryRouteImpact route = highlightImpact(mode);
        if (route == null) return;
        RouteHighlightController.highlight(
                EcoSnapshot.from(repository.snapshot(), System.currentTimeMillis()),
                route.baselineRoute(),
                "Impact of removing " + report.removedTerritory());
        minecraft.setScreen(GuildMapScreen.create(this));
    }

    private static TerritoryRouteImpact highlightImpact(RoutingModeImpact mode) {
        if (mode == null) return null;
        return mode.routeImpacts().stream()
                .filter(TerritoryRouteImpact::changed)
                .filter(value -> value.baselineRoute().size() > 1)
                .findFirst().orElse(null);
    }

    private void refreshAndRebuild() {
        refreshView();
        rebuildWidgets();
    }

    private void refreshView() {
        cacheView = cache.view();
        reports = cacheView.completedReports().values().stream()
                .sorted(Comparator.comparing(TerritoryImpactReport::maximumSeverity).reversed()
                        .thenComparing(TerritoryImpactReport::removedTerritory))
                .toList();
        if (reports.stream().noneMatch(value -> value.removedTerritory().equals(selectedTerritory))) {
            selectedTerritory = reports.isEmpty() ? "" : reports.getFirst().removedTerritory();
            selectedMode = null;
        }
        lastViewSignature = cacheView.generation() + ":" + cacheView.status() + ":"
                + (cacheView.completedTargets() / 10) + ":" + cacheView.completedReports().size();
    }

    private TerritoryImpactReport selectedReport() {
        return reports.stream()
                .filter(value -> value.removedTerritory().equals(selectedTerritory))
                .findFirst().orElse(null);
    }

    private String cacheStatus() {
        return switch (cacheView.status()) {
            case EMPTY -> "No cache request yet.";
            case BUILDING -> "Building generation " + cacheView.generation() + ": "
                    + cacheView.completedTargets() + "/" + cacheView.totalTargets()
                    + (cacheView.reportsAreStale() ? " · previous completed cache shown as stale" : "");
            case READY -> "Ready · revision " + cacheView.requestedRevision() + " · "
                    + cacheView.completedReports().size() + " territories · " + cacheView.buildDurationMillis() + "ms";
            case FAILED -> "Could not build impact cache: " + cacheView.message();
            case CLOSED -> "Impact cache is closed.";
        };
    }

    private int cacheStatusColor() {
        return switch (cacheView.status()) {
            case READY -> GOOD;
            case FAILED, CLOSED -> CRITICAL;
            case EMPTY, BUILDING -> WARNING;
        };
    }

    private static long count(RoutingModeImpact mode, RouteChangeKind kind) {
        return mode.routeImpacts().stream().filter(value -> value.changes().contains(kind)).count();
    }

    private static boolean allZero(ResourceImpactDelta value) {
        return Math.abs(value.deliveredDeltaPerHour()) < 1.0e-9
                && Math.abs(value.taxLossDeltaPerHour()) < 1.0e-9
                && Math.abs(value.towerSupplyDeltaPerHour()) < 1.0e-9
                && Math.abs(value.deficitDeltaPerHour()) < 1.0e-9
                && Math.abs(value.endingStorageDelta()) < 1.0e-9;
    }

    private void renderTerritoryScrollbar(GuiGraphics graphics, Layout layout) {
        int visible = visibleRows(layout);
        if (reports.size() <= visible) return;
        int trackX = layout.leftX() + layout.leftWidth() - 4;
        int trackY = layout.top() + 29;
        int trackHeight = visible * 27 - 2;
        int thumbHeight = Math.max(12, trackHeight * visible / reports.size());
        int maximum = reports.size() - visible;
        int thumbY = trackY + (trackHeight - thumbHeight) * territoryScroll / maximum;
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

    private int drawWrapped(GuiGraphics graphics, String value, int x, int y, int availableWidth, int color) {
        List<FormattedCharSequence> lines = font.split(Component.literal(value), Math.max(30, availableWidth));
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
        int leftWidth = Math.min(270, Math.max(150, width / 3));
        int rightX = leftX + leftWidth + 7;
        return new Layout(leftX, leftWidth, rightX, Math.max(120, width - rightX - 8), top, bottom - top);
    }

    private int visibleRows(Layout layout) {
        return Math.max(1, (layout.height() - 35) / 27);
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

    private static String routeText(List<String> route) {
        return route.isEmpty() ? "No route" : String.join(" -> ", route);
    }

    private static String certainty(ImpactCertainty certainty) {
        return switch (certainty) {
            case EXACT_OBSERVATION -> "exact observed topology";
            case ESTIMATED -> "estimated";
            case UNAVAILABLE -> "unavailable";
        };
    }

    private static String severityMark(ImpactSeverity severity) {
        return switch (severity) {
            case MINOR -> "i";
            case WARNING -> "!";
            case CRITICAL -> "!!";
            case CATASTROPHIC -> "◆";
        };
    }

    private static int severityColor(ImpactSeverity severity) {
        return switch (severity) {
            case MINOR -> GOOD;
            case WARNING -> WARNING;
            case CRITICAL -> CRITICAL;
            case CATASTROPHIC -> CATASTROPHIC;
        };
    }

    private static String pretty(Enum<?> value) {
        String lower = value.name().toLowerCase(Locale.ROOT).replace('_', ' ');
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

    private static void drawPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, PANEL_BORDER);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, PANEL);
    }

    private record Layout(int leftX, int leftWidth, int rightX, int rightWidth, int top, int height) {}
}
