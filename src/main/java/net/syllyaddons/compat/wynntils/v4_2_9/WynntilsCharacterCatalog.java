package net.syllyaddons.compat.wynntils.v4_2_9;

import com.wynntils.core.components.Models;
import com.wynntils.mc.event.ContainerClickEvent;
import com.wynntils.mc.event.ContainerSetContentEvent;
import com.wynntils.models.character.event.CharacterUpdateEvent;
import com.wynntils.models.containers.containers.CharacterSelectionContainer;
import com.wynntils.models.items.items.gui.CharacterItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.syllyaddons.profile.CharacterCatalogProvider;
import net.syllyaddons.profile.KnownCharacter;
import net.syllyaddons.profile.SpellProfileService;
import org.lwjgl.glfw.GLFW;

public final class WynntilsCharacterCatalog implements CharacterCatalogProvider {
    private static final List<Integer> CHARACTER_SLOTS =
            List.of(9, 10, 11, 18, 19, 20, 27, 28, 29, 36, 37, 38, 45, 46, 47);

    private volatile List<KnownCharacter> lastScan = List.of();
    private volatile int pendingSlot = -1;
    private SpellProfileService profiles;

    public void attach(SpellProfileService profiles) {
        this.profiles = java.util.Objects.requireNonNull(profiles, "profiles");
    }

    @Override
    public Optional<List<KnownCharacter>> currentCharacters() {
        List<KnownCharacter> snapshot = lastScan;
        return snapshot.isEmpty() ? Optional.empty() : Optional.of(snapshot);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onContainerContent(ContainerSetContentEvent.Post event) {
        if (!(Models.Container.getCurrentContainer() instanceof CharacterSelectionContainer)) return;

        List<KnownCharacter> scanned = new ArrayList<>();
        for (int slot : CHARACTER_SLOTS) {
            if (slot >= event.getItems().size()) break;
            Optional<CharacterItem> item = Models.Item.asWynnItem(event.getItems().get(slot), CharacterItem.class);
            if (item.isEmpty()) break;
            CharacterItem character = item.get();
            String cardName = character.getClassName();
            String actualClassName = character.getClassType().getActualName(character.isReskinned());
            String nickname = cardName.equalsIgnoreCase(actualClassName)
                            || cardName.equalsIgnoreCase(character.getClassType().getName())
                    ? null
                    : cardName;
            scanned.add(new KnownCharacter(
                    provisionalId(slot),
                    character.getClassType().name(),
                    nickname,
                    character.getLevel()));
        }
        if (scanned.isEmpty()) return;

        lastScan = List.copyOf(scanned);
        SpellProfileService service = profiles;
        if (service != null) service.refreshCharacterCatalog();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onContainerClick(ContainerClickEvent event) {
        if (!(Models.Container.getCurrentContainer() instanceof CharacterSelectionContainer)) return;
        if (event.getMouseButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;
        int slot = event.getSlotNum();
        if (lastScan.stream().anyMatch(character -> character.id().equals(provisionalId(slot)))) {
            pendingSlot = slot;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onCharacterUpdate(CharacterUpdateEvent event) {
        int selectedSlot = pendingSlot;
        if (selectedSlot < 0 || !Models.Character.hasCharacter()) return;
        String stableId = Models.Character.getId();
        if (stableId == null || !stableId.matches("[a-z0-9]{8}")) return;

        pendingSlot = -1;
        SpellProfileService service = profiles;
        if (service != null) service.linkCatalogCharacter(provisionalId(selectedSlot), stableId);
    }

    private static String provisionalId(int slot) {
        return "slot:" + slot;
    }
}
