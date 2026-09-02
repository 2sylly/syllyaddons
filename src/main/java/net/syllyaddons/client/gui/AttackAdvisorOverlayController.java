package net.syllyaddons.client.gui;

import com.wynntils.core.components.Handlers;
import com.wynntils.utils.mc.LoreUtils;
import com.wynntils.utils.wynn.ContainerUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.syllyaddons.advisor.AttackAdvisorService;
import net.syllyaddons.advisor.AttackAdvisorView;
import net.syllyaddons.advisor.AttackButtonDetector;
import net.syllyaddons.advisor.AttackMenuEntry;
import net.syllyaddons.advisor.AttackRouteEstimate;
import net.syllyaddons.config.SyllyConfig;
import net.syllyaddons.config.SyllyConfigService;
import net.syllyaddons.mixin.AbstractContainerScreenAccessor;
import org.lwjgl.glfw.GLFW;

/** Attack-screen-only advice plus an optional explicit confirmation guard. */
public final class AttackAdvisorOverlayController {
    private static final AttackButtonDetector ATTACK_BUTTON = new AttackButtonDetector();
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
            ConfirmationState confirmation = new ConfirmationState();
            ScreenEvents.afterRender(screen).register((ignored, graphics, mouseX, mouseY, tickDelta) ->
                    render(screen, graphics, confirmation));
            ScreenMouseEvents.allowMouseClick(screen).register((ignored, event) ->
                    allowMouseClick(screen, event, confirmation));
        });
    }

    private static void render(Screen screen, GuiGraphics graphics, ConfirmationState confirmation) {
        AttackAdvisorView view = currentView(screen);
        if (view == null) {
            confirmation.clear();
            return;
        }
        drawPanel(graphics, view);
        if (confirmation.visible()) drawConfirmation(graphics, confirmation);
    }

    private static boolean allowMouseClick(
            Screen screen,
            MouseButtonEvent event,
            ConfirmationState confirmation) {
        AttackAdvisorView view = currentView(screen);
        if (view == null) {
            confirmation.clear();
            return true;
        }

        if (confirmation.visible()) {
            if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                openHqManagement(confirmation.headquarters());
                confirmation.clear();
            } else if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && event.hasShiftDown()) {
                confirmAttack(screen, confirmation);
                confirmation.clear();
            }
            return false;
        }

        Bounds panel = panelBounds(view);
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT
                && view.advice().routingObservationNeeded()
                && !view.advice().headquarters().isBlank()
                && panel.contains(event.x(), event.y())) {
            openHqManagement(view.advice().headquarters());
            return false;
        }

        SyllyConfig config = settingsSupplier.get().snapshot();
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT
                || !config.routingAdvisor().blockAttackWhenFastestIsFaster()
                || !view.advice().available()
                || view.advice().timeSavedSeconds() <= 0) {
            return true;
        }

        AttackSlot attack = attackSlot(screen, event.x(), event.y());
        if (attack == null) return true;
        confirmation.open(
                attack.slotIndex(),
                attack.containerId(),
                view.advice().headquarters(),
                view.advice().timeSavedSeconds());
        return false;
    }

    private static AttackAdvisorView currentView(Screen screen) {
        AttackAdvisorService service = serviceSupplier.get();
        SyllyConfigService settings = settingsSupplier.get();
        if (service == null || settings == null || !settings.snapshot().routingAdvisorEnabled()) return null;
        AttackAdvisorView view = service.latest().orElse(null);
        if (view == null || !screenTarget(screen).equalsIgnoreCase(view.menu().target())) return null;
        return view;
    }

    private static AttackSlot attackSlot(Screen screen, double mouseX, double mouseY) {
        if (!(screen instanceof AbstractContainerScreen<?> container)
                || !(screen instanceof AbstractContainerScreenAccessor position)) {
            return null;
        }
        int left = position.syllyaddons$getLeftPos();
        int top = position.syllyaddons$getTopPos();
        List<Slot> slots = container.getMenu().slots;
        for (int index = 0; index < slots.size(); index++) {
            Slot slot = slots.get(index);
            if (!slot.isActive()
                    || mouseX < left + slot.x
                    || mouseX >= left + slot.x + 16
                    || mouseY < top + slot.y
                    || mouseY >= top + slot.y + 16) {
                continue;
            }
            ItemStack item = slot.getItem();
            if (!item.isEmpty() && ATTACK_BUTTON.matches(entry(item))) {
                return new AttackSlot(index, container.getMenu().containerId);
            }
        }
        return null;
    }

    private static void confirmAttack(Screen screen, ConfirmationState confirmation) {
        if (!(screen instanceof AbstractContainerScreen<?> container)) return;
        if (container.getMenu().containerId != confirmation.containerId()) return;
        int slotIndex = confirmation.slotIndex();
        if (slotIndex < 0 || slotIndex >= container.getMenu().slots.size()) return;
        ItemStack item = container.getMenu().slots.get(slotIndex).getItem();
        if (item.isEmpty() || !ATTACK_BUTTON.matches(entry(item))) return;
        ContainerUtils.clickOnSlot(
                slotIndex,
                container.getMenu().containerId,
                GLFW.GLFW_MOUSE_BUTTON_LEFT,
                container.getMenu().getItems());
    }

    private static AttackMenuEntry entry(ItemStack item) {
        List<String> tooltip = LoreUtils.getTooltipLines(item).stream()
                .map(component -> component.getString().strip())
                .filter(line -> !line.isEmpty())
                .toList();
        return new AttackMenuEntry(item.getHoverName().getString(), tooltip);
    }

    private static void openHqManagement(String headquarters) {
        if (!headquarters.isBlank()) Handlers.Command.sendCommandImmediately("gu territory " + headquarters);
    }

    private static void drawPanel(GuiGraphics graphics, AttackAdvisorView view) {
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        Bounds bounds = panelBounds(view);
        List<Line> lines = lines(view);
        graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), 0xE5101622);
        graphics.fill(bounds.x(), bounds.y(), bounds.x() + 3, bounds.bottom(), 0xFF79A9F5);
        graphics.fill(bounds.x() + 3, bounds.y(), bounds.right(), bounds.y() + 1, 0xFF344158);
        int textY = bounds.y() + 6;
        for (Line line : lines) {
            graphics.drawString(
                    font,
                    trim(font, line.text(), bounds.width() - 16),
                    bounds.x() + 9,
                    textY,
                    line.color(),
                    false);
            textY += 11;
        }
    }

    private static Bounds panelBounds(AttackAdvisorView view) {
        Minecraft minecraft = Minecraft.getInstance();
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        int width = Math.min(316, Math.max(190, screenWidth - 16));
        int height = 15 + lines(view).size() * 11;
        int x = Math.max(8, screenWidth - width - 8);
        int y = Math.min(8, Math.max(0, screenHeight - height));
        return new Bounds(x, y, width, height);
    }

    private static List<Line> lines(AttackAdvisorView view) {
        List<Line> lines = new ArrayList<>();
        var advice = view.advice();
        lines.add(new Line("SyllyAddons · Attack Routing · " + advice.target(), 0xFFF1F4FA));
        lines.add(new Line(advice.decision().label(), advice.available() ? 0xFF9DDEB2 : 0xFFFFD166));
        if (advice.available()) {
            lines.add(new Line(estimate("Fastest", advice.fastest()), 0xFFE2E8F3));
            lines.add(new Line(estimate("Cheapest", advice.cheapest()), 0xFFE2E8F3));
            lines.add(new Line("Fastest saves " + duration(advice.timeSavedSeconds()), 0xFFB9C8DF));
            String modeSource = advice.routingModeInferred() ? "inferred from displayed timer/route" : "observed";
            lines.add(new Line("Current mode = " + displayMode(advice) + " (" + modeSource + ")", 0xFF7F8BA3));
        } else if (!advice.diagnostics().isEmpty()) {
            lines.add(new Line(advice.diagnostics().getLast(), 0xFFFFD166));
            if (advice.routingObservationNeeded() && !advice.headquarters().isBlank()) {
                lines.add(new Line("Right-click this panel · Open HQ management", 0xFF9FC2FF));
            }
        }
        lines.add(new Line("Shown only while this attack screen is open", 0xFF7F8BA3));
        return lines;
    }

    private static String displayMode(net.syllyaddons.advisor.AttackRoutingAdvice advice) {
        if (advice.resolvedRoutingMode() == null) return "unknown";
        String lower = advice.resolvedRoutingMode().name().toLowerCase();
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static void drawConfirmation(GuiGraphics graphics, ConfirmationState confirmation) {
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        int width = Math.min(420, Math.max(220, screenWidth - 24));
        int contentWidth = width - 24;
        String message = "Are you sure? Setting your HQ routing to fastest would save "
                + durationWords(confirmation.timeSavedSeconds()) + " on this queue.";
        List<FormattedCharSequence> wrapped = font.split(Component.literal(message), contentWidth);
        int height = 48 + wrapped.size() * 11;
        int x = Math.max(6, (screenWidth - width) / 2);
        int y = Math.max(6, (screenHeight - height) / 2);

        graphics.fill(0, 0, screenWidth, screenHeight, 0x88000000);
        graphics.fill(x, y, x + width, y + height, 0xFA121A28);
        graphics.fill(x, y, x + width, y + 2, 0xFFFFB347);
        graphics.fill(x, y + height - 1, x + width, y + height, 0xFF4B5C78);
        graphics.drawString(font, "Faster queue available", x + 12, y + 9, 0xFFFFD38A, false);
        int textY = y + 23;
        for (FormattedCharSequence line : wrapped) {
            graphics.drawString(font, line, x + 12, textY, 0xFFF1F4FA, false);
            textY += 11;
        }
        graphics.drawString(font, "Right-click · Open HQ management menu", x + 12, textY + 2, 0xFF9FC2FF, false);
        graphics.drawString(font, "Shift-left-click · Attack", x + 12, textY + 13, 0xFFFFC66D, false);
    }

    private static String estimate(String label, AttackRouteEstimate estimate) {
        String timer = duration(estimate.comparisonTimerSeconds());
        String observed = estimate.observedTimerSeconds().isPresent() ? "observed" : "estimated";
        return label + ": " + timer + " · " + observed
                + " · " + estimate.route().steps().size() + " hops";
    }

    private static String duration(int seconds) {
        return seconds / 60 + "m " + seconds % 60 + "s";
    }

    static String durationWords(int seconds) {
        int minutes = seconds / 60;
        int remaining = seconds % 60;
        List<String> parts = new ArrayList<>();
        if (minutes > 0) parts.add(minutes + (minutes == 1 ? " minute" : " minutes"));
        if (remaining > 0 || parts.isEmpty()) parts.add(remaining + (remaining == 1 ? " second" : " seconds"));
        return String.join(" ", parts);
    }

    private static boolean isAttackScreen(Screen screen) {
        return !screenTarget(screen).isBlank();
    }

    private static String screenTarget(Screen screen) {
        if (screen == null) return "";
        String title = screen.getTitle().getString();
        return title.startsWith("Attacking: ") ? title.substring("Attacking: ".length()).strip() : "";
    }

    private static String trim(Font font, String value, int width) {
        if (font.width(value) <= width) return value;
        return font.plainSubstrByWidth(value, Math.max(1, width - font.width("..."))) + "...";
    }

    private record AttackSlot(int slotIndex, int containerId) {}

    private record Bounds(int x, int y, int width, int height) {
        private int right() {
            return x + width;
        }

        private int bottom() {
            return y + height;
        }

        private boolean contains(double pointX, double pointY) {
            return pointX >= x && pointX < right() && pointY >= y && pointY < bottom();
        }
    }

    private static final class ConfirmationState {
        private int slotIndex = -1;
        private int containerId = -1;
        private String headquarters = "";
        private int timeSavedSeconds;

        private boolean visible() {
            return slotIndex >= 0;
        }

        private int slotIndex() {
            return slotIndex;
        }

        private int containerId() {
            return containerId;
        }

        private String headquarters() {
            return headquarters;
        }

        private int timeSavedSeconds() {
            return timeSavedSeconds;
        }

        private void open(int nextSlotIndex, int nextContainerId, String nextHeadquarters, int nextTimeSavedSeconds) {
            slotIndex = nextSlotIndex;
            containerId = nextContainerId;
            headquarters = nextHeadquarters;
            timeSavedSeconds = nextTimeSavedSeconds;
        }

        private void clear() {
            slotIndex = -1;
            containerId = -1;
            headquarters = "";
            timeSavedSeconds = 0;
        }
    }

    private record Line(String text, int color) {}
}
