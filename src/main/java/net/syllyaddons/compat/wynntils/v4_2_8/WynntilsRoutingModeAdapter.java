package net.syllyaddons.compat.wynntils.v4_2_8;

import com.wynntils.core.text.StyledText;
import com.wynntils.utils.mc.LoreUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.world.item.ItemStack;
import net.syllyaddons.domain.Evidence;
import net.syllyaddons.domain.ObservedValue;
import net.syllyaddons.domain.RoutingMode;
import net.syllyaddons.observation.ObservationBatch;

/** Passively recognizes an explicitly labelled current routing mode; option labels alone are intentionally ignored. */
public final class WynntilsRoutingModeAdapter {
    private static final List<Pattern> EXPLICIT_MODE_PATTERNS = List.of(
            Pattern.compile("(?i).*\\b(?:current|active|selected)\\s+(?:hq\\s+)?routing(?:\\s+mode)?\\b.*\\b(cheapest|fastest)\\b.*"),
            Pattern.compile("(?i).*\\b(?:hq\\s+)?routing(?:\\s+mode)?\\b\\s*:?\\s*(cheapest|fastest)\\b.*"),
            Pattern.compile("(?i).*\\b(cheapest|fastest)\\b.*\\b(?:route|routing)\\b.*(?:current|active|selected|enabled|✔).*"));

    public ObservationBatch capture(List<ItemStack> items, long nowEpochMillis) {
        RoutingMode found = null;
        for (ItemStack item : items) {
            for (String line : textLines(item)) {
                RoutingMode candidate = match(line);
                if (candidate == null) continue;
                if (found != null && found != candidate) return empty(nowEpochMillis);
                found = candidate;
            }
        }

        if (found == null) return empty(nowEpochMillis);
        Evidence evidence = WynntilsEvidence.local(
                nowEpochMillis, "Passively read from an explicitly labelled current HQ routing mode");
        return new ObservationBatch(
                nowEpochMillis, null, null, null, ObservedValue.known(found, evidence), Map.of());
    }

    private static ObservationBatch empty(long nowEpochMillis) {
        return new ObservationBatch(nowEpochMillis, null, null, null, null, Map.of());
    }

    private static List<String> textLines(ItemStack item) {
        List<String> lines = new ArrayList<>();
        lines.add(StyledText.fromComponent(item.getHoverName()).getStringWithoutFormatting());
        LoreUtils.getLore(item).stream().map(StyledText::getStringWithoutFormatting).forEach(lines::add);
        return lines;
    }

    private static RoutingMode match(String line) {
        for (Pattern pattern : EXPLICIT_MODE_PATTERNS) {
            Matcher matcher = pattern.matcher(line.strip());
            if (!matcher.matches()) continue;
            return RoutingMode.valueOf(matcher.group(1).toUpperCase(Locale.ROOT));
        }
        return null;
    }
}
