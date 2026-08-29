package net.syllyaddons.client.gui;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.syllyaddons.config.SyllyConfig;
import net.syllyaddons.config.SyllyConfigSection;
import net.syllyaddons.config.SyllyConfigService;
import net.syllyaddons.config.RoutingAdvisorConfig;
import net.syllyaddons.impact.TerritoryImpactCache;
import net.syllyaddons.observation.ObservedStateRepository;
import net.syllyaddons.profile.SpellProfileService;
import net.syllyaddons.snapshot.SnapshotManagerService;

/** Track 3 configuration shell. Feature switches are persisted immediately after validation. */
public final class SyllySettingsScreen extends Screen {
    private static final int BACKGROUND = 0xF00E1420;
    private static final int HEADER = 0xFF172131;
    private static final int PANEL = 0xFF171F2D;
    private static final int PANEL_BORDER = 0xFF344158;
    private static final int SELECTED = 0xFF2E4770;
    private static final int TEXT = 0xFFF1F4FA;
    private static final int MUTED = 0xFFA5B1C7;
    private static final int GOOD = 0xFF9DDEB2;
    private static final int WARNING = 0xFFFFD166;

    private final Screen parent;
    private final SyllyConfigService settings;
    private final java.util.function.Supplier<SpellProfileService> profilesSupplier;
    private final ObservedStateRepository repository;
    private final SnapshotManagerService snapshotManager;
    private final TerritoryImpactCache territoryImpactCache;
    private final Map<SyllyConfigSection, Button> sectionButtons = new EnumMap<>(SyllyConfigSection.class);
    private final List<SettingRow> rows = new ArrayList<>();

    private SyllyConfigSection section = SyllyConfigSection.PROFILES;
    private String searchQuery = "";
    private String status = "Settings save immediately.";
    private EditBox searchBox;
    private int contentX;
    private int contentRight;
    private int nextRowY;

    public SyllySettingsScreen(
            Screen parent,
            SyllyConfigService settings,
            java.util.function.Supplier<SpellProfileService> profilesSupplier,
            ObservedStateRepository repository,
            SnapshotManagerService snapshotManager,
            TerritoryImpactCache territoryImpactCache) {
        super(Component.translatable("screen.syllyaddons.settings"));
        this.parent = parent;
        this.settings = Objects.requireNonNull(settings, "settings");
        this.profilesSupplier = Objects.requireNonNull(profilesSupplier, "profilesSupplier");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.snapshotManager = Objects.requireNonNull(snapshotManager, "snapshotManager");
        this.territoryImpactCache = Objects.requireNonNull(territoryImpactCache, "territoryImpactCache");
    }

    @Override
    protected void init() {
        rows.clear();
        sectionButtons.clear();

        int navWidth = Math.min(150, Math.max(112, width / 4));
        contentX = 12 + navWidth + 8;
        contentRight = width - 12;
        nextRowY = 88;

        searchBox = new EditBox(
                font,
                Math.max(165, width / 2 - 105),
                25,
                Math.min(210, Math.max(90, width - 265)),
                20,
                Component.literal("Search settings"));
        searchBox.setHint(Component.literal("Search settings..."));
        searchBox.setMaxLength(64);
        searchBox.setValue(searchQuery);
        searchBox.setResponder(value -> {
            searchQuery = value;
            applySearchFilter();
        });
        searchBox.setTooltip(Tooltip.create(Component.literal(
                "Filter sections and fields by name or description.")));
        addRenderableWidget(searchBox);

        addButton("Done", width - 64, 25, 52, "Return to the previous screen.", this::onClose);

        int navY = 54;
        for (SyllyConfigSection candidate : SyllyConfigSection.values()) {
            SectionInfo info = SectionInfo.forSection(candidate);
            Button button = addButton(
                    info.label(),
                    12,
                    navY,
                    navWidth,
                    info.description(),
                    () -> selectSection(candidate));
            button.active = candidate != section;
            sectionButtons.put(candidate, button);
            navY += 20;
        }

        if (isResettable(section)) {
            addButton("Reset section", contentRight - 104, 57, 92,
                    "Restore every setting in this section to its default.", this::resetSection);
        }

        buildSection();
        applySearchFilter();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, BACKGROUND);
        graphics.fill(0, 0, width, 49, HEADER);
        drawPanel(graphics, 8, 51, contentX - 16, Math.max(24, height - 60));
        drawPanel(graphics, contentX, 51, Math.max(24, contentRight - contentX), Math.max(24, height - 60));

