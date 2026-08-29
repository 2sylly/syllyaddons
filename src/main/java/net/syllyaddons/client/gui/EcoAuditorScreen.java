package net.syllyaddons.client.gui;

import com.wynntils.screens.maps.GuildMapScreen;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.syllyaddons.audit.AuditCalculation;
import net.syllyaddons.audit.AuditFinding;
import net.syllyaddons.audit.AuditFindingType;
import net.syllyaddons.audit.AuditProvenanceReference;
import net.syllyaddons.audit.AuditReport;
import net.syllyaddons.audit.AuditSeverity;
import net.syllyaddons.audit.EcoAuditor;
import net.syllyaddons.observation.ObservedStateRepository;
import net.syllyaddons.snapshot.ObservedEconomyAnalyzer;
import net.syllyaddons.snapshot.SnapshotPayload;

/** Scrollable Track 6 report with calculation and provenance drill-down. */
public final class EcoAuditorScreen extends Screen {
    private static final int BACKGROUND = 0xF00E1420;
    private static final int HEADER = 0xFF172131;
    private static final int PANEL = 0xFF171F2D;
    private static final int PANEL_BORDER = 0xFF344158;
    private static final int TEXT = 0xFFF1F4FA;
    private static final int MUTED = 0xFFA5B1C7;
    private static final int GOOD = 0xFF9DDEB2;
    private static final int WARNING = 0xFFFFD166;
    private static final int CRITICAL = 0xFFFF7B86;
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final Screen parent;
    private final ObservedStateRepository repository;
    private final ObservedEconomyAnalyzer analyzer = new ObservedEconomyAnalyzer();
    private final EcoAuditor auditor = new EcoAuditor();
    private SnapshotPayload payload;
    private AuditReport report;
    private int selectedIndex;
    private int findingScroll;
    private int detailScroll;
    private int maxDetailScroll;
    private String status = "Refreshes from passive observations only.";

    public EcoAuditorScreen(Screen parent, ObservedStateRepository repository) {
        super(Component.literal("Sylly Addons Eco Auditor"));
        this.parent = parent;
        this.repository = java.util.Objects.requireNonNull(repository, "repository");
    }

