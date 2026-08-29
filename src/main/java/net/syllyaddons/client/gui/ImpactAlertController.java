package net.syllyaddons.client.gui;

import com.wynntils.utils.mc.McUtils;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.sounds.SoundEvents;
import net.syllyaddons.config.ImpactAlertSize;
import net.syllyaddons.config.SyllyConfig;
import net.syllyaddons.config.SyllyConfigService;
import net.syllyaddons.domain.ObservedState;
import net.syllyaddons.impact.ImpactLossAlert;
import net.syllyaddons.impact.ImpactLossAlertMatcher;
import net.syllyaddons.impact.ImpactSeverity;
import net.syllyaddons.impact.OwnershipTransitionDetector;
import net.syllyaddons.impact.TerritoryImpactCache;
import net.syllyaddons.observation.ObservedStateRepository;

/** Refresh-aware HUD alerts backed by an exact pre-loss impact cache entry. */
public final class ImpactAlertController {
    private static final int MAX_QUEUED_ALERTS = 4;
    private static ImpactAlertController instance;

    private final TerritoryImpactCache cache;
    private final SyllyConfigService settings;
    private final ImpactLossAlertMatcher matcher = new ImpactLossAlertMatcher();
    private final Deque<ImpactLossAlert> alerts = new ArrayDeque<>();
    private ObservedState previous;

    private ImpactAlertController(
            ObservedStateRepository repository,
            TerritoryImpactCache cache,
            SyllyConfigService settings) {
        this.cache = Objects.requireNonNull(cache, "cache");
        this.settings = Objects.requireNonNull(settings, "settings");
        previous = Objects.requireNonNull(repository, "repository").snapshot();
        repository.addListener(this::onState);
    }

    public static synchronized void register(
            ObservedStateRepository repository,
            TerritoryImpactCache cache,
            SyllyConfigService settings) {
        if (instance != null) return;
        instance = new ImpactAlertController(repository, cache, settings);
        HudRenderCallback.EVENT.register((graphics, tickCounter) -> instance.render(graphics));
    }

    private synchronized void onState(ObservedState current) {
        ObservedState before = previous;
        previous = current;
        if (!OwnershipTransitionDetector.sameSession(before, current)) {
            alerts.clear();
            return;
        }
        SyllyConfig config = settings.snapshot();
        List<ImpactLossAlert> matched = matcher.match(
                before, current, cache.view(), config, System.currentTimeMillis());
        if (matched.isEmpty()) return;
        for (ImpactLossAlert alert : matched) {
            alerts.addLast(alert);
            while (alerts.size() > MAX_QUEUED_ALERTS) alerts.removeFirst();
        }
        if (config.impactAlertSound()) {
            Minecraft.getInstance().execute(() -> McUtils.playSoundUI(SoundEvents.NOTE_BLOCK_PLING.value()));
        }
    }

    private synchronized void render(GuiGraphics graphics) {
        long now = System.currentTimeMillis();
        alerts.removeIf(alert -> alert.expired(now));
        if (alerts.isEmpty()) return;
        SyllyConfig config = settings.snapshot();
        List<ImpactLossAlert> visible = new ArrayList<>(alerts);
        int y = 32;
        for (int index = visible.size() - 1; index >= 0; index--) {
            y += renderAlert(graphics, visible.get(index), config.impactAlertSize(), y) + 5;
        }
    }

    private static int renderAlert(
            GuiGraphics graphics,
            ImpactLossAlert alert,
            ImpactAlertSize size,
            int y) {
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int width = switch (size) {
            case SMALL -> 218;
            case MEDIUM -> 292;
            case LARGE -> 360;
        };
        int height = switch (size) {
            case SMALL -> 34;
            case MEDIUM -> 48;
            case LARGE -> 61;
        };
        width = Math.min(width, Math.max(140, screenWidth - 16));
        int x = (screenWidth - width) / 2;
        int color = severityColor(alert.severity());
        graphics.fill(x, y, x + width, y + height, 0xE5101622);
        graphics.fill(x, y, x + 4, y + height, color);
        graphics.fill(x + 4, y, x + width, y + 1, color);
        graphics.drawString(font, trim(font, alert.severity() + " · " + alert.territory() + " lost", width - 18),
                x + 10, y + 7, color, false);
        graphics.drawString(font,
                trim(font, "Pre-loss baseline " + duration(alert.baselineAgeMillis()) + " old · rev "
                        + alert.baselineRevision(), width - 18),
                x + 10, y + 19, 0xFFCFD7E6, false);
        if (size != ImpactAlertSize.SMALL) {
            graphics.drawString(font,
                    trim(font, "Captured by " + alert.capturedBy() + " · observed refresh window ≤ "
                            + duration(alert.refreshWindowMillis()), width - 18),
                    x + 10, y + 31, 0xFFA5B1C7, false);
        }
        if (size == ImpactAlertSize.LARGE) {
            graphics.drawString(font,
                    "Advisory snapshot—not the exact loss time.", x + 10, y + 43, 0xFF7F8BA3, false);
        }
        return height;
    }

    private static String duration(long millis) {
        if (millis < 1_000) return "<1s";
        long seconds = millis / 1_000;
        if (seconds < 60) return seconds + "s";
        return seconds / 60 + "m " + seconds % 60 + "s";
    }

    private static int severityColor(ImpactSeverity severity) {
        return switch (severity) {
            case MINOR -> 0xFFFFE06A;
            case WARNING -> 0xFFFFA64D;
            case CRITICAL -> 0xFFFF5C70;
            case CATASTROPHIC -> 0xFFD49BFF;
        };
    }

    private static String trim(Font font, String value, int width) {
        if (font.width(value) <= width) return value;
        return font.plainSubstrByWidth(value, Math.max(1, width - font.width("..."))) + "...";
    }
}
