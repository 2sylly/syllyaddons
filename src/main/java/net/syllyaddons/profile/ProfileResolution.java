package net.syllyaddons.profile;

public record ProfileResolution(SpellProfile profile, ResolutionSource source) {
    public static ProfileResolution none() {
        return new ProfileResolution(null, ResolutionSource.NONE);
    }

    public boolean resolved() {
        return profile != null;
    }
}
