package net.syllyaddons.diagnostics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.syllyaddons.advisor.AttackAdvisorService;
import net.syllyaddons.impact.TerritoryImpactCache;
import net.syllyaddons.observation.ObservedStateRepository;
import net.syllyaddons.optimizer.OptimizerService;

/** Local-only, atomic debug export. Raw configs, logs, character IDs, guild identities, and profile names are excluded. */
public final class DebugBundleService {
    private final Path outputDirectory;
    private final Map<String, String> versions;
    private final ObservedStateRepository repository;
    private final OperationsHealthService healthService;
    private final AttackAdvisorService attackAdvisor;
    private final TerritoryImpactCache impactCache;
    private final OptimizerService optimizer;
    private final RedactedObservedStateFactory redactedStateFactory = new RedactedObservedStateFactory();
    private final Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    public DebugBundleService(
            Path outputDirectory,
            Map<String, String> versions,
            ObservedStateRepository repository,
            OperationsHealthService healthService,
            AttackAdvisorService attackAdvisor,
            TerritoryImpactCache impactCache,
            OptimizerService optimizer) {
        this.outputDirectory = Objects.requireNonNull(outputDirectory, "outputDirectory").toAbsolutePath().normalize();
        this.versions = Map.copyOf(Objects.requireNonNull(versions, "versions"));
        this.repository = Objects.requireNonNull(repository, "repository");
        this.healthService = Objects.requireNonNull(healthService, "healthService");
        this.attackAdvisor = Objects.requireNonNull(attackAdvisor, "attackAdvisor");
        this.impactCache = Objects.requireNonNull(impactCache, "impactCache");
        this.optimizer = Objects.requireNonNull(optimizer, "optimizer");
    }

    public synchronized DebugBundleResult export(long nowEpochMillis) throws IOException {
        if (nowEpochMillis < 0) throw new IllegalArgumentException("nowEpochMillis must be non-negative");
        Files.createDirectories(outputDirectory);
        Path destination = outputDirectory.resolve("syllyaddons-debug-" + nowEpochMillis + ".zip");
        Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp");
        var state = repository.snapshot();
        SensitiveValueRedactor redactor = SensitiveValueRedactor.from(state);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(temporary), StandardCharsets.UTF_8)) {
            write(zip, "manifest.json", manifest(nowEpochMillis));
            write(zip, "health.json", gson.toJsonTree(healthService.assess(nowEpochMillis)));
            write(zip, "observed-state-redacted.json", redactedStateFactory.create(state));
            write(zip, "calculations.json", calculations(redactor));
            write(zip, "README.txt", """
                    Sylly Addons local debug bundle

                    This archive omits raw logs, configuration files, spell profiles, character IDs, guild identities,
                    territory names, profile names, evidence notes, and alert text. Territory and guild relationships in
                    the snapshot use per-bundle aliases. Review the archive before sharing it.
                    """);
        } catch (IOException | RuntimeException exception) {
            Files.deleteIfExists(temporary);
            throw exception;
        }
        moveAtomically(temporary, destination);
        return new DebugBundleResult(destination, Files.size(destination));
    }

    private JsonObject manifest(long nowEpochMillis) {
        JsonObject root = new JsonObject();
        root.addProperty("format", "syllyaddons-debug-bundle-1");
        root.addProperty("createdAtEpochMillis", nowEpochMillis);
        root.add("versions", gson.toJsonTree(new java.util.TreeMap<>(versions)));
        root.addProperty("privacy", "redacted-local-export");
        return root;
    }

    private JsonObject calculations(SensitiveValueRedactor redactor) {
        JsonObject root = new JsonObject();
        var impact = impactCache.view();
        JsonObject impactJson = new JsonObject();
        impactJson.addProperty("status", impact.status().name());
        impactJson.addProperty("generation", impact.generation());
        impactJson.addProperty("sourceRevision", impact.requestedRevision());
        impactJson.addProperty("completedTargets", impact.completedTargets());
        impactJson.addProperty("totalTargets", impact.totalTargets());
        impactJson.addProperty("buildDurationMillis", impact.buildDurationMillis());
        impactJson.addProperty("message", redactor.redact(impact.message()));
        impactJson.addProperty("reportsAreStale", impact.reportsAreStale());
        root.add("territoryImpact", impactJson);

        JsonObject advisorJson = new JsonObject();
        attackAdvisor.latest().ifPresentOrElse(view -> {
            advisorJson.addProperty("observed", true);
            advisorJson.addProperty("available", view.advice().available());
            advisorJson.addProperty("decision", view.advice().decision().name());
            advisorJson.add("diagnostics", gson.toJsonTree(redactor.redact(view.advice().diagnostics())));
            if (view.queueValidation() != null) {
                JsonObject validation = new JsonObject();
                validation.addProperty("matches", view.queueValidation().matches());
                validation.addProperty("menuTimerSeconds", view.queueValidation().menuTimerSeconds());
                validation.addProperty("queuedTimerSeconds", view.queueValidation().queuedTimerSeconds());
                advisorJson.add("queueTimerValidation", validation);
            }
        }, () -> advisorJson.addProperty("observed", false));
        root.add("routingAdvisor", advisorJson);

        var optimizerView = optimizer.view();
        JsonObject optimizerJson = new JsonObject();
        optimizerJson.addProperty("status", optimizerView.status().name());
        optimizerJson.addProperty("sourceRevision", optimizerView.sourceRevision());
        optimizerJson.add("diagnostics", gson.toJsonTree(redactor.redact(optimizerView.diagnostics())));
        optimizerView.result().ifPresent(result -> {
            optimizerJson.addProperty("termination", result.termination().name());
            optimizerJson.addProperty("optimalityProven", result.optimalityProven());
            optimizerJson.addProperty("independentlyVerified", result.independentlyVerified());
            optimizerJson.addProperty("evaluatedNodes", result.evaluatedNodes());
            optimizerJson.addProperty("elapsedMillis", result.elapsedMillis());
            optimizerJson.addProperty("recommendationPresent", result.recommendation().isPresent());
            optimizerJson.add("calculationDiagnostics", gson.toJsonTree(redactor.redact(result.diagnostics())));
        });
        root.add("optimizer", optimizerJson);
        return root;
    }

    private void write(ZipOutputStream zip, String name, Object value) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        byte[] bytes = (value instanceof String text ? text : gson.toJson(value)).getBytes(StandardCharsets.UTF_8);
        zip.write(bytes);
        zip.closeEntry();
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
