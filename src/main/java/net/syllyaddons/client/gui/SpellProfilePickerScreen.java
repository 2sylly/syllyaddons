package net.syllyaddons.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.syllyaddons.client.profile.InputDisplayName;
import net.syllyaddons.client.profile.KeyConflictDetector;
import net.syllyaddons.profile.InputDevice;
import net.syllyaddons.profile.KnownCharacter;
import net.syllyaddons.profile.ManualSelectionMode;
import net.syllyaddons.profile.PhysicalInput;
import net.syllyaddons.profile.ProfileResolution;
import net.syllyaddons.profile.SpellProfile;
import net.syllyaddons.profile.SpellProfileService;
import org.lwjgl.glfw.GLFW;

public final class SpellProfilePickerScreen extends Screen {
    private static final int BACKGROUND = 0xF00E1420;
    private static final int HEADER = 0xFF172131;
    private static final int PANEL = 0xFF171F2D;
    private static final int PANEL_BORDER = 0xFF344158;
    private static final int ROW = 0xFF182231;
    private static final int ROW_CURRENT = 0xFF20314B;
    private static final int ACCENT = 0xFF70A8FF;
    private static final int TEXT = 0xFFF1F4FA;
    private static final int MUTED = 0xFFA5B1C7;
    private static final int GOOD = 0xFF9DDEB2;
    private static final int WARNING = 0xFFFFD166;
    private static final String[] SPELL_DESCRIPTIONS = {
        "", "First Wynncraft spell", "Second Wynncraft spell", "Third Wynncraft spell", "Fourth Wynncraft spell"
    };

    private final Screen parent;
    private final SpellProfileService profiles;
    private Tab tab;
    private String selectedProfileId;
    private SpellProfile draft;
    private EditBox nameBox;
    private int capturingSpell;
    private int characterScrollRow;
    private int profilePage;
    private boolean draggingCharacterScrollbar;
    private boolean dirty;
    private String status = "";

    public SpellProfilePickerScreen(SpellProfileService profiles) {
        this(null, profiles, Tab.CHARACTERS, profiles.activeResolution().resolved()
                ? profiles.activeResolution().profile().id()
                : null);
    }

    SpellProfilePickerScreen(SpellProfileService profiles, String selectedProfileId) {
        this(null, profiles, Tab.CHARACTERS, selectedProfileId);
    }

    public SpellProfilePickerScreen(Screen parent, SpellProfileService profiles, boolean openProfilesTab) {
        this(parent, profiles, openProfilesTab ? Tab.PROFILES : Tab.CHARACTERS, profiles.activeResolution().resolved()
                ? profiles.activeResolution().profile().id()
                : null);
    }

