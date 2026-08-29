package net.syllyaddons.client.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.syllyaddons.domain.Evidence;
import net.syllyaddons.domain.ObservedState;
import net.syllyaddons.domain.ObservedValue;
import net.syllyaddons.domain.TerritoryState;
import net.syllyaddons.observation.DataHealthReport;
import net.syllyaddons.observation.DataHealthService;
import net.syllyaddons.observation.DataIssueType;
import net.syllyaddons.observation.FreshnessPolicy;
import net.syllyaddons.observation.ObservedStateRepository;

/** Read-only, scrollable inspection screen for Track 1 observations and their provenance. */
public final class ObservedStateDebugScreen extends Screen {
    private static final int LINE_HEIGHT = 11;
    private static final int CONTENT_TOP = 32;
    private static final int CONTENT_BOTTOM_MARGIN = 20;

    private final Screen parent;
    private final ObservedStateRepository repository;
    private final DataHealthService dataHealthService =
            new DataHealthService(FreshnessPolicy.personalDefaults());
    private List<String> lines = List.of();
    private long renderedRevision = -1;
    private long renderedAtSecond = -1;
    private int scrollLine;

    public ObservedStateDebugScreen(ObservedStateRepository repository) {
        this(null, repository);
    }

    public ObservedStateDebugScreen(Screen parent, ObservedStateRepository repository) {
        super(Component.translatable("screen.syllyaddons.data_status"));
        this.parent = parent;
        this.repository = java.util.Objects.requireNonNull(repository, "repository");
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Do not call Screen#renderBackground here. Other client UI mods can already request the one blur allowed per
        // frame in 1.21.11, and a second request crashes with "Can only blur once per frame".
        graphics.fill(0, 0, width, height, 0xD0101010);

        long now = System.currentTimeMillis();
        refreshLines(now);
        int visibleLines = visibleLineCount();
        scrollLine = Math.min(scrollLine, maxScrollLine(visibleLines));

        graphics.drawCenteredString(font, title, width / 2, 10, 0xFFFFFFFF);
        graphics.enableScissor(8, CONTENT_TOP, Math.max(9, width - 8), Math.max(CONTENT_TOP + 1, height - CONTENT_BOTTOM_MARGIN));
        int end = Math.min(lines.size(), scrollLine + visibleLines);
        for (int index = scrollLine; index < end; index++) {
            graphics.drawString(font, lines.get(index), 12, CONTENT_TOP + (index - scrollLine) * LINE_HEIGHT, color(lines.get(index)), false);
        }
        graphics.disableScissor();

        String footer = lines.isEmpty()
                ? "No observations"
                : (scrollLine + 1) + "-" + Math.min(lines.size(), scrollLine + visibleLines) + " / " + lines.size()
                        + "  |  mouse wheel to scroll  |  Esc to close";
        graphics.drawCenteredString(font, footer, width / 2, Math.max(CONTENT_TOP, height - 13), 0xFFA0A0A0);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int direction = (int) Math.signum(verticalAmount);
        if (direction == 0) return false;
        scrollLine = Math.max(0, Math.min(maxScrollLine(visibleLineCount()), scrollLine - direction * 4));
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void refreshLines(long nowEpochMillis) {
        ObservedState state = repository.snapshot();
        long nowSecond = nowEpochMillis / 1_000;
        if (state.revision() == renderedRevision && nowSecond == renderedAtSecond) return;

        renderedRevision = state.revision();
        renderedAtSecond = nowSecond;
        DataHealthReport report = dataHealthService.assess(state, nowEpochMillis);
        Map<DataIssueType, Long> issueCounts = report.countsByType();
        List<String> rebuilt = new ArrayList<>();
        rebuilt.add("State revision " + state.revision() + " | assembled " + age(state.assembledAtEpochMillis(), nowEpochMillis) + " ago");
        rebuilt.add("Health: " + (report.healthy() ? "healthy" : issueCounts));
        append(rebuilt, "character", state.character(), nowEpochMillis);
        append(rebuilt, "guild", state.guild(), nowEpochMillis);
        append(rebuilt, "hqTerritory", state.hqTerritory(), nowEpochMillis);
        append(rebuilt, "routingMode", state.routingMode(), nowEpochMillis);

        state.territories().values().stream()
                .sorted(Comparator.comparing(TerritoryState::name))
                .forEach(territory -> appendTerritory(rebuilt, territory, nowEpochMillis));
        lines = List.copyOf(rebuilt);
    }

    private static void appendTerritory(List<String> destination, TerritoryState territory, long nowEpochMillis) {
        destination.add("");
        destination.add("[" + territory.name() + "]");
        append(destination, "  owner", territory.owner(), nowEpochMillis);
        append(destination, "  acquiredAtEpochMillis", territory.acquiredAtEpochMillis(), nowEpochMillis);
        append(destination, "  headquarters", territory.headquarters(), nowEpochMillis);
        append(destination, "  bounds", territory.bounds(), nowEpochMillis);
        append(destination, "  links", territory.links(), nowEpochMillis);
        append(destination, "  resources", territory.resources(), nowEpochMillis);
        append(destination, "  treasury", territory.treasury(), nowEpochMillis);
        append(destination, "  treasuryBonusPercent", territory.treasuryBonusPercent(), nowEpochMillis);
        append(destination, "  defences", territory.defences(), nowEpochMillis);
        append(destination, "  upgrades", territory.upgrades(), nowEpochMillis);
        append(destination, "  alerts", territory.alerts(), nowEpochMillis);
    }

    private static void append(
            List<String> destination, String label, ObservedValue<?> observed, long nowEpochMillis) {
        Evidence evidence = observed.evidence();
        if (!observed.isKnown()) {
            destination.add(label + " = UNKNOWN | " + evidence.note());
            return;
        }

        String note = evidence.note().isBlank() ? "" : " | " + evidence.note();
        destination.add(label + " = " + observed.value()
                + " | " + evidence.kind()
                + " | " + evidence.source() + "@" + evidence.sourceVersion()
                + " | age " + age(evidence.observedAtEpochMillis(), nowEpochMillis)
                + note);
    }

    private static String age(long observedAtEpochMillis, long nowEpochMillis) {
        if (observedAtEpochMillis <= 0) return "never";
        long seconds = Math.max(0, (nowEpochMillis - observedAtEpochMillis) / 1_000);
        if (seconds < 60) return seconds + "s";
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + "m " + (seconds % 60) + "s";
        long hours = minutes / 60;
        return hours + "h " + (minutes % 60) + "m";
    }

    private int visibleLineCount() {
        return Math.max(1, (height - CONTENT_TOP - CONTENT_BOTTOM_MARGIN) / LINE_HEIGHT);
    }

    private int maxScrollLine(int visibleLines) {
        return Math.max(0, lines.size() - visibleLines);
    }

    private static int color(String line) {
        if (line.contains("UNKNOWN")) return 0xFFFFAA55;
        if (line.startsWith("Health:") && !line.endsWith("healthy")) return 0xFFFFFF55;
        if (line.startsWith("[")) return 0xFF55FFFF;
        return 0xFFE0E0E0;
    }
}
