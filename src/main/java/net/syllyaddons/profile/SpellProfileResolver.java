package net.syllyaddons.profile;

import java.util.Map;

public final class SpellProfileResolver {
    public ProfileResolution resolve(
            SpellProfileConfig config,
            String characterId,
            String className,
            String temporaryOverrideProfileId,
            String currentProfileId) {
        Map<String, SpellProfile> profiles = config.profiles();

        ProfileResolution resolution = resolve(profiles, temporaryOverrideProfileId, ResolutionSource.TEMPORARY_OVERRIDE);
        if (resolution.resolved()) return resolution;

        if (characterId != null) {
            resolution = resolve(
                    profiles, config.rememberedOverrides().get(characterId), ResolutionSource.REMEMBERED_OVERRIDE);
            if (resolution.resolved()) return resolution;

            resolution = resolve(
                    profiles, config.characterAssignments().get(characterId), ResolutionSource.CHARACTER_ASSIGNMENT);
            if (resolution.resolved()) return resolution;
        }

        if (className != null) {
            resolution = resolve(profiles, config.classFallbacks().get(className), ResolutionSource.CLASS_FALLBACK);
            if (resolution.resolved()) return resolution;
        }

        resolution = resolve(profiles, config.globalDefaultProfileId(), ResolutionSource.GLOBAL_DEFAULT);
        if (resolution.resolved()) return resolution;

        resolution = resolve(profiles, currentProfileId, ResolutionSource.KEEP_CURRENT);
        return resolution.resolved() ? resolution : ProfileResolution.none();
    }

    private static ProfileResolution resolve(
            Map<String, SpellProfile> profiles, String profileId, ResolutionSource source) {
        if (profileId == null) return ProfileResolution.none();
        SpellProfile profile = profiles.get(profileId);
        return profile == null ? ProfileResolution.none() : new ProfileResolution(profile, source);
    }
}
