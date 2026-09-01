package net.syllyaddons.recorder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.slf4j.Logger;

/** Synchronous crash-resilient JSON-lines writer. It has no network code. */
final class RecorderStore implements AutoCloseable {
    private static final DateTimeFormatter FILE_TIME =
            DateTimeFormatter.ofPattern("uuuuMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC);
    private final Path sessionsDirectory;
    private final Logger logger;
    private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();
    private BufferedWriter writer;
    private Path activeFile;
    private long startedAtMillis;
    private long sequence;

    RecorderStore(Path sessionsDirectory, Logger logger) {
        this.sessionsDirectory = sessionsDirectory.toAbsolutePath().normalize();
        this.logger = logger;
    }

    synchronized boolean recording() {
        return writer != null;
    }

    synchronized Path activeFile() {
        return activeFile;
    }

    synchronized Path start(Map<String, ?> metadata) throws IOException {
        if (recording()) return activeFile;
        Files.createDirectories(sessionsDirectory);
        startedAtMillis = System.currentTimeMillis();
        sequence = 0;
        activeFile = sessionsDirectory.resolve("session-" + FILE_TIME.format(Instant.ofEpochMilli(startedAtMillis))
                + ".jsonl");
        writer = Files.newBufferedWriter(
                activeFile,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE);
        write("session_start", metadata);
        return activeFile;
    }

    synchronized void record(String type, Map<String, ?> data) {
        if (!recording()) return;
        try {
            write(type, data);
        } catch (IOException exception) {
            logger.error("Local dev recording stopped after a write failure", exception);
            closeQuietly();
        }
    }

    synchronized Path stop() {
        if (!recording()) return activeFile;
        Path finished = activeFile;
        try {
            write("session_stop", Map.of());
        } catch (IOException exception) {
            logger.warn("Could not write the local recording footer", exception);
        } finally {
            closeQuietly();
        }
        return finished;
    }

    @Override
    public synchronized void close() {
        stop();
    }

    private void write(String type, Map<String, ?> data) throws IOException {
        long now = System.currentTimeMillis();
        JsonObject event = new JsonObject();
        event.addProperty("formatVersion", 1);
        event.addProperty("sequence", sequence++);
        event.addProperty("timestamp", Instant.ofEpochMilli(now).toString());
        event.addProperty("elapsedMillis", Math.max(0, now - startedAtMillis));
        event.addProperty("type", type);
        event.add("data", gson.toJsonTree(data));
        writer.write(gson.toJson(event));
        writer.newLine();
        writer.flush();
    }

    private void closeQuietly() {
        try {
            if (writer != null) writer.close();
        } catch (IOException exception) {
            logger.warn("Could not close the local dev recording", exception);
        } finally {
            writer = null;
        }
    }
}
