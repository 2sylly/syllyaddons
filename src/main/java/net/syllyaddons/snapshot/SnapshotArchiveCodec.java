package net.syllyaddons.snapshot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public final class SnapshotArchiveCodec {
    private static final String CHECKSUM_FIELD = "checksumSha256";
    private static final Set<String> ROOT_FIELDS = Set.of(
            "formatVersion", "createdAtEpochMillis", "sourceVersions", "payload", CHECKSUM_FIELD);
    private final Gson prettyGson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private final Gson compactGson = new GsonBuilder().disableHtmlEscaping().create();
    private final SnapshotArchiveValidator validator;

    public SnapshotArchiveCodec(SnapshotArchiveValidator validator) {
        this.validator = java.util.Objects.requireNonNull(validator, "validator");
    }

    public String encode(SnapshotArchiveContent content) throws SnapshotFormatException {
        validator.validate(content);
        JsonObject body = compactGson.toJsonTree(content).getAsJsonObject();
        String checksum = checksum(body);
        JsonObject envelope = body.deepCopy();
        envelope.addProperty(CHECKSUM_FIELD, checksum);
        return prettyGson.toJson(envelope) + "\n";
    }

    public SnapshotArchive decode(String json) throws SnapshotFormatException {
        if (json == null || json.isBlank()) throw new SnapshotFormatException(".tnsreco file is empty");
        if (json.getBytes(StandardCharsets.UTF_8).length > SnapshotArchiveValidator.MAX_FILE_BYTES) {
            throw new SnapshotFormatException(".tnsreco file exceeds the 16 MiB limit");
        }
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) throw new SnapshotFormatException(".tnsreco root must be a JSON object");
            JsonObject envelope = parsed.getAsJsonObject();
            for (String field : envelope.keySet()) {
                if (!ROOT_FIELDS.contains(field)) throw new SnapshotFormatException("Unknown root field: " + field);
            }
            for (String field : ROOT_FIELDS) {
                if (!envelope.has(field)) throw new SnapshotFormatException("Missing root field: " + field);
            }
            if (!envelope.get("formatVersion").isJsonPrimitive()) {
                throw new SnapshotFormatException("formatVersion must be a number");
            }
            int formatVersion = envelope.get("formatVersion").getAsInt();
            if (formatVersion != SnapshotArchiveContent.CURRENT_FORMAT_VERSION) {
                throw new SnapshotFormatException("Unsupported .tnsreco format version " + formatVersion);
            }
            if (!envelope.get(CHECKSUM_FIELD).isJsonPrimitive()) {
                throw new SnapshotFormatException("checksumSha256 must be text");
            }
            String suppliedChecksum = envelope.get(CHECKSUM_FIELD).getAsString().toLowerCase(java.util.Locale.ROOT);
            if (!suppliedChecksum.matches("[0-9a-f]{64}")) {
                throw new SnapshotFormatException("checksumSha256 is not a SHA-256 hex digest");
            }
            JsonObject body = envelope.deepCopy();
            body.remove(CHECKSUM_FIELD);
            String calculatedChecksum = checksum(body);
            if (!MessageDigest.isEqual(
                    suppliedChecksum.getBytes(StandardCharsets.US_ASCII),
                    calculatedChecksum.getBytes(StandardCharsets.US_ASCII))) {
                throw new SnapshotFormatException("Snapshot checksum does not match its contents");
            }
            SnapshotArchiveContent content = compactGson.fromJson(body, SnapshotArchiveContent.class);
            validator.validate(content);
            return new SnapshotArchive(content, calculatedChecksum);
        } catch (SnapshotFormatException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new SnapshotFormatException("Invalid .tnsreco JSON: " + safeMessage(exception), exception);
        }
    }

    private String checksum(JsonObject body) throws SnapshotFormatException {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonicalJson(body).getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new SnapshotFormatException("SHA-256 is unavailable", exception);
        }
    }

    private String canonicalJson(JsonElement element) {
        StringBuilder output = new StringBuilder();
        appendCanonical(element, output);
        return output.toString();
    }

    private void appendCanonical(JsonElement element, StringBuilder output) {
        if (element == null || element.isJsonNull()) {
            output.append("null");
        } else if (element.isJsonArray()) {
            output.append('[');
            for (int index = 0; index < element.getAsJsonArray().size(); index++) {
                if (index > 0) output.append(',');
                appendCanonical(element.getAsJsonArray().get(index), output);
            }
            output.append(']');
        } else if (element.isJsonObject()) {
            output.append('{');
            List<String> names = new ArrayList<>(element.getAsJsonObject().keySet());
            names.sort(Comparator.naturalOrder());
            for (int index = 0; index < names.size(); index++) {
                if (index > 0) output.append(',');
                String name = names.get(index);
                output.append(compactGson.toJson(name)).append(':');
                appendCanonical(element.getAsJsonObject().get(name), output);
            }
            output.append('}');
        } else {
            output.append(compactGson.toJson(element));
        }
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }
}
