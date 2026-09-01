package net.syllyaddons.diagnostics;

import com.google.gson.Gson;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.slf4j.Logger;

/** Small allow-listed JSON logger. Player names, IDs, guilds, territory names, and profile names have no valid key. */
public final class StructuredDiagnosticLogger {
    private static final Gson GSON = new Gson();
    private static final Set<String> ALLOWED_FIELDS = Set.of(
            "subsystem",
            "status",
            "category",
            "revision",
            "count",
            "durationMillis",
            "generation",
            "version",
            "fileName",
            "errorType");

    private StructuredDiagnosticLogger() {}

    public static void info(Logger logger, String event, Map<String, ?> fields) {
        logger.info("{}", format(event, fields));
    }

    public static void warn(Logger logger, String event, Map<String, ?> fields) {
        logger.warn("{}", format(event, fields));
    }

    public static void error(Logger logger, String event, Map<String, ?> fields) {
        logger.error("{}", format(event, fields));
    }

    public static String format(String event, Map<String, ?> fields) {
        if (event == null || !event.matches("[a-z0-9_]+")) {
            throw new IllegalArgumentException("Diagnostic event must use lowercase snake_case");
        }
        TreeMap<String, Object> safeFields = new TreeMap<>();
        for (Map.Entry<String, ?> entry : fields.entrySet()) {
            if (!ALLOWED_FIELDS.contains(entry.getKey())) {
                throw new IllegalArgumentException("Sensitive or unknown diagnostic field: " + entry.getKey());
            }
            Object value = entry.getValue();
            if (value != null && !(value instanceof Number) && !(value instanceof Boolean)
                    && !(value instanceof Enum<?>) && !(value instanceof String)) {
                throw new IllegalArgumentException("Unsupported diagnostic value for " + entry.getKey());
            }
            safeFields.put(entry.getKey(), value instanceof Enum<?> enumValue ? enumValue.name() : value);
        }
        LinkedHashMap<String, Object> document = new LinkedHashMap<>();
        document.put("event", event);
        document.putAll(safeFields);
        return GSON.toJson(document);
    }
}
