package net.syllyaddons.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.UnaryOperator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.syllyaddons.config.SyllyConfig;
import net.syllyaddons.config.SyllyConfigSection;
import net.syllyaddons.config.SyllyConfigService;
import net.syllyaddons.impact.ImpactSeverity;

/** Compact persisted controls for Track 8 map filters and loss alerts. */
public final class ImpactDisplaySettingsScreen extends Screen {
    private static final int BACKGROUND = 0xF00E1420;
    private static final int HEADER = 0xFF172131;
    private static final int TEXT = 0xFFF1F4FA;
    private static final int MUTED = 0xFFA5B1C7;
    private static final int GOOD = 0xFF9DDEB2;
    private static final int WARNING = 0xFFFFD166;

    private final Screen parent;
    private final SyllyConfigService settings;
    private final List<Row> rows = new ArrayList<>();
    private String status = "Changes save immediately.";

    public ImpactDisplaySettingsScreen(Screen parent, SyllyConfigService settings) {
        super(Component.literal("Territory overlays & alerts"));
        this.parent = parent;
        this.settings = settings;
    }

    @Override
    protected void init() {
        rows.clear();
        addButton("Back", 10, 14, 48, "Return to Territory Impact settings.", this::onClose);
        addButton("Reset all", width - 78, 14, 68, "Restore all Track 8 display defaults.", () -> {
            if (settings.reset(SyllyConfigSection.TERRITORY_IMPACT)) status = "Track 8 display settings reset.";
            else status = settings.warning().orElse("Could not reset settings.");
            rebuildWidgets();
        });
        SyllyConfig config = settings.snapshot();
        int y = 52;
        addChoice("Map colouring", "Colour territory regions from the completed impact cache.", y,
                config.impactOverlayEnabled() ? "Enabled" : "Disabled",
                current -> current.withImpactOverlayEnabled(!current.impactOverlayEnabled()));
        y += 31;
        addChoice("Guild filter", "Own guild, a named enemy, or all visible guilds.", y,
                config.impactOverlayScope().label(),
                current -> current.withImpactOverlayScope(current.impactOverlayScope().next()));
        y += 31;
        addText("Selected enemy", "Exact guild name, tag, or UUID used by Selected enemy.", y,
                config.impactSelectedEnemy(), SyllyConfig::withImpactSelectedEnemy);
        y += 31;
        addChoice("Disconnections", "Hide impacts that do not disconnect at least one route.", y,
                config.impactDisconnectionsOnly() ? "Only" : "Any impact",
                current -> current.withImpactDisconnectionsOnly(!current.impactDisconnectionsOnly()));
        y += 31;
        addChoice("Resource filter", "Require a non-zero delta for the chosen resource.", y,
                config.impactResourceFilter().label(),
                current -> current.withImpactResourceFilter(current.impactResourceFilter().next()));
        y += 31;
        addInteger("Minimum delay", "Minimum positive route delay in seconds (0-3600).", y,
                config.impactMinimumDelaySeconds(), SyllyConfig.MIN_IMPACT_DELAY_SECONDS,
                SyllyConfig.MAX_IMPACT_DELAY_SECONDS, SyllyConfig::withImpactMinimumDelaySeconds);
        y += 31;
        addChoice("Alert size", "Amount of detail shown by territory-loss alerts.", y,
                config.impactAlertSize().label(),
                current -> current.withImpactAlertSize(current.impactAlertSize().next()));
        y += 31;
        addInteger("Alert duration", "Seconds each alert remains visible (2-30).", y,
                config.impactAlertDurationSeconds(), SyllyConfig.MIN_IMPACT_ALERT_DURATION_SECONDS,
                SyllyConfig.MAX_IMPACT_ALERT_DURATION_SECONDS, SyllyConfig::withImpactAlertDurationSeconds);
        y += 31;
        addChoice("Alert sound", "Play one UI note when a matched loss alert appears.", y,
                config.impactAlertSound() ? "Enabled" : "Muted",
                current -> current.withImpactAlertSound(!current.impactAlertSound()));
        y += 31;
        addChoice("Minimum severity", "Suppress loss alerts below this pre-loss impact score.", y,
                pretty(config.impactAlertMinimumSeverity()),
                current -> current.withImpactAlertMinimumSeverity(next(current.impactAlertMinimumSeverity())));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, BACKGROUND);
        graphics.fill(0, 0, width, 44, HEADER);
        graphics.drawString(font, title, 68, 20, TEXT, false);
        for (Row row : rows) {
            graphics.drawString(font, row.label(), 16, row.y(), TEXT, false);
            graphics.drawString(font, trim(row.description(), Math.max(80, width - 190)), 16, row.y() + 11, MUTED, false);
        }
        graphics.drawString(font, trim(status, width - 20), 10, height - 14,
                status.toLowerCase(Locale.ROOT).contains("could not") ? WARNING : GOOD, false);
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

