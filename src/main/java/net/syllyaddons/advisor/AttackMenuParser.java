package net.syllyaddons.advisor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Tolerant text parser for Wynncraft's passive {@code Attacking: <territory>} inventory. */
public final class AttackMenuParser {
    private static final Pattern TITLE = Pattern.compile("(?i)^Attacking:\\s*(.+?)\\s*$");
    private static final Pattern MINUTES = Pattern.compile("(?i)([0-9]+)\\s*(?:minutes?|mins?|m)(?![a-z])");
    private static final Pattern SECONDS = Pattern.compile("(?i)([0-9]+)\\s*(?:seconds?|secs?|s)(?![a-z])");
    private static final Pattern CLOCK = Pattern.compile("(?<![0-9])([0-9]+):([0-5][0-9])(?![0-9])");

    public AttackMenuSnapshot parse(
            String title,
            List<AttackMenuEntry> entries,
            Set<String> knownTerritories,
            long observedAtEpochMillis) {
        List<String> diagnostics = new ArrayList<>();
        String target = parseTarget(title);
        if (target.isBlank()) diagnostics.add("The open container is not a recognized attack screen.");

        List<String> canonicalNames = knownTerritories == null ? List.of() : knownTerritories.stream()
                .filter(java.util.Objects::nonNull)
                .map(String::strip)
                .filter(name -> !name.isEmpty())
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
        target = canonical(target, canonicalNames);

        OptionalInt timer = OptionalInt.empty();
        LinkedHashSet<String> route = new LinkedHashSet<>();
        for (AttackMenuEntry entry : entries == null ? List.<AttackMenuEntry>of() : entries) {
            List<String> lines = new ArrayList<>();
            lines.add(entry.displayName());
            lines.addAll(entry.lore());
            boolean timerContext = containsAny(entry.displayName(), "timer", "time", "duration", "queue");
            boolean routeContext = containsAny(entry.displayName(), "route", "path");
            for (String line : lines) {
                if (timer.isEmpty() && (timerContext || containsAny(line, "timer", "time", "duration", "minute", " min"))) {
                    timer = parseTimer(line);
                }
                if (routeContext || containsAny(
                        line, "route", "path", "→", "->", "➜", "➔", "➤", "➡", "✔", "✓", "✖", "✕", "✗")) {
                    appendTerritories(route, line, canonicalNames);
                }
            }
        }
        if (timer.isEmpty()) diagnostics.add("Attack timer was not found in the displayed menu text.");
        return new AttackMenuSnapshot(target, timer, List.copyOf(route), observedAtEpochMillis, diagnostics);
    }

    private static String parseTarget(String title) {
        if (title == null) return "";
        Matcher matcher = TITLE.matcher(title.strip());
        return matcher.matches() ? matcher.group(1).strip() : "";
    }

    private static OptionalInt parseTimer(String text) {
        try {
            Matcher clock = CLOCK.matcher(text);
            if (clock.find()) {
                long total = Math.addExact(
                        Math.multiplyExact(Long.parseLong(clock.group(1)), 60L),
                        Long.parseLong(clock.group(2)));
                return total <= Integer.MAX_VALUE ? OptionalInt.of((int) total) : OptionalInt.empty();
            }
            Matcher minutes = MINUTES.matcher(text);
            Matcher seconds = SECONDS.matcher(text);
            long total = 0;
            boolean found = false;
            if (minutes.find()) {
                total = Math.multiplyExact(Long.parseLong(minutes.group(1)), 60L);
                found = true;
            }
            if (seconds.find()) {
                total = Math.addExact(total, Long.parseLong(seconds.group(1)));
                found = true;
            }
            return found && total <= Integer.MAX_VALUE ? OptionalInt.of((int) total) : OptionalInt.empty();
        } catch (NumberFormatException | ArithmeticException exception) {
            return OptionalInt.empty();
        }
    }

    private static void appendTerritories(Set<String> destination, String line, List<String> names) {
        List<TerritoryMatch> matches = new ArrayList<>();
        for (String name : names) {
            Pattern token = Pattern.compile(
                    "(?i)(?<![\\p{L}\\p{N}])" + Pattern.quote(name) + "(?![\\p{L}\\p{N}])");
            Matcher matcher = token.matcher(line);
            while (matcher.find()) matches.add(new TerritoryMatch(name, matcher.start(), matcher.end()));
        }
        matches.sort(Comparator.comparingInt(TerritoryMatch::start)
                .thenComparing(Comparator.comparingInt(TerritoryMatch::length).reversed()));
        int consumedThrough = -1;
        for (TerritoryMatch match : matches) {
            if (match.start() < consumedThrough) continue;
            destination.add(match.name());
            consumedThrough = match.end();
        }
    }

    private static String canonical(String candidate, List<String> names) {
        return names.stream().filter(name -> name.equalsIgnoreCase(candidate)).findFirst().orElse(candidate);
    }

    private static boolean containsAny(String value, String... needles) {
        String lower = value == null ? "" : value.toLowerCase(Locale.ROOT);
        for (String needle : needles) if (lower.contains(needle)) return true;
        return false;
    }

    private record TerritoryMatch(String name, int start, int end) {
        private int length() {
            return end - start;
        }
    }
}
