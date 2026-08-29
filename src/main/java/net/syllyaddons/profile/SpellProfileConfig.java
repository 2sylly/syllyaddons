package net.syllyaddons.profile;

import java.util.Map;

public record SpellProfileConfig(
        int schemaVersion,
        Map<String, SpellProfile> profiles,
        Map<String, String> characterAssignments,
        Map<String, String> classFallbacks,
        String globalDefaultProfileId,
        Map<String, String> rememberedOverrides,
        Map<String, KnownCharacter> knownCharacters,
        boolean automaticSwitchingEnabled) {
    public static final int CURRENT_SCHEMA_VERSION = 2;

    public SpellProfileConfig {
        if (schemaVersion <= 0) throw new IllegalArgumentException("schemaVersion must be positive");
        if (schemaVersion < 2) automaticSwitchingEnabled = true;
        schemaVersion = Math.max(schemaVersion, CURRENT_SCHEMA_VERSION);
        profiles = profiles == null ? Map.of() : Map.copyOf(profiles);
        characterAssignments = characterAssignments == null ? Map.of() : Map.copyOf(characterAssignments);
        classFallbacks = classFallbacks == null ? Map.of() : Map.copyOf(classFallbacks);
        rememberedOverrides = rememberedOverrides == null ? Map.of() : Map.copyOf(rememberedOverrides);
        knownCharacters = knownCharacters == null ? Map.of() : Map.copyOf(knownCharacters);
        globalDefaultProfileId = normalizeNullable(globalDefaultProfileId);
    }

    public SpellProfileConfig(
            int schemaVersion,
            Map<String, SpellProfile> profiles,
            Map<String, String> characterAssignments,
            Map<String, String> classFallbacks,
            String globalDefaultProfileId,
            Map<String, String> rememberedOverrides) {
        this(
                schemaVersion,
                profiles,
                characterAssignments,
                classFallbacks,
                globalDefaultProfileId,
                rememberedOverrides,
                Map.of(),
                true);
    }

    public static SpellProfileConfig empty() {
        return new SpellProfileConfig(
                CURRENT_SCHEMA_VERSION, Map.of(), Map.of(), Map.of(), null, Map.of(), Map.of(), true);
    }

    private static String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
