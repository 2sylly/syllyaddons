package net.syllyaddons.impact;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import net.syllyaddons.domain.EcoSnapshot;
import net.syllyaddons.domain.EvidenceKind;
import net.syllyaddons.domain.GuildIdentity;
import net.syllyaddons.domain.ObservedState;
import net.syllyaddons.domain.ResourceType;
import net.syllyaddons.domain.RoutingMode;
import net.syllyaddons.domain.TerritoryState;
import net.syllyaddons.economy.EconomyResult;
import net.syllyaddons.economy.ResourceEconomySummary;
import net.syllyaddons.routing.ObservedTerritoryGraphFactory;
import net.syllyaddons.routing.OwnerTaxPolicy;
import net.syllyaddons.routing.RouteDiagnostic;
import net.syllyaddons.routing.RouteEngine;
import net.syllyaddons.routing.RouteResult;
import net.syllyaddons.routing.RoutingRules;
import net.syllyaddons.routing.RuleConfidence;
import net.syllyaddons.routing.TerritoryGraph;
import net.syllyaddons.snapshot.ObservedEconomyAnalyzer;
import net.syllyaddons.snapshot.SnapshotPayload;

/** Full recomputation simulator. No live state or cached route is mutated by a removal. */
public final class TerritoryImpactSimulator implements TerritoryImpactComputer {
    private static final double EPSILON = 1.0e-9;
    private final ImpactCacheKeyFactory keyFactory = new ImpactCacheKeyFactory();
    private final ImpactStateFactory stateFactory = new ImpactStateFactory();
    private final ObservedTerritoryGraphFactory graphFactory = new ObservedTerritoryGraphFactory();
    private final CriticalTerritoryAnalyzer criticalAnalyzer = new CriticalTerritoryAnalyzer();
    private final ObservedEconomyAnalyzer economyAnalyzer = new ObservedEconomyAnalyzer();
    private final RouteEngine routeEngine = new RouteEngine();

    @Override
    public ImpactBaseline buildBaseline(ObservedState state, long nowEpochMillis) {
        if (!state.hqTerritory().isKnown()) throw new ImpactUnavailableException("An observed headquarters is required");
        if (!state.guild().isKnown()) throw new ImpactUnavailableException("An observed guild identity is required");
        if (state.territories().isEmpty()) throw new ImpactUnavailableException("No territory topology is available");
        String headquarters = state.hqTerritory().value();
        if (!state.territories().containsKey(headquarters)) {
            throw new ImpactUnavailableException("The observed headquarters is absent from the territory map");
        }

        List<RoutingMode> modes = state.routingMode().isKnown()
                ? List.of(state.routingMode().value())
                : List.of(RoutingMode.CHEAPEST, RoutingMode.FASTEST);
        List<String> owned = ownedTerritories(state);
        if (owned.isEmpty()) throw new ImpactUnavailableException("No territories are attributed to the observed guild");
        ObservedState firstMode = stateFactory.withMode(state, modes.getFirst(), nowEpochMillis);
        TerritoryGraph graph = graphFactory.create(EcoSnapshot.from(firstMode, nowEpochMillis));
        OwnerTaxPolicy taxPolicy = taxPolicy(state.guild().value());
        RoutingRules routingRules = RoutingRules.research2026_08_29();

        Map<RoutingMode, SnapshotPayload> economies = new EnumMap<>(RoutingMode.class);
        Map<RoutingMode, Map<String, RouteResult>> routesByMode = new EnumMap<>(RoutingMode.class);
        for (RoutingMode mode : modes) {
            ObservedState modeState = stateFactory.withMode(state, mode, nowEpochMillis);
            economies.put(mode, economyAnalyzer.analyze(modeState, nowEpochMillis));
            routesByMode.put(mode, calculateRoutes(graph, owned, headquarters, mode, taxPolicy, routingRules));
        }
        return new ImpactBaseline(
                keyFactory.create(state),
                nowEpochMillis,
                state,
                graph,
                modes,
                owned,
                economies,
                routesByMode,
                criticalAnalyzer.analyze(graph, headquarters, owned),
                topologyCertainty(state, graph));
    }

