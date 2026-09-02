package net.syllyaddons.advisor;

import java.util.Locale;

/** Recognizes the real attack action without relying on a fixed inventory slot. */
public final class AttackButtonDetector {
    public boolean matches(AttackMenuEntry entry) {
        if (entry == null) return false;
        String name = normalize(entry.displayName());
        boolean attackName = name.equals("attack")
                || name.startsWith("attack with your guild")
                || name.startsWith("attack territory");
        if (!attackName) return false;

        boolean hasQueueTime = false;
        boolean hasClickCue = false;
        for (String lore : entry.lore()) {
            String line = normalize(lore);
            hasQueueTime |= line.contains("time to start") || line.contains("queue time") || line.contains("timer");
            hasClickCue |= line.contains("click") && (line.contains("attack") || line.contains("queue"));
        }
        return hasQueueTime || hasClickCue;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }
}