    private SpellProfilePickerScreen(
            Screen parent, SpellProfileService profiles, Tab initialTab, String selectedProfileId) {
        super(Component.translatable("screen.syllyaddons.spell_profiles"));
        this.parent = parent;
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.tab = Objects.requireNonNull(initialTab, "initialTab");
        this.selectedProfileId = selectedProfileId;
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    protected void init() {
        ensureSelection();
        addTabButtons();
        if (tab == Tab.CHARACTERS) {
            initCharactersTab();
        } else {
            initProfilesTab();
        }
    }

    private void addTabButtons() {
        addButton("Characters", 18, 17, 104, () -> switchTab(Tab.CHARACTERS));
        addButton("Profiles", 126, 17, 104, () -> switchTab(Tab.PROFILES));
        addButton("Done", width - 70, 17, 52, this::onClose);
    }

    private void initCharactersTab() {
        int contentX = 12;
        int contentWidth = width - 24;
        int rowsTop = 92;
        int rowsVisible = visibleCharacterRows(rowsTop);

        addButton(
                profiles.automaticSwitchingEnabled() ? "[x] Enabled" : "[ ] Disabled",
                width - 132,
                55,
                114,
                () -> {
                    profiles.setAutomaticSwitchingEnabled(!profiles.automaticSwitchingEnabled());
                    status = profiles.automaticSwitchingEnabled()
                            ? "Automatic switching enabled."
                            : "Automatic switching paused; the active profile is kept.";
                    rebuildWidgets();
                });

        List<KnownCharacter> characters = profiles.knownCharacters();
        int maxScroll = Math.max(0, characters.size() - rowsVisible);
        characterScrollRow = Math.clamp(characterScrollRow, 0, maxScroll);
        int start = characterScrollRow;
        int end = Math.min(start + rowsVisible, characters.size());
        boolean scrollable = maxScroll > 0;
        int listWidth = contentWidth - (scrollable ? 8 : 0);
        int assignmentX = contentX + Math.max(210, contentWidth / 2);
        int assignmentWidth = contentX + listWidth - assignmentX - 10;

        for (int index = start; index < end; index++) {
            KnownCharacter character = characters.get(index);
            int rowY = rowsTop + (index - start) * 39;
            addRenderableWidget(createAssignmentButton(
                    character, assignmentX, rowY + 9, assignmentWidth));
        }

    }

    private void initProfilesTab() {
        Layout layout = profileLayout();
        List<SpellProfile> available = profiles.profiles();
        int rowsVisible = Math.max(1, (layout.bottom() - layout.top() - 66) / 24);
        int pageCount = pageCount(available.size(), rowsVisible);
        profilePage = Math.min(profilePage, pageCount - 1);
        int start = profilePage * rowsVisible;
        int end = Math.min(start + rowsVisible, available.size());

        addButton("+ New", layout.leftX() + layout.leftWidth() - 60, layout.top() + 8, 52, () -> {
            SpellProfile created = profiles.createProfile();
            selectProfile(created.id());
            status = "New profile created. Add bindings, then save it.";
        });

        for (int index = start; index < end; index++) {
            SpellProfile profile = available.get(index);
            String marker = profile.id().equals(selectedProfileId) ? "> " : "  ";
            addButton(
                    marker + profile.name(),
                    layout.leftX() + 8,
                    layout.top() + 36 + (index - start) * 24,
                    layout.leftWidth() - 16,
                    () -> selectProfile(profile.id()));
        }

        if (pageCount > 1) {
            addButton("<", layout.leftX() + 8, layout.bottom() - 27, 30, () -> {
                profilePage = Math.floorMod(profilePage - 1, pageCount);
                rebuildWidgets();
            });
            addButton(">", layout.leftX() + 42, layout.bottom() - 27, 30, () -> {
                profilePage = Math.floorMod(profilePage + 1, pageCount);
                rebuildWidgets();
            });
        }

        if (draft == null) return;

        int right = layout.rightX();
        int rightWidth = layout.rightWidth();
        nameBox = new EditBox(
                font,
                right + 10,
                layout.top() + 8,
                Math.max(100, rightWidth - 112),
                20,
                Component.literal("Profile name"));
        nameBox.setMaxLength(48);
        nameBox.setValue(draft.name());
        nameBox.setResponder(value -> {
            if (!value.isBlank() && !value.strip().equals(draft.name())) {
                draft = draft.renamed(value);
                dirty = true;
                status = "Unsaved changes.";
            }
        });
        addRenderableWidget(nameBox);

        int bindingTop = layout.top() + 37;
        for (int spell = 1; spell <= 4; spell++) {
            int currentSpell = spell;
            String input = draft.inputForSpell(spell).map(InputDisplayName::display).orElse("Unbound");
            String label = capturingSpell == spell ? "Press a key..." : input;
            addButton(label, right + rightWidth - 98, bindingTop + (spell - 1) * 34 + 6, 86, () -> {
                capturingSpell = currentSpell;
                status = "Capturing Spell " + currentSpell + ". Esc cancels; Delete unbinds.";
                rebuildWidgets();
            });
        }

        int firstActionsY = bindingTop + 140;
        int x = right + 10;
        addButton("Import keys", x, firstActionsY, 82, this::importBindingsIntoDraft);
        x += 87;
        addButton("Duplicate", x, firstActionsY, 70, this::duplicateSelected);
        x += 75;
        addButton("Delete", x, firstActionsY, 56, this::deleteSelected);
        addButton("Save profile", right + rightWidth - 102, firstActionsY, 90, this::saveDraft);

        int secondActionsY = firstActionsY + 24;
        int availableWidth = rightWidth - 20;
        int gap = 4;
        int buttonWidth = (availableWidth - gap * 4) / 5;
        x = right + 10;
        Button temporary = addButton(
                "Use once", x, secondActionsY, buttonWidth, () -> selectMode(ManualSelectionMode.TEMPORARY));
        x += buttonWidth + gap;
        Button remembered = addButton(
                "Remember", x, secondActionsY, buttonWidth, () -> selectMode(ManualSelectionMode.REMEMBERED));
        x += buttonWidth + gap;
        Button assign = addButton(
                "Assign", x, secondActionsY, buttonWidth, () -> selectMode(ManualSelectionMode.ASSIGN_TO_CHARACTER));
        x += buttonWidth + gap;
        Button classDefault = addButton("Class", x, secondActionsY, buttonWidth, this::setClassDefault);
        x += buttonWidth + gap;
        addButton("Global", x, secondActionsY, buttonWidth, this::setGlobalDefault);
        temporary.active = draft != null;
        remembered.active = assign.active = profiles.characterId() != null;
        classDefault.active = profiles.className() != null;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, BACKGROUND);
        graphics.fill(0, 0, width, 47, HEADER);
        graphics.fill(tab == Tab.CHARACTERS ? 18 : 126, 42, tab == Tab.CHARACTERS ? 122 : 230, 45, ACCENT);

        if (tab == Tab.CHARACTERS) {
            renderCharactersTab(graphics);
        } else {
            renderProfilesTab(graphics);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderCharactersTab(GuiGraphics graphics) {
        int contentX = 12;
        int contentWidth = width - 24;
        int rowsTop = 92;
        int rowsVisible = visibleCharacterRows(rowsTop);
        graphics.drawString(font, "Automatic profile switching", 18, 57, TEXT, false);
        graphics.drawString(
                font,
                "Choose the profile loaded when each character is selected.",
                18,
                72,
                MUTED,
                false);

        List<KnownCharacter> characters = profiles.knownCharacters();
        int maxScroll = Math.max(0, characters.size() - rowsVisible);
        characterScrollRow = Math.clamp(characterScrollRow, 0, maxScroll);
        int start = Math.min(characterScrollRow, characters.size());
        int end = Math.min(start + rowsVisible, characters.size());
        boolean scrollable = maxScroll > 0;
        int listWidth = contentWidth - (scrollable ? 8 : 0);
        if (characters.isEmpty()) {
            drawPanel(graphics, contentX, rowsTop, contentWidth, 47);
            graphics.drawCenteredString(
                    font, "Join a Wynncraft character and it will appear here.", width / 2, rowsTop + 19, MUTED);
        }

        int rowsBottom = rowsTop + rowsVisible * 39;
        graphics.enableScissor(contentX, rowsTop, contentX + contentWidth, rowsBottom);
        for (int index = start; index < end; index++) {
            KnownCharacter character = characters.get(index);
            int rowY = rowsTop + (index - start) * 39;
            boolean current = character.id().equals(profiles.characterId());
            graphics.fill(contentX, rowY, contentX + listWidth, rowY + 34, PANEL_BORDER);
            graphics.fill(contentX + 1, rowY + 1, contentX + listWidth - 1, rowY + 33, current ? ROW_CURRENT : ROW);
            graphics.fill(contentX + 8, rowY + 5, contentX + 36, rowY + 29, 0xFF29466E);
            String initial = character.className().substring(0, 1).toUpperCase(Locale.ROOT);
            graphics.drawCenteredString(font, initial, contentX + 22, rowY + 13, 0xFFC6DBFF);
            String nickname = character.nickname() == null ? "No nickname" : character.nickname();
            String name = titleCase(character.className()) + " · " + nickname;
            graphics.drawString(font, name, contentX + 45, rowY + 7, TEXT, false);
            String level = character.level() > 0 ? "Level " + character.level() : "Level unknown";
            graphics.drawString(
                    font,
                    level + " · " + (current ? "Current character" : "Known character"),
                    contentX + 45,
                    rowY + 19,
                    current ? GOOD : MUTED,
                    false);
        }
        graphics.disableScissor();

        if (scrollable) {
            int trackX = contentX + contentWidth - 5;
            int trackHeight = rowsVisible * 39 - 5;
            int thumbHeight = Math.max(12, trackHeight * rowsVisible / characters.size());
            int thumbTravel = trackHeight - thumbHeight;
            int thumbY = rowsTop + (maxScroll == 0 ? 0 : thumbTravel * characterScrollRow / maxScroll);
            graphics.fill(trackX, rowsTop, trackX + 3, rowsTop + trackHeight, 0xFF253146);
            graphics.fill(trackX, thumbY, trackX + 3, thumbY + thumbHeight, ACCENT);
        }

        graphics.drawString(
                font,
                "Unassigned characters use their class fallback, then the global default.",
                18,
                height - 24,
                MUTED,
                false);
        if (!status.isBlank()) graphics.drawString(font, status, 18, height - 36, statusColor(), false);
    }

    private void renderProfilesTab(GuiGraphics graphics) {
        Layout layout = profileLayout();
        drawPanel(graphics, layout.leftX(), layout.top(), layout.leftWidth(), layout.bottom() - layout.top());
        drawPanel(graphics, layout.rightX(), layout.top(), layout.rightWidth(), layout.bottom() - layout.top());
        graphics.drawString(font, "Profiles", layout.leftX() + 10, layout.top() + 14, TEXT, false);

        if (draft == null) {
            graphics.drawCenteredString(
                    font,
                    "Create a profile to begin.",
                    layout.rightX() + layout.rightWidth() / 2,
                    layout.top() + 60,
                    MUTED);
            return;
        }

        graphics.drawString(
                font,
                draft.bindings().size() + " bindings",
                layout.rightX() + layout.rightWidth() - 78,
                layout.top() + 14,
                MUTED,
                false);
        int bindingTop = layout.top() + 37;
        for (int spell = 1; spell <= 4; spell++) {
            int rowY = bindingTop + (spell - 1) * 34;
            if (spell > 1) {
                graphics.fill(
                        layout.rightX() + 10,
                        rowY,
                        layout.rightX() + layout.rightWidth() - 10,
                        rowY + 1,
                        0xFF2B374A);
            }
            graphics.drawString(font, "Spell " + spell, layout.rightX() + 10, rowY + 5, TEXT, false);
            graphics.drawString(font, SPELL_DESCRIPTIONS[spell], layout.rightX() + 10, rowY + 18, MUTED, false);
        }

        int conflicts = KeyConflictDetector.detect(draft).size();
        String shownStatus = status;
        int color = statusColor();
        if (shownStatus.isBlank()) {
            ProfileResolution active = profiles.activeResolution();
            shownStatus = active.resolved()
                    ? "Active: " + active.profile().name() + " (" + friendlySource(active) + ")"
                    : "No active profile yet.";
            color = active.resolved() ? GOOD : WARNING;
        }
        if (conflicts > 0 && capturingSpell == 0) {
            shownStatus = conflicts + " key conflict(s); profile bindings win during gameplay.";
            color = WARNING;
        }
        graphics.drawString(font, shownStatus, layout.rightX() + 10, layout.bottom() - 14, color, false);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (tab == Tab.PROFILES && capturingSpell != 0) {
            if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
                capturingSpell = 0;
                status = "Capture cancelled.";
            } else if (event.key() == GLFW.GLFW_KEY_BACKSPACE || event.key() == GLFW.GLFW_KEY_DELETE) {
                draft = draft.withoutSpell(capturingSpell);
                capturingSpell = 0;
                dirty = true;
                status = "Binding removed. Save the profile to keep this change.";
            } else if (event.key() >= 0) {
                draft = draft.withBinding(capturingSpell, new PhysicalInput(InputDevice.KEYSYM, event.key()));
                capturingSpell = 0;
                dirty = true;
                status = "Binding captured. Save the profile to keep this change.";
            }
            rebuildWidgets();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (tab == Tab.CHARACTERS
                && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && isOverCharacterScrollbar(event.x(), event.y())) {
            draggingCharacterScrollbar = true;
            scrollCharacterListTo(event.y());
            return true;
        }
        if (tab == Tab.PROFILES && capturingSpell != 0 && event.button() >= 0) {
            draft = draft.withBinding(capturingSpell, new PhysicalInput(InputDevice.MOUSE, event.button()));
            capturingSpell = 0;
            dirty = true;
            status = "Mouse binding captured. Save the profile to keep this change.";
            rebuildWidgets();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (draggingCharacterScrollbar && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            scrollCharacterListTo(event.y());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (draggingCharacterScrollbar) {
            draggingCharacterScrollbar = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (tab == Tab.CHARACTERS && isOverCharacterList(mouseX, mouseY)) {
            int rowsVisible = visibleCharacterRows(92);
            int maxScroll = Math.max(0, profiles.knownCharacters().size() - rowsVisible);
            if (maxScroll > 0 && verticalAmount != 0) {
                int direction = verticalAmount > 0 ? -1 : 1;
                int updated = Math.clamp(characterScrollRow + direction, 0, maxScroll);
                if (updated != characterScrollRow) {
                    characterScrollRow = updated;
                    rebuildWidgets();
                }
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void switchTab(Tab newTab) {
        if (tab == newTab) return;
        tab = newTab;
        capturingSpell = 0;
        draggingCharacterScrollbar = false;
        status = dirty ? "Unsaved profile changes are still in this screen." : "";
        rebuildWidgets();
    }

    private void ensureSelection() {
        List<SpellProfile> available = profiles.profiles();
        if (selectedProfileId == null || profiles.profile(selectedProfileId).isEmpty()) {
            selectedProfileId = available.isEmpty() ? null : available.getFirst().id();
            loadDraft();
        } else if (draft == null || !draft.id().equals(selectedProfileId)) {
            loadDraft();
        }
    }

    private void selectProfile(String profileId) {
        if (profileId.equals(selectedProfileId)) return;
        selectedProfileId = profileId;
        loadDraft();
        capturingSpell = 0;
        status = "";
        rebuildWidgets();
    }

    private void loadDraft() {
        draft = selectedProfileId == null ? null : profiles.profile(selectedProfileId).orElse(null);
        dirty = false;
    }

    private void saveDraft() {
        if (draft == null) return;
        if (nameBox != null && !nameBox.getValue().isBlank()) draft = draft.renamed(nameBox.getValue());
        profiles.updateProfile(draft);
        dirty = false;
        status = "Profile saved.";
        rebuildWidgets();
    }

    private void importBindingsIntoDraft() {
        if (draft == null) return;
        draft = new SpellProfile(draft.id(), draft.name(), profiles.currentNativeBindings());
        dirty = true;
        status = "Imported the current Wynntils keys. Save to keep them.";
        rebuildWidgets();
    }

    private void duplicateSelected() {
        if (draft == null) return;
        if (dirty) saveDraft();
        SpellProfile duplicate = profiles.duplicateProfile(draft.id());
        selectedProfileId = duplicate.id();
        loadDraft();
        status = "Profile duplicated.";
        rebuildWidgets();
    }

    private void deleteSelected() {
        if (draft == null) return;
        profiles.deleteProfile(draft.id());
        selectedProfileId = null;
        draft = null;
        capturingSpell = 0;
        dirty = false;
        status = "Profile deleted.";
        rebuildWidgets();
    }

    private void selectMode(ManualSelectionMode mode) {
        if (draft == null) return;
        if (dirty) saveDraft();
        profiles.select(selectedProfileId, mode);
        status = switch (mode) {
            case TEMPORARY -> "Using this profile until the character changes.";
            case REMEMBERED -> "Remembered for this character.";
            case ASSIGN_TO_CHARACTER -> "Assigned to the current character.";
        };
        rebuildWidgets();
    }

    private void setClassDefault() {
        if (draft == null || profiles.className() == null) return;
        if (dirty) saveDraft();
        profiles.setCurrentClassFallback(selectedProfileId);
        status = "Set as the " + titleCase(profiles.className()) + " fallback.";
        rebuildWidgets();
    }

    private void setGlobalDefault() {
        if (draft == null) return;
        if (dirty) saveDraft();
        profiles.setGlobalDefault(selectedProfileId);
        status = "Set as the global default.";
        rebuildWidgets();
    }

    private CycleButton<ProfileChoice> createAssignmentButton(
            KnownCharacter character, int x, int y, int width) {
        List<ProfileChoice> choices = new ArrayList<>();
        choices.add(new ProfileChoice(null, "Use fallback · " + fallbackName(character)));
        for (SpellProfile profile : profiles.profiles()) {
            choices.add(new ProfileChoice(profile.id(), profile.name()));
        }
        String assigned = profiles.assignedProfileId(character.id());
        ProfileChoice selected = choices.stream()
                .filter(choice -> Objects.equals(choice.profileId(), assigned))
                .findFirst()
                .orElse(choices.getFirst());
        return CycleButton.<ProfileChoice>builder(
                        choice -> Component.literal(choice.label() + "  v"), selected)
                .withValues(choices)
                .displayOnlyValue()
                .create(x, y, width, 20, Component.literal("Profile"), (button, choice) -> {
                    profiles.assignCharacter(character.id(), choice.profileId());
                    status = choice.profileId() == null
                            ? titleCase(character.className()) + " now uses its fallback."
                            : choice.label() + " assigned to " + character.id() + ".";
                });
    }

    private String fallbackName(KnownCharacter character) {
        String fallback = profiles.classFallbackProfileId(character.className());
        if (fallback == null) fallback = profiles.configSnapshot().globalDefaultProfileId();
        return fallback == null
                ? "No fallback"
                : profiles.profile(fallback).map(SpellProfile::name).orElse("No fallback");
    }

    private int visibleCharacterRows(int rowsTop) {
        return Math.max(1, (height - rowsTop - 44) / 39);
    }

    private boolean isOverCharacterList(double mouseX, double mouseY) {
        int rowsTop = 92;
        int rowsBottom = rowsTop + visibleCharacterRows(rowsTop) * 39;
        return mouseX >= 12 && mouseX < width - 12 && mouseY >= rowsTop && mouseY < rowsBottom;
    }

    private boolean isOverCharacterScrollbar(double mouseX, double mouseY) {
        int rowsTop = 92;
        int rowsVisible = visibleCharacterRows(rowsTop);
        int rowsBottom = rowsTop + rowsVisible * 39;
        boolean scrollable = profiles.knownCharacters().size() > rowsVisible;
        return scrollable
                && mouseX >= width - 22
                && mouseX < width - 9
                && mouseY >= rowsTop
                && mouseY < rowsBottom;
    }

    private void scrollCharacterListTo(double mouseY) {
        int rowsTop = 92;
        int rowsVisible = visibleCharacterRows(rowsTop);
        int characterCount = profiles.knownCharacters().size();
        int maxScroll = Math.max(0, characterCount - rowsVisible);
        if (maxScroll == 0) return;

        int trackHeight = rowsVisible * 39 - 5;
        int thumbHeight = Math.max(12, trackHeight * rowsVisible / characterCount);
        int thumbTravel = trackHeight - thumbHeight;
        double thumbTop = Math.clamp(mouseY - rowsTop - thumbHeight / 2.0, 0, thumbTravel);
        int updated = (int) Math.round(thumbTop * maxScroll / thumbTravel);
        if (updated != characterScrollRow) {
            characterScrollRow = updated;
            rebuildWidgets();
        }
    }

    private Layout profileLayout() {
        int leftX = 10;
        int top = 51;
        int bottom = height - 9;
        int leftWidth = Math.min(154, Math.max(122, width / 4));
        int rightX = leftX + leftWidth + 8;
        return new Layout(leftX, leftWidth, rightX, width - rightX - 10, top, bottom);
    }

    private int statusColor() {
        return status.startsWith("Unsaved") || status.contains("paused") ? WARNING : GOOD;
    }

    private static int pageCount(int itemCount, int pageSize) {
        return Math.max(1, (itemCount + pageSize - 1) / pageSize);
    }

    private static String titleCase(String value) {
        if (value == null || value.isBlank()) return "Unknown";
        String lower = value.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static String friendlySource(ProfileResolution active) {
        return active.source().name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private static void drawPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, PANEL_BORDER);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, PANEL);
    }

    private Button addButton(String label, int x, int y, int width, Runnable action) {
        return addRenderableWidget(Button.builder(Component.literal(label), button -> action.run())
                .bounds(x, y, Math.max(20, width), 20)
                .build());
    }

    private enum Tab {
        CHARACTERS,
        PROFILES
    }

    private record Layout(int leftX, int leftWidth, int rightX, int rightWidth, int top, int bottom) {}

    private record ProfileChoice(String profileId, String label) {}
}