    public Map<String, TerritoryImpactReport> simulateAll(
            ImpactBaseline baseline, BooleanSupplier cancelled) {
        Map<String, TerritoryImpactReport> reports = new LinkedHashMap<>();
        for (String target : baseline.graph().nodes().keySet().stream().sorted().toList()) {
            if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) break;
            reports.put(target, simulate(baseline, target));
        }
        return Map.copyOf(reports);
    }

    @Override
    public TerritoryImpactReport simulate(ImpactBaseline baseline, String removedTerritory) {
        if (baseline.graph().node(removedTerritory) == null) {
            throw new IllegalArgumentException("Unknown removal target " + removedTerritory);
        }
        String headquarters = baseline.observation().hqTerritory().value();
        OwnerRelation ownerRelation = ownerRelation(baseline.observation(), removedTerritory);
        Map<RoutingMode, RoutingModeImpact> modes = new EnumMap<>(RoutingMode.class);
        List<String> reportMissing = new ArrayList<>();
        if (ownerRelation != OwnerRelation.OWN_GUILD) {
            reportMissing.add("Enemy production, upgrades, HQ, and tower supply are not inferred; scores describe the observed guild network only.");
        }

        for (RoutingMode mode : baseline.modes()) {
            ImpactSimulationState simulation = stateFactory.create(
                    baseline.observation(), mode, removedTerritory, baseline.builtAtEpochMillis());
            TerritoryGraph simulatedGraph = simulation.simulatedGraph();
            OwnerTaxPolicy taxes = taxPolicy(baseline.observation().guild().value());
            Map<String, RouteResult> simulatedRoutes = calculateRoutes(
                    simulatedGraph,
                    baseline.ownedTerritories(),
                    headquarters,
                    mode,
                    taxes,
                    RoutingRules.research2026_08_29());
            Map<String, Set<String>> simulatedCritical = criticalAnalyzer.analyze(
                    simulatedGraph, headquarters, baseline.ownedTerritories());
            List<TerritoryRouteImpact> routeImpacts = routeImpacts(
                    baseline.routesByMode().get(mode),
                    simulatedRoutes,
                    baseline.criticalTerritoriesBySource(),
                    simulatedCritical,
                    removedTerritory);

            SnapshotPayload baselineEconomy = baseline.economyByMode().get(mode);
            SnapshotPayload simulatedEconomy = economyAnalyzer.analyze(
                    simulation.simulatedObservation(), baseline.builtAtEpochMillis());
            Map<ResourceType, ResourceImpactDelta> resourceDeltas = resourceDeltas(baselineEconomy, simulatedEconomy);
            ImpactCertainty economyCertainty = baselineEconomy.economy() == null
                    ? ImpactCertainty.UNAVAILABLE
                    : ImpactCertainty.ESTIMATED;
            ImpactCertainty simulatedTopology = weakest(
                    baseline.topologyCertainty(),
                    topologyCertainty(simulation.simulatedObservation(), simulatedGraph));
            ImpactScore defensive = score(
                    true,
                    removedTerritory,
                    removedTerritory.equals(headquarters),
                    ownerRelation,
                    routeImpacts,
                    resourceDeltas,
                    economyCertainty);
            ImpactScore offensive = score(
                    false,
                    removedTerritory,
                    removedTerritory.equals(headquarters),
                    ownerRelation,
                    routeImpacts,
                    resourceDeltas,
                    economyCertainty);
            List<RouteDiagnostic> diagnostics = new ArrayList<>(baselineEconomy.analysisDiagnostics());
            if (baselineEconomy.economy() != null) diagnostics.addAll(baselineEconomy.economy().diagnostics());
            diagnostics.addAll(simulatedEconomy.analysisDiagnostics());
            if (simulatedEconomy.economy() != null) diagnostics.addAll(simulatedEconomy.economy().diagnostics());
            modes.put(mode, new RoutingModeImpact(
                    mode,
                    routeImpacts,
                    resourceDeltas,
                    simulatedTopology,
                    ImpactCertainty.ESTIMATED,
                    economyCertainty,
                    defensive,
                    offensive,
                    diagnostics.stream().distinct().toList()));
        }
        return new TerritoryImpactReport(
                removedTerritory,
                ownerRelation,
                removedTerritory.equals(headquarters),
                baseline.observation().revision(),
                baseline.cacheKey(),
                modes,
                reportMissing);
    }

    private List<TerritoryRouteImpact> routeImpacts(
            Map<String, RouteResult> baselineRoutes,
            Map<String, RouteResult> simulatedRoutes,
            Map<String, Set<String>> baselineCritical,
            Map<String, Set<String>> simulatedCritical,
            String removedTerritory) {
        List<TerritoryRouteImpact> impacts = new ArrayList<>();
        for (String source : baselineRoutes.keySet().stream().sorted().toList()) {
            RouteResult before = baselineRoutes.get(source);
            RouteResult after = simulatedRoutes.get(source);
            Set<RouteChangeKind> changes = new LinkedHashSet<>();
            if (before.found() && (source.equals(removedTerritory) || after == null || !after.found())) {
                changes.add(RouteChangeKind.DISCONNECTED);
            } else if (after != null && before.path().equals(after.path())) {
                changes.add(RouteChangeKind.UNCHANGED);
            } else if (before.found() || (after != null && after.found())) {
                changes.add(RouteChangeKind.REROUTED);
            } else {
                changes.add(RouteChangeKind.UNCHANGED);
            }
            Set<String> newlyCritical = new HashSet<>(simulatedCritical.getOrDefault(source, Set.of()));
            newlyCritical.removeAll(baselineCritical.getOrDefault(source, Set.of()));
            newlyCritical.remove(removedTerritory);
            if (!newlyCritical.isEmpty()) changes.add(RouteChangeKind.NEWLY_CRITICAL);
            impacts.add(new TerritoryRouteImpact(
                    source,
                    changes,
                    before.path(),
                    after == null ? List.of() : after.path(),
                    before.deliverySeconds(),
                    after == null ? 0 : after.deliverySeconds(),
                    before.selectionCost(),
                    after == null ? 0 : after.selectionCost(),
                    newlyCritical.stream().sorted().toList(),
                    after == null ? RuleConfidence.UNKNOWN
                            : RuleConfidence.weakest(before.confidence(), after.confidence())));
        }
        return List.copyOf(impacts);
    }

    private static Map<ResourceType, ResourceImpactDelta> resourceDeltas(
            SnapshotPayload baseline, SnapshotPayload simulated) {
        Map<ResourceType, ResourceImpactDelta> values = new EnumMap<>(ResourceType.class);
        for (ResourceType resource : ResourceType.values()) {
            ResourceEconomySummary before = summary(baseline.economy(), resource);
            ResourceEconomySummary after = summary(simulated.economy(), resource);
            values.put(resource, new ResourceImpactDelta(
                    resource,
                    before.deliveredProduction(),
                    after.deliveredProduction(),
                    after.deliveredProduction() - before.deliveredProduction(),
                    before.taxLoss(),
                    after.taxLoss(),
                    after.taxLoss() - before.taxLoss(),
                    before.spent(),
                    after.spent(),
                    after.spent() - before.spent(),
                    before.deficit(),
                    after.deficit(),
                    after.deficit() - before.deficit(),
                    before.endingStorage(),
                    after.endingStorage(),
                    after.endingStorage() - before.endingStorage(),
                    baseline.economy() == null ? ImpactCertainty.UNAVAILABLE : ImpactCertainty.ESTIMATED));
        }
        return Map.copyOf(values);
    }

    private static ResourceEconomySummary summary(EconomyResult economy, ResourceType resource) {
        if (economy != null && economy.summaries().containsKey(resource)) return economy.summaries().get(resource);
        return new ResourceEconomySummary(resource, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    private static ImpactScore score(
            boolean defensive,
            String removedTerritory,
            boolean headquartersRemoved,
            OwnerRelation ownerRelation,
            List<TerritoryRouteImpact> routes,
            Map<ResourceType, ResourceImpactDelta> resources,
            ImpactCertainty economyCertainty) {
        double disconnected = routes.stream()
                .filter(value -> !value.sourceTerritory().equals(removedTerritory))
                .filter(value -> value.changes().contains(RouteChangeKind.DISCONNECTED)).count();
        double rerouted = routes.stream().filter(value -> value.changes().contains(RouteChangeKind.REROUTED)).count();
        double newlyCritical = routes.stream().flatMap(value -> value.newlyCriticalTerritories().stream()).distinct().count();
        double delayMinutes = routes.stream()
                .filter(value -> !value.changes().contains(RouteChangeKind.DISCONNECTED))
                .mapToLong(value -> Math.max(0, value.deliveryDeltaSeconds())).sum() / 60.0;
        double baselineDelivered = resources.values().stream().mapToDouble(ResourceImpactDelta::baselineDeliveredPerHour).sum();
        double deliveredLoss = resources.values().stream().mapToDouble(value -> Math.max(0, -value.deliveredDeltaPerHour())).sum();
        double deliveredLossFraction = baselineDelivered <= EPSILON ? 0 : deliveredLoss / baselineDelivered;
        double baselineDeficitScale = Math.max(1, resources.values().stream()
                .mapToDouble(value -> Math.max(0, value.baselineDeliveredPerHour())).sum());
        double deficitIncreaseFraction = resources.values().stream()
                        .mapToDouble(value -> Math.max(0, value.deficitDeltaPerHour())).sum()
                / baselineDeficitScale;
        double taxIncrease = resources.values().stream().mapToDouble(value -> Math.max(0, value.taxLossDeltaPerHour())).sum();
        double taxIncreaseFraction = baselineDelivered <= EPSILON ? 0 : taxIncrease / baselineDelivered;

        List<ImpactScoreFactor> factors = new ArrayList<>();
        if (headquartersRemoved) add(factors, "Headquarters removed", "HQ flag × weight", 1, defensive ? 70 : 80, defensive ? 70 : 80);
        addCapped(factors, "Disconnected owned territories", "min(count × weight, cap)", disconnected, defensive ? 15 : 18, defensive ? 45 : 50);
        addCapped(factors, "Rerouted owned territories", "min(count × weight, cap)", rerouted, defensive ? 3 : 5, defensive ? 15 : 20);
        addCapped(factors, "Newly critical territories", "min(count × weight, cap)", newlyCritical, defensive ? 4 : 6, defensive ? 12 : 18);
        addCapped(factors, "Added delivery minutes", "min(minutes × weight, cap)", delayMinutes, defensive ? 0.5 : 0.75, defensive ? 8 : 10);
        addCapped(factors, "HQ delivery loss fraction", "min(loss fraction × weight, cap)", deliveredLossFraction, defensive ? 35 : 20, defensive ? 35 : 20);
        if (defensive) addCapped(factors, "Tower deficit increase fraction", "min(deficit fraction × weight, cap)", deficitIncreaseFraction, 20, 15);
        if (!defensive) addCapped(factors, "Tax increase fraction", "min(tax fraction × weight, cap)", taxIncreaseFraction, 15, 12);
        double total = Math.min(100, factors.stream().mapToDouble(ImpactScoreFactor::contribution).sum());
        List<String> missing = new ArrayList<>();
        missing.add("Severity weights are advisory Track 7 rules, not Wynncraft server values.");
        if (ownerRelation != OwnerRelation.OWN_GUILD) {
            missing.add("Enemy HQ, production, upgrades, and defensive intent are unavailable; no enemy numeric effect is claimed.");
        }
        if (economyCertainty == ImpactCertainty.UNAVAILABLE) {
            missing.add("Economy inputs are unavailable; the score is driven by topology only.");
        }
        return new ImpactScore(
                total,
                ImpactSeverity.fromScore(total),
                ImpactCertainty.ESTIMATED,
                factors,
                missing);
    }

    private static void addCapped(
            List<ImpactScoreFactor> factors, String label, String formula, double input, double weight, double cap) {
        add(factors, label, formula, input, weight, Math.min(cap, input * weight));
    }

    private static void add(
            List<ImpactScoreFactor> factors, String label, String formula, double input, double weight, double contribution) {
        if (contribution > EPSILON) factors.add(new ImpactScoreFactor(label, formula, input, weight, contribution));
    }

    private static Map<String, RouteResult> calculateRoutes(
            TerritoryGraph graph,
            List<String> sources,
            String headquarters,
            RoutingMode mode,
            OwnerTaxPolicy taxPolicy,
            RoutingRules rules) {
        RouteEngine engine = new RouteEngine();
        Map<String, RouteResult> routes = new LinkedHashMap<>();
        for (String source : sources) {
            routes.put(source, engine.find(graph, source, headquarters, mode, taxPolicy, rules));
        }
        return Map.copyOf(routes);
    }

    private static List<String> ownedTerritories(ObservedState state) {
        GuildIdentity guild = state.guild().value();
        return state.territories().values().stream()
                .filter(territory -> territory.owner().isKnown())
                .filter(territory -> ownedBy(territory, guild))
                .map(TerritoryState::name)
                .sorted()
                .toList();
    }

    private static OwnerRelation ownerRelation(ObservedState state, String territoryName) {
        TerritoryState territory = state.territories().get(territoryName);
        if (territory == null || !territory.owner().isKnown() || !state.guild().isKnown()) return OwnerRelation.UNKNOWN;
        return ownedBy(territory, state.guild().value()) ? OwnerRelation.OWN_GUILD : OwnerRelation.FOREIGN_GUILD;
    }

    private static boolean ownedBy(TerritoryState territory, GuildIdentity guild) {
        var owner = territory.owner().value();
        return (!guild.uuid().isBlank() && guild.uuid().equals(owner.guildUuid()))
                || (!guild.name().isBlank() && guild.name().equals(owner.guildName()));
    }

    private static OwnerTaxPolicy taxPolicy(GuildIdentity guild) {
        Set<String> identifiers = new HashSet<>();
        if (!guild.uuid().isBlank()) identifiers.add(guild.uuid());
        if (!guild.name().isBlank()) identifiers.add(guild.name());
        return new OwnerTaxPolicy(identifiers, ObservedEconomyAnalyzer.ASSUMED_FOREIGN_TAX_RATE);
    }

    private static ImpactCertainty topologyCertainty(ObservedState state, TerritoryGraph graph) {
        boolean complete = graph.diagnostics().isEmpty() && state.territories().values().stream()
                .allMatch(territory -> territory.links().isKnown()
                        && territory.links().evidence().kind().reliability() >= EvidenceKind.PUBLIC_EXACT.reliability());
        return complete ? ImpactCertainty.EXACT_OBSERVATION : ImpactCertainty.ESTIMATED;
    }

    private static ImpactCertainty weakest(ImpactCertainty left, ImpactCertainty right) {
        return left.ordinal() >= right.ordinal() ? left : right;
    }
}
