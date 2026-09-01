package net.syllyaddons.diagnostics;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;
import net.syllyaddons.advisor.AttackAdvisorService;
import net.syllyaddons.config.SyllyConfig;
import net.syllyaddons.config.SyllyConfigService;
import net.syllyaddons.impact.ImpactCacheView;
import net.syllyaddons.impact.TerritoryImpactCache;
import net.syllyaddons.observation.DataHealthReport;
import net.syllyaddons.observation.DataHealthService;
import net.syllyaddons.observation.FreshnessPolicy;
import net.syllyaddons.observation.ObservedStateRepository;
import net.syllyaddons.optimizer.OptimizationResult;
import net.syllyaddons.optimizer.OptimizerService;
import net.syllyaddons.optimizer.OptimizerView;
import net.syllyaddons.profile.SpellProfileService;

/** Produces a compact operational view without exposing character IDs, guild identity, or profile names. */
public final class OperationsHealthService {
    private final SubsystemHealthRegistry registry;
    private final ObservedStateRepository repository;
    private final SyllyConfigService settings;
    private final Supplier<SpellProfileService> profiles;
    private final AttackAdvisorService attackAdvisor;
    private final TerritoryImpactCache impactCache;
    private final OptimizerService optimizer;
    private final DataHealthService dataHealth = new DataHealthService(FreshnessPolicy.personalDefaults());