        graphics.drawString(font, title, 12, 10, TEXT, false);
        SectionInfo info = SectionInfo.forSection(section);
        graphics.drawString(font, info.label(), contentX + 12, 59, TEXT, false);
        graphics.drawString(font, info.description(), contentX + 12, 72, MUTED, false);

        int visibleRows = 0;
        for (SettingRow row : rows) {
            if (!row.primary().visible) continue;
            visibleRows++;
            graphics.drawString(font, row.label(), contentX + 12, row.y(), TEXT, false);
            graphics.drawString(font, row.description(), contentX + 12, row.y() + 12, MUTED, false);
        }
        if (visibleRows == 0) {
            graphics.drawString(font, "No settings in this section match the search.", contentX + 12, 94, MUTED, false);
        }

        int footerY = height - 15;
        settings.warning().ifPresent(warning -> graphics.drawString(
                font, trimToWidth(warning, width - 24), 12, footerY, WARNING, false));
        if (settings.warning().isEmpty()) {
            graphics.drawString(font, trimToWidth(status, width - 24), 12, footerY, statusColor(), false);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void buildSection() {
        switch (section) {
            case PROFILES -> buildProfiles();
            case CHARACTERS -> buildCharacters();
            case ECO_AUDITOR -> {
                SyllyConfig config = settings.snapshot();
                addActionRow(
                        "Audit report",
                        "Inspect economic findings, exact arithmetic, freshness, missing inputs, and route provenance.",
                        "Open auditor",
                        () -> {
                            if (minecraft != null) minecraft.setScreen(new EcoAuditorScreen(this, repository));
                        },
                        "eco audit report arithmetic provenance route findings freshness");
                addBooleanRow(
                        "Eco Auditor",
                        "Enable live economic checks and cooldown-controlled chat summaries.",
                        config.ecoAuditorEnabled(),
                        current -> current.withEcoAuditorEnabled(!current.ecoAuditorEnabled()),
                        current -> current.withEcoAuditorEnabled(SyllyConfig.defaults().ecoAuditorEnabled()),
                        "eco auditor enabled checks warnings");
                addIntegerRow(
                        "Warning cooldown",
                        "Minimum seconds between repeated auditor notices (5-600).",
                        config.ecoWarningCooldownSeconds(),
                        SyllyConfig.MIN_ECO_WARNING_COOLDOWN_SECONDS,
                        SyllyConfig.MAX_ECO_WARNING_COOLDOWN_SECONDS,
                        SyllyConfig::withEcoWarningCooldownSeconds,
                        current -> current.withEcoWarningCooldownSeconds(
                                SyllyConfig.defaults().ecoWarningCooldownSeconds()),
                        "eco warning cooldown seconds notifications");
            }
            case TERRITORY_IMPACT -> {
                addActionRow(
                        "Impact simulator",
                        "Inspect cached consequences of removing each territory from the current network.",
                        "Open simulator",
                        () -> {
                            territoryImpactCache.request(repository.snapshot(), System.currentTimeMillis());
                            if (minecraft != null) minecraft.setScreen(new TerritoryImpactScreen(
                                    this, repository, territoryImpactCache));
                        },
                        "territory impact simulator removal cache defensive offensive");
                addActionRow(
                        "Map overlays & alerts",
                        "Configure map filters and refresh-aware territory-loss alerts.",
                        "Open display controls",
                        () -> {
                            if (minecraft != null) minecraft.setScreen(new ImpactDisplaySettingsScreen(this, settings));
                        },
                        "overlay colouring own guild enemy resource delay disconnection alerts sound severity");
                addBooleanRow(
                        "Territory Impact",
                        "Precompute territory-removal consequences after relevant observations change.",
                        settings.snapshot().territoryImpactEnabled(),
                        current -> current.withTerritoryImpactEnabled(!current.territoryImpactEnabled()),
                        current -> current.withTerritoryImpactEnabled(SyllyConfig.defaults().territoryImpactEnabled()),
                        "territory impact enabled before after cache");
            }
            case ROUTING_ADVISOR -> {
                SyllyConfig config = settings.snapshot();
                RoutingAdvisorConfig advisor = config.routingAdvisor();
                addBooleanRow(
                        "Routing Advisor",
                        "Show passive Fastest/Cheapest advice on the open attack screen.",
                        config.routingAdvisorEnabled(),
                        current -> current.withRoutingAdvisorEnabled(!current.routingAdvisorEnabled()),
                        current -> current.withRoutingAdvisorEnabled(SyllyConfig.defaults().routingAdvisorEnabled()),
                        "routing advisor enabled route recommendations attack passive read only");
                addIntegerRow(
                        "Minimum time saving",
                        "Seconds Fastest must save before it can be recommended (0-7200).",
                        advisor.minimumTimeSavingSeconds(),
                        RoutingAdvisorConfig.MIN_TIME_SECONDS,
                        RoutingAdvisorConfig.MAX_TIME_SECONDS,
                        (current, value) -> current.withRoutingAdvisor(
                                current.routingAdvisor().withMinimumTimeSavingSeconds(value)),
                        current -> current.withRoutingAdvisor(current.routingAdvisor().withMinimumTimeSavingSeconds(
                                RoutingAdvisorConfig.defaults().minimumTimeSavingSeconds())),
                        "minimum time saving seconds fastest threshold");
                addIntegerRow(
                        "Maximum extra cost",
                        "Most extra emeralds Fastest may cost before Cheapest wins (0-16777216).",
                        advisor.maximumAdditionalCostEmeralds(),
                        RoutingAdvisorConfig.MIN_COST_EMERALDS,
                        RoutingAdvisorConfig.MAX_COST_EMERALDS,
                        (current, value) -> current.withRoutingAdvisor(
                                current.routingAdvisor().withMaximumAdditionalCostEmeralds(value)),
                        current -> current.withRoutingAdvisor(current.routingAdvisor().withMaximumAdditionalCostEmeralds(
                                RoutingAdvisorConfig.defaults().maximumAdditionalCostEmeralds())),
                        "maximum additional extra cost emeralds threshold");
                addBooleanRow(
                        "Active operations only",
                        "Hide the panel after the attack screen/queued validation ends.",
                        advisor.activeOperationsOnly(),
                        current -> current.withRoutingAdvisor(
                                current.routingAdvisor().withActiveOperationsOnly(
                                        !current.routingAdvisor().activeOperationsOnly())),
                        current -> current.withRoutingAdvisor(current.routingAdvisor().withActiveOperationsOnly(
                                RoutingAdvisorConfig.defaults().activeOperationsOnly())),
                        "active operations only attack screen queued panel history");
                addIntegerRow(
                        "Negligible delay",
                        "Time differences at or below this many seconds are insignificant (0-7200).",
                        advisor.insignificantTimeSeconds(),
                        RoutingAdvisorConfig.MIN_TIME_SECONDS,
                        RoutingAdvisorConfig.MAX_TIME_SECONDS,
                        (current, value) -> current.withRoutingAdvisor(
                                current.routingAdvisor().withInsignificantTimeSeconds(value)),
                        current -> current.withRoutingAdvisor(current.routingAdvisor().withInsignificantTimeSeconds(
                                RoutingAdvisorConfig.defaults().insignificantTimeSeconds())),
                        "negligible insignificant delay time seconds threshold");
                addIntegerRow(
                        "Negligible cost",
                        "Cost differences at or below this many emeralds are insignificant (0-16777216).",
                        advisor.insignificantCostEmeralds(),
                        RoutingAdvisorConfig.MIN_COST_EMERALDS,
                        RoutingAdvisorConfig.MAX_COST_EMERALDS,
                        (current, value) -> current.withRoutingAdvisor(
                                current.routingAdvisor().withInsignificantCostEmeralds(value)),
                        current -> current.withRoutingAdvisor(current.routingAdvisor().withInsignificantCostEmeralds(
                                RoutingAdvisorConfig.defaults().insignificantCostEmeralds())),
                        "negligible insignificant cost emeralds threshold");
            }
            case OPTIMIZER -> addBooleanRow(
                    "Optimizer",
                    "Enable bounded optimization tools when Track 10 is active.",
                    settings.snapshot().optimizerEnabled(),
                    current -> current.withOptimizerEnabled(!current.optimizerEnabled()),
                    current -> current.withOptimizerEnabled(SyllyConfig.defaults().optimizerEnabled()),
                    "optimizer enabled optimization");
            case SNAPSHOTS -> {
                SyllyConfig config = settings.snapshot();
                addActionRow(
                        "Snapshot manager",
                        "Export, import read-only, compare, and inspect resource provenance.",
                        "Open snapshots",
                        () -> {
                            if (minecraft != null) minecraft.setScreen(new SnapshotManagerScreen(this, snapshotManager));
                        },
                        "snapshot manager export import compare provenance tnsreco");
                addBooleanRow(
                        "Automatic snapshots",
                        "Allow automatic historical snapshots when Track 5 storage is active.",
                        config.automaticSnapshotsEnabled(),
                        current -> current.withAutomaticSnapshotsEnabled(!current.automaticSnapshotsEnabled()),
                        current -> current.withAutomaticSnapshotsEnabled(
                                SyllyConfig.defaults().automaticSnapshotsEnabled()),
                        "automatic snapshots enabled history");
                addIntegerRow(
                        "Snapshot retention",
                        "Maximum automatic snapshots retained locally (1-250).",
                        config.snapshotRetention(),
                        SyllyConfig.MIN_SNAPSHOT_RETENTION,
                        SyllyConfig.MAX_SNAPSHOT_RETENTION,
                        SyllyConfig::withSnapshotRetention,
                        current -> current.withSnapshotRetention(SyllyConfig.defaults().snapshotRetention()),
                        "snapshot retention maximum history count");
            }
            case NOTIFICATIONS -> {
                SyllyConfig config = settings.snapshot();
                addBooleanRow(
                        "Profile swap message",
                        "Show [SyllyAddons] Swapped to Profile ... in chat.",
                        config.profileSwapNotifications(),
                        current -> current.withProfileSwapNotifications(!current.profileSwapNotifications()),
                        current -> current.withProfileSwapNotifications(
                                SyllyConfig.defaults().profileSwapNotifications()),
                        "profile swap message chat notification");
                addBooleanRow(
                        "Configuration warnings",
                        "Show repaired or unsaved configuration warnings in chat.",
                        config.configurationWarnings(),
                        current -> current.withConfigurationWarnings(!current.configurationWarnings()),
                        current -> current.withConfigurationWarnings(SyllyConfig.defaults().configurationWarnings()),
                        "configuration warnings corrupt repaired unsaved chat");
            }
            case COMPATIBILITY -> buildCompatibility();
        }
    }

    private void buildProfiles() {
        SpellProfileService profiles = profilesSupplier.get();
        if (profiles == null) {
            addUnavailableRow("Profile editor", "Wynntils is still starting; reopen settings in a moment.");
            return;
        }
        if (!profiles.lastError().isBlank()) status = profiles.lastError();
        addActionRow(
                "Profile editor",
                "Create, duplicate, rename, bind, and select spell profiles.",
                "Open profiles",
                () -> {
                    profiles.refreshCharacterCatalog();
                    if (minecraft != null) minecraft.setScreen(new SpellProfilePickerScreen(this, profiles, true));
                },
                "profile editor spell binding keys duplicate rename");
        addProfileBooleanRow(
                "Automatic profile switching",
                "Resolve the selected character's assignment and class fallback automatically.",
                profiles,
                "automatic profile switching character fallback");
    }

    private void buildCharacters() {
        SpellProfileService profiles = profilesSupplier.get();
        if (profiles == null) {
            addUnavailableRow("Character assignments", "Wynntils is still starting; reopen settings in a moment.");
            return;
        }
        addActionRow(
                "Character assignments",
                "Scan the class menu and assign a profile to each known character.",
                "Open characters",
                () -> {
                    profiles.refreshCharacterCatalog();
                    if (minecraft != null) minecraft.setScreen(new SpellProfilePickerScreen(this, profiles, false));
                },
                "characters assignments class nickname level scan menu");
    }

    private void buildCompatibility() {
        addActionRow(
                "Observed data status",
                "Inspect live values, provenance, freshness, and integration diagnostics.",
                "Open data status",
                () -> {
                    if (minecraft != null) minecraft.setScreen(new ObservedStateDebugScreen(this, repository));
                },
                "debug data status provenance health diagnostics observations");
        addUnavailableRow(
                "Wynntils compatibility guard",
                "Exact support is locked to Wynntils 4.2.8 for Minecraft 1.21.11.");
    }

    private void addBooleanRow(
            String label,
            String description,
            boolean enabled,
            UnaryOperator<SyllyConfig> toggle,
            UnaryOperator<SyllyConfig> reset,
            String terms) {
        RowLayout layout = nextLayout();
        Button primary = addButton(
                enabled ? "Enabled" : "Disabled",
                layout.controlX(),
                layout.controlY(),
                layout.controlWidth(),
                description,
                () -> updateSettings(toggle, label + " updated."));
        Button resetButton = addButton(
                "Reset", layout.resetX(), layout.controlY(), 48, "Restore this field's default value.",
                () -> updateSettings(reset, label + " reset."));
        rows.add(new SettingRow(label, description, terms, layout.labelY(), primary, resetButton));
    }

    private void addProfileBooleanRow(
            String label, String description, SpellProfileService profiles, String terms) {
        RowLayout layout = nextLayout();
        Button primary = addButton(
                profiles.automaticSwitchingEnabled() ? "Enabled" : "Disabled",
                layout.controlX(),
                layout.controlY(),
                layout.controlWidth(),
                description,
                () -> {
                    profiles.setAutomaticSwitchingEnabled(!profiles.automaticSwitchingEnabled());
                    status = profiles.lastError().isBlank() ? label + " updated." : profiles.lastError();
                    rebuildWidgets();
                });
        Button resetButton = addButton(
                "Reset", layout.resetX(), layout.controlY(), 48, "Restore this field's default value.",
                () -> {
                    profiles.setAutomaticSwitchingEnabled(true);
                    status = label + " reset.";
                    rebuildWidgets();
                });
        rows.add(new SettingRow(label, description, terms, layout.labelY(), primary, resetButton));
    }

    private void addIntegerRow(
            String label,
            String description,
            int value,
            int minimum,
            int maximum,
            IntConfigUpdate update,
            UnaryOperator<SyllyConfig> reset,
            String terms) {
        RowLayout layout = nextLayout();
        EditBox field = new EditBox(
                font,
                layout.controlX(),
                layout.controlY(),
                layout.controlWidth(),
                20,
                Component.literal(label));
        field.setMaxLength(9);
        field.setValue(Integer.toString(value));
        field.setTooltip(Tooltip.create(Component.literal(description)));
        field.setResponder(text -> {
            int parsed;
            try {
                parsed = Integer.parseInt(text);
            } catch (NumberFormatException exception) {
                status = label + " must be a whole number.";
                return;
            }
            if (parsed < minimum || parsed > maximum) {
                status = label + " must be between " + minimum + " and " + maximum + ".";
                return;
            }
            if (settings.update(current -> update.apply(current, parsed))) {
                status = label + " saved.";
            } else {
                status = settings.warning().orElse("Could not save " + label + ".");
            }
        });
        addRenderableWidget(field);
        Button resetButton = addButton(
                "Reset", layout.resetX(), layout.controlY(), 48, "Restore this field's default value.",
                () -> updateSettings(reset, label + " reset."));
        rows.add(new SettingRow(label, description, terms, layout.labelY(), field, resetButton));
    }

    private void addActionRow(
            String label, String description, String buttonLabel, Runnable action, String terms) {
        RowLayout layout = nextLayout();
        Button primary = addButton(
                buttonLabel,
                layout.controlX(),
                layout.controlY(),
                layout.controlWidth() + 52,
                description,
                action);
        rows.add(new SettingRow(label, description, terms, layout.labelY(), primary, null));
    }

    private void addUnavailableRow(String label, String description) {
        RowLayout layout = nextLayout();
        Button primary = addButton(
                "Required",
                layout.controlX(),
                layout.controlY(),
                layout.controlWidth() + 52,
                description,
                () -> {});
        primary.active = false;
        rows.add(new SettingRow(label, description, label + " " + description, layout.labelY(), primary, null));
    }

    private RowLayout nextLayout() {
        int availableWidth = Math.max(120, contentRight - contentX - 24);
        boolean compact = availableWidth < 340;
        int labelY = nextRowY;
        int controlY = compact ? labelY + 26 : labelY - 4;
        int controlX = compact ? contentX + 12 : Math.max(contentX + 180, contentRight - 198);
        int resetX = contentRight - 60;
        int controlWidth = Math.max(56, resetX - controlX - 6);
        nextRowY += compact ? 58 : 44;
        return new RowLayout(labelY, controlX, controlY, controlWidth, resetX);
    }

    private void selectSection(SyllyConfigSection selected) {
        section = selected;
        status = "Settings save immediately.";
        rebuildWidgets();
    }

    private void resetSection() {
        if (section == SyllyConfigSection.PROFILES) {
            SpellProfileService profiles = profilesSupplier.get();
            if (profiles != null) profiles.setAutomaticSwitchingEnabled(true);
            status = "Profiles settings reset.";
        } else if (settings.reset(section)) {
            status = SectionInfo.forSection(section).label() + " reset.";
        } else {
            status = settings.warning().orElse("Could not reset this section.");
        }
        rebuildWidgets();
    }

    private void updateSettings(UnaryOperator<SyllyConfig> change, String successMessage) {
        if (settings.update(change)) {
            status = successMessage;
        } else {
            status = settings.warning().orElse("Could not save settings.");
        }
        rebuildWidgets();
    }

    private void applySearchFilter() {
        String query = searchQuery.strip().toLowerCase(Locale.ROOT);
        for (Map.Entry<SyllyConfigSection, Button> entry : sectionButtons.entrySet()) {
            entry.getValue().visible = query.isEmpty() || SectionInfo.forSection(entry.getKey()).matches(query);
        }
        for (SettingRow row : rows) {
            boolean visible = query.isEmpty() || row.matches(query);
            row.primary().visible = visible;
            if (row.reset() != null) row.reset().visible = visible;
        }
    }

    private String trimToWidth(String value, int maximumWidth) {
        if (font.width(value) <= maximumWidth) return value;
        String suffix = "...";
        return font.plainSubstrByWidth(value, Math.max(1, maximumWidth - font.width(suffix))) + suffix;
    }

    private int statusColor() {
        String lower = status.toLowerCase(Locale.ROOT);
        return lower.contains("must") || lower.contains("could not") || lower.contains("error") ? WARNING : GOOD;
    }

    private static boolean isResettable(SyllyConfigSection section) {
        return section != SyllyConfigSection.CHARACTERS && section != SyllyConfigSection.COMPATIBILITY;
    }

    private Button addButton(
            String label, int x, int y, int width, String tooltip, Runnable action) {
        Button button = Button.builder(Component.literal(label), ignored -> action.run())
                .bounds(x, y, Math.max(20, width), 20)
                .tooltip(Tooltip.create(Component.literal(tooltip)))
                .build();
        return addRenderableWidget(button);
    }

    private static void drawPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, PANEL_BORDER);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, PANEL);
    }

    @FunctionalInterface
    private interface IntConfigUpdate {
        SyllyConfig apply(SyllyConfig config, int value);
    }

    private record SettingRow(
            String label,
            String description,
            String terms,
            int y,
            AbstractWidget primary,
            Button reset) {
        private boolean matches(String query) {
            return (label + " " + description + " " + terms).toLowerCase(Locale.ROOT).contains(query);
        }
    }

    private record RowLayout(int labelY, int controlX, int controlY, int controlWidth, int resetX) {}

    private record SectionInfo(String label, String description, String terms) {
        private boolean matches(String query) {
            return (label + " " + description + " " + terms).toLowerCase(Locale.ROOT).contains(query);
        }

        private static SectionInfo forSection(SyllyConfigSection section) {
            return switch (section) {
                case PROFILES -> new SectionInfo(
                        "Profiles", "Spell layouts and automatic selection.", "profile spell binding keys fallback");
                case CHARACTERS -> new SectionInfo(
                        "Characters", "Character discovery and profile assignments.", "character class nickname level scan assignment");
                case ECO_AUDITOR -> new SectionInfo(
                        "Eco Auditor", "Economic warnings and issue checks.", "eco auditor warning cooldown checks");
                case TERRITORY_IMPACT -> new SectionInfo(
                        "Territory Impact", "Before/after territory consequences.", "territory impact before after");
                case ROUTING_ADVISOR -> new SectionInfo(
                        "Routing Advisor", "Route recommendations and diagnostics.", "routing advisor route recommendation");
                case OPTIMIZER -> new SectionInfo(
                        "Optimizer", "Bounded economy optimization tools.", "optimizer optimization");
                case SNAPSHOTS -> new SectionInfo(
                        "Snapshots", "Historical capture and retention.", "snapshot history retention automatic");
                case NOTIFICATIONS -> new SectionInfo(
                        "Notifications", "Chat messages and warnings.", "notifications profile swap warning chat");
                case COMPATIBILITY -> new SectionInfo(
                        "Compatibility", "Wynntils guard and observed data status.", "compatibility wynntils debug data status");
            };
        }
    }
}
