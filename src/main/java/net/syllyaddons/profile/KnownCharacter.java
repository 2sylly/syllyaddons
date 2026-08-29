package net.syllyaddons.profile;

import java.util.regex.Pattern;

public record KnownCharacter(String id, String className, String nickname, int level) {
    private static final Pattern STABLE_ID = Pattern.compile("[a-z0-9]{8}");
    private static final Pattern MENU_SLOT_ID = Pattern.compile("slot:[0-9]+");

    public KnownCharacter {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
        if (className == null || className.isBlank()) throw new IllegalArgumentException("className must not be blank");
        if (level < 0) throw new IllegalArgumentException("level must not be negative");
        id = id.strip();
        className = className.strip();
        nickname = nickname == null || nickname.isBlank() ? null : nickname.strip();
    }

    public KnownCharacter(String id, String className) {
        this(id, className, null, 0);
    }

    public boolean hasStableId() {
        return STABLE_ID.matcher(id).matches();
    }

    public boolean hasCatalogId() {
        return hasStableId() || MENU_SLOT_ID.matcher(id).matches();
    }
}
