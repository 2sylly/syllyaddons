package net.syllyaddons.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;

class SpellProfileResolverTest {
    private final SpellProfile character = new SpellProfile("character", "Character", java.util.List.of());
    private final SpellProfile classFallback = new SpellProfile("class", "Class", java.util.List.of());
    private final SpellProfile global = new SpellProfile("global", "Global", java.util.List.of());
    private final SpellProfile manual = new SpellProfile("manual", "Manual", java.util.List.of());
    private final Map<String, SpellProfile> profiles = Map.of(
            character.id(), character,
            classFallback.id(), classFallback,
            global.id(), global,
            manual.id(), manual);
    private final SpellProfileResolver resolver = new SpellProfileResolver();

    @Test
    void followsManualCharacterClassGlobalAndKeepCurrentOrder() {
        SpellProfileConfig config = new SpellProfileConfig(
                1,
                profiles,
                Map.of("char-1", character.id()),
                Map.of("MAGE", classFallback.id()),
                global.id(),
                Map.of());

        assertResolution(config, "char-1", "MAGE", manual.id(), null, manual, ResolutionSource.TEMPORARY_OVERRIDE);
        assertResolution(config, "char-1", "MAGE", null, null, character, ResolutionSource.CHARACTER_ASSIGNMENT);
        assertResolution(config, "other", "MAGE", null, null, classFallback, ResolutionSource.CLASS_FALLBACK);
        assertResolution(config, "other", "WARRIOR", null, null, global, ResolutionSource.GLOBAL_DEFAULT);

        SpellProfileConfig noDefaults = new SpellProfileConfig(1, profiles, Map.of(), Map.of(), null, Map.of());
        assertResolution(noDefaults, "other", "WARRIOR", null, manual.id(), manual, ResolutionSource.KEEP_CURRENT);
    }

    @Test
    void rememberedManualSelectionPrecedesAssignment() {
        SpellProfileConfig config = new SpellProfileConfig(
                1,
                profiles,
                Map.of("char-1", character.id()),
                Map.of(),
                global.id(),
                Map.of("char-1", manual.id()));

        assertResolution(config, "char-1", "MAGE", null, null, manual, ResolutionSource.REMEMBERED_OVERRIDE);
    }

    private void assertResolution(
            SpellProfileConfig config,
            String characterId,
            String className,
            String temporary,
            String current,
            SpellProfile expected,
            ResolutionSource source) {
        ProfileResolution resolution = resolver.resolve(config, characterId, className, temporary, current);
        assertEquals(expected, resolution.profile());
        assertEquals(source, resolution.source());
    }
}