    public OperationsHealthService(
            SubsystemHealthRegistry registry,
            ObservedStateRepository repository,
            SyllyConfigService settings,
            Supplier<SpellProfileService> profiles,
            AttackAdvisorService attackAdvisor,
            TerritoryImpactCache impactCache,
            OptimizerService optimizer) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.attackAdvisor = Objects.requireNonNull(attackAdvisor, "attackAdvisor");
        this.impactCache = Objects.requireNonNull(impactCache, "impactCache");
        this.optimizer = Objects.requireNonNull(optimizer, "optimizer");
    }

    public SubsystemHealthReport assess(long nowEpochMillis) {
        var state = repository.snapshot();
        DataHealthReport observedHealth = dataHealth.assess(state, nowEpochMillis);
        SyllyConfig config = settings.snapshot();
        List<SubsystemHealth> values = new ArrayList<>();
        values.add(registry.get(Subsystem.COMPATIBILITY));
        values.add(observationHealth(state.revision(), state.territories().size(), observedHealth));
        values.add(profileHealth());
        values.add(registry.get(Subsystem.SNAPSHOTS));
        values.add(featureHealth(
                Subsystem.ECO_AUDITOR,
                config.ecoAuditorEnabled(),
                observedHealth,
                "Ready to audit observed economy"));
        values.add(impactHealth(config.territoryImpactEnabled()));
        values.add(advisorHealth(config.routingAdvisorEnabled()));
        values.add(optimizerHealth(config.optimizerEnabled()));
        return new SubsystemHealthReport(nowEpochMillis, state.revision(), values);
    }

    private SubsystemHealth observationHealth(long revision, int territoryCount, DataHealthReport report) {
        SubsystemHealth runtime = registry.get(Subsystem.OBSERVATION);
        if (runtime.status() == SubsystemHealthStatus.FAILED) return runtime;
        if (revision == 0 || territoryCount == 0) {
            return health(
                    Subsystem.OBSERVATION,
                    SubsystemHealthStatus.DEGRADED,
                    DiagnosticCategory.MISSING_DATA,
                    "Waiting for a live territory observation",
                    List.of("No complete live territory state has been assembled yet."));
        }
        if (!report.healthy()) {
            return health(
                    Subsystem.OBSERVATION,
                    SubsystemHealthStatus.DEGRADED,
                    DiagnosticCategory.MISSING_DATA,
                    report.issues().size() + " missing, stale, or estimated fields",
                    report.countsByType().entrySet().stream()
                            .map(entry -> entry.getKey() + "=" + entry.getValue())
                            .sorted()
                            .toList());
        }
        return SubsystemHealth.healthy(Subsystem.OBSERVATION, territoryCount + " territories have fresh exact inputs");
    }

    private SubsystemHealth profileHealth() {
        SubsystemHealth runtime = registry.get(Subsystem.SPELL_PROFILES);
        if (runtime.status() == SubsystemHealthStatus.FAILED) return runtime;
        SpellProfileService service = profiles.get();
        if (service == null) return SubsystemHealth.waiting(Subsystem.SPELL_PROFILES, "Waiting for Wynntils startup");
        if (!service.lastError().isBlank()) {
            DiagnosticCategory category = containsIntegrationFailure(List.of(service.lastError()))
                    ? DiagnosticCategory.INTEGRATION_FAILURE
                    : DiagnosticCategory.INTERNAL_FAILURE;
            return health(
                    Subsystem.SPELL_PROFILES,
                    SubsystemHealthStatus.DEGRADED,
                    category,
                    "Profiles are available with a startup warning",
                    List.of("See the local log or debug bundle for the non-identifying warning."));
        }
        return SubsystemHealth.healthy(Subsystem.SPELL_PROFILES, "Profile switching is available");
    }

    private SubsystemHealth featureHealth(
            Subsystem subsystem,
            boolean enabled,
            DataHealthReport observedHealth,
            String readySummary) {
        SubsystemHealth runtime = registry.get(subsystem);
        if (runtime.status() == SubsystemHealthStatus.FAILED) return runtime;
        if (!enabled) return disabled(subsystem);
        if (!observedHealth.healthy()) {
            return health(
                    subsystem,
                    SubsystemHealthStatus.DEGRADED,
                    DiagnosticCategory.MISSING_DATA,
                    "Enabled; some calculations are withheld until inputs are complete",
                    List.of(observedHealth.issues().size() + " observed-data findings currently exist."));
        }
        return SubsystemHealth.healthy(subsystem, readySummary);
    }

    private SubsystemHealth impactHealth(boolean enabled) {
        SubsystemHealth runtime = registry.get(Subsystem.TERRITORY_IMPACT);
        if (runtime.status() == SubsystemHealthStatus.FAILED) return runtime;
        if (!enabled) return disabled(Subsystem.TERRITORY_IMPACT);
        ImpactCacheView view = impactCache.view();
        return switch (view.status()) {
            case READY -> SubsystemHealth.healthy(
                    Subsystem.TERRITORY_IMPACT,
                    view.completedTargets() + " removal reports cached in " + view.buildDurationMillis() + " ms");
            case BUILDING -> health(
                    Subsystem.TERRITORY_IMPACT,
                    SubsystemHealthStatus.WAITING,
                    DiagnosticCategory.WAITING,
                    "Rebuilding " + view.completedTargets() + "/" + view.totalTargets(),
                    List.of());
            case UNAVAILABLE -> health(
                    Subsystem.TERRITORY_IMPACT,
                    SubsystemHealthStatus.DEGRADED,
                    DiagnosticCategory.MISSING_DATA,
                    "Unavailable while the guild has no observed headquarters",
                    List.of("Territory-impact routing needs an HQ; this is not an internal failure."));
            case FAILED -> health(
                    Subsystem.TERRITORY_IMPACT,
                    SubsystemHealthStatus.FAILED,
                    DiagnosticCategory.INTERNAL_FAILURE,
                    "The latest cache rebuild failed safely",
                    List.of(genericFailure(view.message())));
            case CLOSED -> health(
                    Subsystem.TERRITORY_IMPACT,
                    SubsystemHealthStatus.DISABLED,
                    DiagnosticCategory.DISABLED,
                    "Cache is closed",
                    List.of());
            case EMPTY -> health(
                    Subsystem.TERRITORY_IMPACT,
                    SubsystemHealthStatus.DEGRADED,
                    DiagnosticCategory.MISSING_DATA,
                    "No impact baseline has been built yet",
                    List.of());
        };
    }

    private SubsystemHealth advisorHealth(boolean enabled) {
        SubsystemHealth runtime = registry.get(Subsystem.ROUTING_ADVISOR);
        if (runtime.status() == SubsystemHealthStatus.FAILED) return runtime;
        if (!enabled) return disabled(Subsystem.ROUTING_ADVISOR);
        var latest = attackAdvisor.latest();
        if (latest.isEmpty()) return SubsystemHealth.waiting(Subsystem.ROUTING_ADVISOR, "No attack menu observed yet");
        List<String> diagnostics = latest.get().advice().diagnostics();
        if (latest.get().queueValidation() != null && !latest.get().queueValidation().matches()) {
            return health(
                    Subsystem.ROUTING_ADVISOR,
                    SubsystemHealthStatus.DEGRADED,
                    DiagnosticCategory.CALCULATION_DISAGREEMENT,
                    "Observed queue timer disagrees with the menu calculation",
                    List.of("Recommendation remains read-only; no action was issued."));
        }
        if (!latest.get().advice().available()) {
            DiagnosticCategory category = containsDisagreement(diagnostics)
                    ? DiagnosticCategory.CALCULATION_DISAGREEMENT
                    : DiagnosticCategory.MISSING_DATA;
            return health(
                    Subsystem.ROUTING_ADVISOR,
                    SubsystemHealthStatus.DEGRADED,
                    category,
                    category == DiagnosticCategory.CALCULATION_DISAGREEMENT
                            ? "Route calculation disagreed with observed menu data"
                            : "Recommendation withheld because required inputs are missing",
                    List.of(diagnostics.size() + " calculation diagnostics are available."));
        }
        return SubsystemHealth.healthy(Subsystem.ROUTING_ADVISOR, "Latest passive recommendation is available");
    }

    private SubsystemHealth optimizerHealth(boolean enabled) {
        SubsystemHealth runtime = registry.get(Subsystem.OPTIMIZER);
        if (runtime.status() == SubsystemHealthStatus.FAILED) return runtime;
        if (!enabled) return disabled(Subsystem.OPTIMIZER);
        OptimizerView view = optimizer.view();
        List<String> diagnostics = new ArrayList<>(view.diagnostics());
        view.result().map(OptimizationResult::diagnostics).ifPresent(diagnostics::addAll);
        if (containsDisagreement(diagnostics)) {
            return health(
                    Subsystem.OPTIMIZER,
                    SubsystemHealthStatus.DEGRADED,
                    DiagnosticCategory.CALCULATION_DISAGREEMENT,
                    "Independent economy revalidation disagreed; recommendation withheld",
                    List.of("The normal economy engine and search result did not match."));
        }
        return switch (view.status()) {
            case COMPLETE -> SubsystemHealth.healthy(Subsystem.OPTIMIZER, "Latest bounded search completed safely");
            case RUNNING -> SubsystemHealth.waiting(Subsystem.OPTIMIZER, "Bounded search is running");
            case IDLE -> SubsystemHealth.waiting(Subsystem.OPTIMIZER, "No optimizer run requested yet");
            case STALE -> health(
                    Subsystem.OPTIMIZER,
                    SubsystemHealthStatus.DEGRADED,
                    DiagnosticCategory.MISSING_DATA,
                    "Latest result is stale after an observation change",
                    List.of());
            case UNAVAILABLE -> health(
                    Subsystem.OPTIMIZER,
                    SubsystemHealthStatus.DEGRADED,
                    DiagnosticCategory.MISSING_DATA,
                    "Search withheld because required inputs are incomplete",
                    List.of(diagnostics.size() + " input diagnostics are available."));
            case FAILED -> health(
                    Subsystem.OPTIMIZER,
                    SubsystemHealthStatus.FAILED,
                    DiagnosticCategory.INTERNAL_FAILURE,
                    "Optimizer failed safely",
                    List.of("The optimizer did not modify any game state."));
        };
    }

    static boolean containsDisagreement(List<String> diagnostics) {
        return diagnostics.stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(value -> value.contains("disagree") || value.contains("mismatch"));
    }

    private static boolean containsIntegrationFailure(List<String> diagnostics) {
        return diagnostics.stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(value -> value.contains("wynntils") || value.contains("integration"));
    }

    private static SubsystemHealth disabled(Subsystem subsystem) {
        return health(
                subsystem,
                SubsystemHealthStatus.DISABLED,
                DiagnosticCategory.DISABLED,
                "Disabled in settings; other subsystems remain active",
                List.of());
    }

    private static String genericFailure(String value) {
        if (value == null || value.isBlank()) return "No additional failure reason was provided.";
        return "Failure type: " + value.strip();
    }

    private static SubsystemHealth health(
            Subsystem subsystem,
            SubsystemHealthStatus status,
            DiagnosticCategory category,
            String summary,
            List<String> diagnostics) {
        return new SubsystemHealth(subsystem, status, category, summary, diagnostics);
    }
}