    private void addChoice(
            String label, String description, int y, String value, UnaryOperator<SyllyConfig> update) {
        int controlWidth = Math.min(150, Math.max(90, width / 3));
        Button button = addButton(value, width - controlWidth - 14, y - 4, controlWidth, description, () -> {
            save(update, label + " updated.");
            rebuildWidgets();
        });
        rows.add(new Row(label, description, y, button));
    }

    private void addText(
            String label, String description, int y, String value, TextConfigUpdate update) {
        int controlWidth = Math.min(150, Math.max(90, width / 3));
        EditBox field = new EditBox(font, width - controlWidth - 14, y - 4, controlWidth, 20, Component.literal(label));
        field.setMaxLength(64);
        field.setValue(value);
        field.setHint(Component.literal("Guild name / tag"));
        field.setTooltip(Tooltip.create(Component.literal(description)));
        field.setResponder(text -> save(current -> update.apply(current, text), label + " saved."));
        addRenderableWidget(field);
        rows.add(new Row(label, description, y, field));
    }

    private void addInteger(
            String label,
            String description,
            int y,
            int value,
            int minimum,
            int maximum,
            IntConfigUpdate update) {
        int controlWidth = Math.min(150, Math.max(90, width / 3));
        EditBox field = new EditBox(font, width - controlWidth - 14, y - 4, controlWidth, 20, Component.literal(label));
        field.setMaxLength(4);
        field.setValue(Integer.toString(value));
        field.setTooltip(Tooltip.create(Component.literal(description)));
        field.setResponder(text -> {
            try {
                int parsed = Integer.parseInt(text);
                if (parsed < minimum || parsed > maximum) {
                    status = label + " must be between " + minimum + " and " + maximum + ".";
                    return;
                }
                save(current -> update.apply(current, parsed), label + " saved.");
            } catch (NumberFormatException exception) {
                status = label + " must be a whole number.";
            }
        });
        addRenderableWidget(field);
        rows.add(new Row(label, description, y, field));
    }

    private void save(UnaryOperator<SyllyConfig> update, String success) {
        if (settings.update(update)) status = success;
        else status = settings.warning().orElse("Could not save settings.");
    }

    private Button addButton(String label, int x, int y, int width, String tooltip, Runnable action) {
        return addRenderableWidget(Button.builder(Component.literal(label), ignored -> action.run())
                .bounds(x, y, width, 20)
                .tooltip(Tooltip.create(Component.literal(tooltip)))
                .build());
    }

    private String trim(String value, int width) {
        if (font.width(value) <= width) return value;
        return font.plainSubstrByWidth(value, Math.max(1, width - font.width("..."))) + "...";
    }

    private static ImpactSeverity next(ImpactSeverity value) {
        ImpactSeverity[] values = ImpactSeverity.values();
        return values[(value.ordinal() + 1) % values.length];
    }

    private static String pretty(ImpactSeverity severity) {
        String lower = severity.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    @FunctionalInterface
    private interface TextConfigUpdate {
        SyllyConfig apply(SyllyConfig config, String value);
    }

    @FunctionalInterface
    private interface IntConfigUpdate {
        SyllyConfig apply(SyllyConfig config, int value);
    }

    private record Row(String label, String description, int y, net.minecraft.client.gui.components.AbstractWidget control) {}
}
