package net.syllyaddons.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.syllyaddons.domain.CharacterIdentity;
import net.syllyaddons.domain.Evidence;
import net.syllyaddons.domain.EvidenceKind;
import net.syllyaddons.domain.ObservedState;
import net.syllyaddons.domain.ObservedValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SpellProfileServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void importsNativeBindingsResolvesCharacterAndDispatchesWithoutChangingNativeConfig() {
        List<Integer> castSpells = new ArrayList<>();
        List<SpellBinding> nativeBindings = List.of(
                new SpellBinding(new PhysicalInput(InputDevice.KEYSYM, 81), 1),
                new SpellBinding(new PhysicalInput(InputDevice.MOUSE, 4), 3));
        SpellProfileService service = new SpellProfileService(
                new SpellProfileStore(temporaryDirectory.resolve("profiles.json")),
                spell -> {
                    castSpells.add(spell);
                    return SpellCastResult.QUEUED;
                },
                () -> nativeBindings,
                ignored -> {});

        service.initialize(stateFor("c25d20ba", "MAGE"));

        assertTrue(service.activeResolution().resolved());
        assertEquals(ResolutionSource.GLOBAL_DEFAULT, service.activeResolution().source());
        assertEquals(3, service.spellForInput(new PhysicalInput(InputDevice.MOUSE, 4)).orElseThrow());
        assertEquals(SpellCastResult.QUEUED, service.castSpell(3));
        assertEquals(List.of(3), castSpells);
        assertEquals(nativeBindings, service.currentNativeBindings());

        SpellProfile custom = service.createProfile()
                .withBinding(2, new PhysicalInput(InputDevice.KEYSYM, 69));
        service.updateProfile(custom);
        service.select(custom.id(), ManualSelectionMode.ASSIGN_TO_CHARACTER);
        assertEquals(custom.id(), service.activeResolution().profile().id());

        service.clearSession();
        assertFalse(service.activeResolution().resolved());
    }

    @Test
    void sameCharacterObservationRestoresBindingsAfterWorldSessionClear() {
        SpellProfileService service = new SpellProfileService(
                new SpellProfileStore(temporaryDirectory.resolve("world-hop-profiles.json")),
                ignored -> SpellCastResult.QUEUED,
                () -> List.of(new SpellBinding(new PhysicalInput(InputDevice.KEYSYM, 81), 1)),
                ignored -> {});
        ObservedState sameCharacter = stateFor("c25d20ba", "MAGE");
        service.initialize(sameCharacter);
        SpellProfile assigned = service.createProfile()
                .withBinding(4, new PhysicalInput(InputDevice.KEYSYM, 82));
        service.updateProfile(assigned);
        service.assignCharacter("c25d20ba", assigned.id());

        service.clearSession();
        assertFalse(service.spellForInput(new PhysicalInput(InputDevice.KEYSYM, 82)).isPresent());

        service.onObservedState(sameCharacter);

        assertEquals(assigned.id(), service.activeResolution().profile().id());
        assertEquals(4, service.spellForInput(new PhysicalInput(InputDevice.KEYSYM, 82)).orElseThrow());
    }

    @Test
    void notifiesOnlyWhenResolvedProfileIdChanges() {
        List<String> notifications = new ArrayList<>();
        SpellProfileService service = new SpellProfileService(
                new SpellProfileStore(temporaryDirectory.resolve("profiles.json")),
                ignored -> SpellCastResult.QUEUED,
                List::of,
                CharacterCatalogProvider.unavailable(),
                ignored -> {},
                notifications::add);

        service.initialize(stateFor("c25d20ba", "MAGE"));
        assertEquals(List.of("Imported Wynntils"), notifications);
        notifications.clear();

        SpellProfile profile = service.createProfile().renamed("Lightbender");
        service.updateProfile(profile);
        assertTrue(notifications.isEmpty());

        service.select(profile.id(), ManualSelectionMode.ASSIGN_TO_CHARACTER);
        assertEquals(List.of("Lightbender"), notifications);
        service.updateProfile(profile.renamed("Lightbender Updated"));
        assertEquals(List.of("Lightbender"), notifications);
    }

    @Test
    void remembersCharactersAndCanPauseAutomaticSwitching() throws Exception {
        Path configPath = temporaryDirectory.resolve("profiles.json");
        SpellProfileService service = new SpellProfileService(
                new SpellProfileStore(configPath),
                ignored -> SpellCastResult.QUEUED,
                List::of,
                ignored -> {});
        service.initialize(stateFor("c25d20ba", "MAGE"));

        SpellProfile mage = service.createProfile();
        service.assignCharacter("c25d20ba", mage.id());
        assertEquals(mage.id(), service.activeResolution().profile().id());
        assertEquals(List.of(new KnownCharacter("c25d20ba", "MAGE")), service.knownCharacters());

        service.setAutomaticSwitchingEnabled(false);
        service.onCharacter(new CharacterIdentity("72aa454b", "ARCHER", false));
        assertEquals(mage.id(), service.activeResolution().profile().id());
        assertEquals("72aa454b", service.knownCharacters().getFirst().id());

        service.setAutomaticSwitchingEnabled(true);
        assertEquals(ResolutionSource.GLOBAL_DEFAULT, service.activeResolution().source());

        SpellProfileConfig saved = new SpellProfileStore(configPath).load().orElseThrow();
        assertTrue(saved.automaticSwitchingEnabled());
        assertEquals(new KnownCharacter("c25d20ba", "MAGE"), saved.knownCharacters().get("c25d20ba"));
        assertEquals(new KnownCharacter("72aa454b", "ARCHER"), saved.knownCharacters().get("72aa454b"));
    }

    @Test
    void catalogCorrectsDisplayedClassesWithoutErasingUnmatchedAssignments() throws Exception {
        Path configPath = temporaryDirectory.resolve("profiles.json");
        SpellProfile profile = new SpellProfile("default", "Default", List.of());
        SpellProfileConfig stale = new SpellProfileConfig(
                SpellProfileConfig.CURRENT_SCHEMA_VERSION,
                Map.of(profile.id(), profile),
                Map.of("deadbeef", profile.id()),
                Map.of(),
                profile.id(),
                Map.of(),
                Map.of(
                        "-", new KnownCharacter("-", "SHAMAN"),
                        "c25d20ba", new KnownCharacter("c25d20ba", "WARRIOR"),
                        "deadbeef", new KnownCharacter("deadbeef", "WARRIOR")),
                true);
        new SpellProfileStore(configPath).save(stale);

        List<KnownCharacter> actualCharacters = List.of(
                new KnownCharacter("c25d20ba", "MAGE", "War Mage", 106),
                new KnownCharacter("72aa454b", "ASSASSIN", "Night", 105));
        SpellProfileService service = new SpellProfileService(
                new SpellProfileStore(configPath),
                ignored -> SpellCastResult.QUEUED,
                List::of,
                () -> Optional.of(actualCharacters),
                ignored -> {});

        service.initialize(stateFor("c25d20ba", "WARRIOR"));

        assertEquals(actualCharacters, service.knownCharacters());
        assertEquals("MAGE", service.className());
        assertEquals(Map.of("deadbeef", profile.id()), service.configSnapshot().characterAssignments());
        SpellProfileConfig saved = new SpellProfileStore(configPath).load().orElseThrow();
        assertEquals(Map.of(
                "c25d20ba", actualCharacters.get(0),
                "72aa454b", actualCharacters.get(1)), saved.knownCharacters());
    }

    @Test
    void menuCatalogShowsEveryCardAndMovesItsAssignmentWhenStableIdArrives() {
        List<KnownCharacter> menuScan = List.of(
                new KnownCharacter("slot:9", "MAGE", "War Mage", 120),
                new KnownCharacter("slot:10", "WARRIOR", null, 106));
        SpellProfileService service = new SpellProfileService(
                new SpellProfileStore(temporaryDirectory.resolve("profiles.json")),
                ignored -> SpellCastResult.QUEUED,
                List::of,
                () -> Optional.of(menuScan),
                ignored -> {});
        service.initialize(stateFor("c25d20ba", "MAGE"));

        assertEquals(menuScan, service.knownCharacters());
        SpellProfile warrior = service.createProfile();
        service.assignCharacter("slot:10", warrior.id());

        assertTrue(service.linkCatalogCharacter("slot:10", "c3bfc0b0"));
        assertEquals(warrior.id(), service.assignedProfileId("c3bfc0b0"));
        assertEquals(null, service.assignedProfileId("slot:10"));
        assertEquals(
                new KnownCharacter("c3bfc0b0", "WARRIOR", null, 106),
                service.configSnapshot().knownCharacters().get("c3bfc0b0"));
    }

    private static ObservedState stateFor(String characterId, String className) {
        Evidence evidence = new Evidence(EvidenceKind.LOCAL_EXACT, 1, "test", "1", "");
        return new ObservedState(
                1,
                1,
                1,
                ObservedValue.known(new CharacterIdentity(characterId, className, false), evidence),
                ObservedValue.unknown("guild"),
                ObservedValue.unknown("hq"),
                ObservedValue.unknown("routing"),
                java.util.Map.of());
    }
}
