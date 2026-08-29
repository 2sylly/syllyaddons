package net.syllyaddons.domain;

public record TerritoryOwner(String guildUuid, String guildName, String guildPrefix) {
    public TerritoryOwner {
        guildUuid = normalize(guildUuid);
        guildName = normalize(guildName);
        guildPrefix = normalize(guildPrefix);
    }

    public static TerritoryOwner unowned() {
        return new TerritoryOwner("", "", "");
    }

    public boolean isOwned() {
        return !guildName.isBlank() || !guildUuid.isBlank();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip();
    }
}
