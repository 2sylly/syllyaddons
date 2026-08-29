package net.syllyaddons.compat.wynntils.v4_2_8;

import com.wynntils.mc.event.ContainerSetContentEvent;
import com.wynntils.mc.event.ContainerSetSlotEvent;
import com.wynntils.models.territories.event.GuildWarQueuedEvent;
import com.wynntils.models.worlds.event.WorldStateEvent;
import com.wynntils.models.worlds.type.WorldState;
import com.wynntils.utils.mc.LoreUtils;
import com.wynntils.utils.mc.McUtils;
import java.util.List;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.syllyaddons.advisor.AttackAdvisorService;
import net.syllyaddons.advisor.AttackMenuEntry;
import org.slf4j.Logger;

/** Passive Track 9 bridge. This class does not expose click, command, or packet methods. */
public final class WynntilsAttackAdvisorListener {
    private final AttackAdvisorService service;
    private final Logger logger;

    public WynntilsAttackAdvisorListener(AttackAdvisorService service, Logger logger) {
        this.service = java.util.Objects.requireNonNull(service, "service");
        this.logger = java.util.Objects.requireNonNull(logger, "logger");
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onContainerContent(ContainerSetContentEvent.Post event) {
        captureOpenAttackMenu();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onContainerSlot(ContainerSetSlotEvent.Post event) {
        captureOpenAttackMenu();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onWarQueued(GuildWarQueuedEvent event) {
        try {
            var timer = event.getAttackTimer();
            service.observeQueued(timer.territoryName(), timer.asSeconds(), System.currentTimeMillis());
        } catch (RuntimeException exception) {
            logger.warn("Failed to validate the displayed attack timer", exception);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onWorldStateChanged(WorldStateEvent event) {
        if (event.getOldState() == WorldState.WORLD && event.getNewState() != WorldState.WORLD) service.clear();
    }

    private void captureOpenAttackMenu() {
        try {
            Screen screen = McUtils.screen();
            if (screen == null || !screen.getTitle().getString().startsWith("Attacking: ")) return;
            if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) return;
            List<AttackMenuEntry> entries = containerScreen.getMenu().getItems().stream()
                    .filter(item -> !item.isEmpty())
                    .map(WynntilsAttackAdvisorListener::entry)
                    .toList();
            service.observeMenu(screen.getTitle().getString(), entries, System.currentTimeMillis());
        } catch (RuntimeException exception) {
            logger.warn("Failed to passively read the attack menu", exception);
        }
    }

    private static AttackMenuEntry entry(ItemStack item) {
        List<String> tooltip = LoreUtils.getTooltipLines(item).stream()
                .map(component -> component.getString().strip())
                .filter(line -> !line.isEmpty())
                .toList();
        return new AttackMenuEntry(item.getHoverName().getString(), tooltip);
    }
}