    @Override
    protected void init() {
        if (report == null) refresh(false);
        addButton("Back", 10, 14, 48, "Return to settings.", this::onClose);
        addButton("Refresh", 64, 14, 64, "Re-run checks against the latest passive observations.", () -> refresh(true));

        Layout layout = layout();
        int visible = visibleFindingRows(layout);
        int maximum = Math.max(0, report.findings().size() - visible);
        findingScroll = Math.clamp(findingScroll, 0, maximum);
        int end = Math.min(report.findings().size(), findingScroll + visible);
        for (int index = findingScroll; index < end; index++) {
            AuditFinding finding = report.findings().get(index);
            int rowY = layout.top() + 28 + (index - findingScroll) * 31;
            String marker = index == selectedIndex ? "> " : "";
            String label = marker + severityMark(finding.severity()) + " " + trim(finding.title(), layout.leftWidth() - 30);
            int selected = index;
            addButton(
                    label,
                    layout.leftX() + 7,
                    rowY,
                    layout.leftWidth() - 14,
                    finding.summary(),
                    () -> select(selected));
        }

        AuditFinding selected = selectedFinding();
        if (selected != null) {
            AuditProvenanceReference reference = selected.provenance().stream()
                    .filter(value -> !value.route().isEmpty())
                    .findFirst().orElse(null);
            Button map = addButton(
                    "Show route",
                    layout.rightX() + layout.rightWidth() - 90,
                    layout.top() + 7,
                    82,
                    reference == null ? "No routed provenance is attached to this finding."
                            : String.join(" -> ", reference.route()),
                    this::showRoute);
            map.active = reference != null;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, BACKGROUND);
        graphics.fill(0, 0, width, 45, HEADER);
        if (width >= 350) graphics.drawString(font, "Explainable economy checks", 139, 20, TEXT, false);

        Layout layout = layout();
        drawPanel(graphics, layout.leftX(), layout.top(), layout.leftWidth(), layout.height());
        drawPanel(graphics, layout.rightX(), layout.top(), layout.rightWidth(), layout.height());
        graphics.drawString(font, report.findings().size() + " findings", layout.leftX() + 8, layout.top() + 10, TEXT, false);
        renderFindingScrollbar(graphics, layout);

        int contentTop = layout.top() + 33;
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

        graphics.drawString(font, trim(status, width - 20), 10, height - 14, statusColor(), false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount == 0) return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        Layout layout = layout();
        if (mouseX < layout.rightX()) {
            int maximum = Math.max(0, report.findings().size() - visibleFindingRows(layout));
            int next = Math.clamp(findingScroll + (verticalAmount > 0 ? -1 : 1), 0, maximum);
            if (next != findingScroll) {
                findingScroll = next;
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

    private void refresh(boolean announce) {
        long now = System.currentTimeMillis();
        payload = analyzer.analyze(repository.snapshot(), now);
        report = auditor.audit(payload, now);
        selectedIndex = Math.clamp(selectedIndex, 0, Math.max(0, report.findings().size() - 1));
        findingScroll = 0;
        detailScroll = 0;
        if (announce) {
            status = "Audited revision " + report.sourceRevision() + ": " + report.findings().size() + " finding(s).";
            rebuildWidgets();
        }
    }

    private void select(int index) {
        selectedIndex = index;
        detailScroll = 0;
        rebuildWidgets();
    }

    private void showRoute() {
        AuditFinding finding = selectedFinding();
        if (finding == null || minecraft == null) return;
        AuditProvenanceReference reference = finding.provenance().stream()
                .filter(value -> !value.route().isEmpty())
                .findFirst().orElse(null);
        if (reference == null) return;
        RouteHighlightController.highlight(payload.observed(), reference.route(), finding.title());
        minecraft.setScreen(GuildMapScreen.create(this));
    }

    private AuditFinding selectedFinding() {
        if (report == null || report.findings().isEmpty()) return null;
        selectedIndex = Math.clamp(selectedIndex, 0, report.findings().size() - 1);
        return report.findings().get(selectedIndex);
    }

    private int renderDetails(GuiGraphics graphics, int x, int y, int availableWidth) {
        AuditFinding finding = selectedFinding();
        if (finding == null) {
            graphics.drawString(font, "No actionable findings", x, y, GOOD, false);
            y += 16;
            y = drawWrapped(graphics, "Checks with insufficient inputs are listed below instead of being guessed.", x, y, availableWidth, MUTED);
            return renderDiagnostics(graphics, x, y + 6, availableWidth);
        }

        graphics.drawString(font, finding.title(), x, y, severityColor(finding.severity()), false);
        y += 14;
        y = drawWrapped(graphics, finding.summary(), x, y, availableWidth, TEXT);
        y = drawWrapped(graphics, "Categories: " + finding.categories().stream().map(EcoAuditorScreen::categoryName).sorted().collect(java.util.stream.Collectors.joining(", ")), x, y + 2, availableWidth, MUTED);

        graphics.drawString(font, "Affected territories", x, y + 4, TEXT, false);
        y += 17;
        y = drawWrapped(graphics, finding.affectedTerritories().isEmpty() ? "None named" : String.join(", ", finding.affectedTerritories()), x + 8, y, availableWidth - 8, MUTED);

        graphics.drawString(font, "Arithmetic", x, y + 4, TEXT, false);
        y += 17;
        if (finding.calculations().isEmpty()) {
            graphics.drawString(font, "No numeric claim", x + 8, y, MUTED, false);
            y += 12;
        }
        for (AuditCalculation calculation : finding.calculations()) {
            y = drawWrapped(graphics, calculation.label() + ": " + number(calculation.result()) + calculation.unit(), x + 8, y, availableWidth - 8, TEXT);
            y = drawWrapped(graphics, calculation.formula(), x + 16, y, availableWidth - 16, MUTED);
            String inputs = calculation.inputs().entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + number(entry.getValue()))
                    .collect(java.util.stream.Collectors.joining(", "));
            y = drawWrapped(graphics, "Inputs: " + inputs, x + 16, y, availableWidth - 16, MUTED);
        }

        if (!finding.routeFacts().isEmpty()) {
            graphics.drawString(font, "Route/rule facts", x, y + 4, TEXT, false);
            y += 17;
            for (String fact : finding.routeFacts()) y = drawWrapped(graphics, "• " + fact, x + 8, y, availableWidth - 8, MUTED);
        }

        graphics.drawString(font, "Provenance", x, y + 4, TEXT, false);
        y += 17;
        if (finding.provenance().isEmpty()) {
            graphics.drawString(font, "No resource lot attached", x + 8, y, MUTED, false);
            y += 12;
        }
        for (AuditProvenanceReference reference : finding.provenance()) {
            y = drawWrapped(
                    graphics,
                    pretty(reference.resource()) + " from " + reference.sourceTerritory() + ": gross "
                            + number(reference.grossAmount()) + ", delivered " + number(reference.deliveredAmount())
                            + ", tax " + number(reference.taxLoss()),
                    x + 8,
                    y,
                    availableWidth - 8,
                    MUTED);
            y = drawWrapped(graphics, reference.route().isEmpty() ? "No route" : String.join(" -> ", reference.route()), x + 16, y, availableWidth - 16, MUTED);
        }

        var evidence = finding.evidence();
        graphics.drawString(font, "Evidence and freshness", x, y + 4, TEXT, false);
        y += 17;
        y = drawWrapped(graphics, evidence.weakestKind() + " · oldest " + time(evidence.oldestObservedAtEpochMillis())
                + " · age " + humanAge(evidence.ageAtAuditMillis()), x + 8, y, availableWidth - 8, MUTED);
        y = drawWrapped(graphics, "Sources: " + String.join(", ", evidence.sources()), x + 8, y, availableWidth - 8, MUTED);
        y = drawWrapped(graphics, "Versions: " + String.join(", ", evidence.sourceVersions()), x + 8, y, availableWidth - 8, MUTED);

        graphics.drawString(font, "Missing inputs / limits", x, y + 4, TEXT, false);
        y += 17;
        if (finding.missingInputs().isEmpty()) {
            graphics.drawString(font, "None specific to this finding", x + 8, y, GOOD, false);
            y += 12;
        } else {
            for (String missing : finding.missingInputs()) y = drawWrapped(graphics, "• " + missing, x + 8, y, availableWidth - 8, WARNING);
        }
        return renderDiagnostics(graphics, x, y + 5, availableWidth);
    }

    private int renderDiagnostics(GuiGraphics graphics, int x, int y, int availableWidth) {
        graphics.drawString(font, "Report diagnostics", x, y, TEXT, false);
        y += 13;
        if (report.diagnostics().isEmpty()) {
            graphics.drawString(font, "None", x + 8, y, GOOD, false);
            return y + 12;
        }
        for (var diagnostic : report.diagnostics()) {
            y = drawWrapped(graphics, diagnostic.code() + ": " + diagnostic.message(), x + 8, y, availableWidth - 8, MUTED);
        }
        return y;
    }

    private void renderFindingScrollbar(GuiGraphics graphics, Layout layout) {
        int visible = visibleFindingRows(layout);
        if (report.findings().size() <= visible) return;
        int trackX = layout.leftX() + layout.leftWidth() - 4;
        int trackY = layout.top() + 28;
        int trackHeight = visible * 31 - 2;
        int thumbHeight = Math.max(12, trackHeight * visible / report.findings().size());
        int max = report.findings().size() - visible;
        int thumbY = trackY + (trackHeight - thumbHeight) * findingScroll / max;
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
        int leftWidth = Math.min(280, Math.max(150, width / 3));
        int rightX = leftX + leftWidth + 7;
        return new Layout(leftX, leftWidth, rightX, Math.max(120, width - rightX - 8), top, bottom - top);
    }

    private int visibleFindingRows(Layout layout) {
        return Math.max(1, (layout.height() - 34) / 31);
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

    private int statusColor() {
        return status.toLowerCase(Locale.ROOT).contains("could not") ? CRITICAL : GOOD;
    }

    private static String severityMark(AuditSeverity severity) {
        return switch (severity) {
            case CRITICAL -> "!!";
            case WARNING -> "!";
            case INFO -> "i";
        };
    }

    private static int severityColor(AuditSeverity severity) {
        return switch (severity) {
            case CRITICAL -> CRITICAL;
            case WARNING -> WARNING;
            case INFO -> GOOD;
        };
    }

    private static String categoryName(AuditFindingType type) {
        return type.name().toLowerCase(Locale.ROOT).replace('_', ' ');
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

    private static String time(long epochMillis) {
        return epochMillis == 0 ? "unknown" : TIME.format(Instant.ofEpochMilli(epochMillis));
    }

    private static String humanAge(long millis) {
        long seconds = millis / 1_000;
        if (seconds < 60) return seconds + "s";
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + "m";
        long hours = minutes / 60;
        return hours < 48 ? hours + "h" : (hours / 24) + "d";
    }

    private static void drawPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, PANEL_BORDER);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, PANEL);
    }

    private record Layout(int leftX, int leftWidth, int rightX, int rightWidth, int top, int height) {}
}
