package net.syllyaddons.audit;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import net.syllyaddons.domain.EcoSnapshot;
import net.syllyaddons.domain.Evidence;
import net.syllyaddons.domain.EvidenceKind;
import net.syllyaddons.domain.GuildIdentity;
import net.syllyaddons.domain.ObservedValue;
import net.syllyaddons.domain.ResourceBalance;
import net.syllyaddons.domain.ResourceType;
import net.syllyaddons.domain.TerritoryRating;
import net.syllyaddons.domain.TerritoryState;
import net.syllyaddons.economy.EconomyResult;
import net.syllyaddons.economy.ProvenanceKind;
import net.syllyaddons.economy.ResourceDeficit;
import net.syllyaddons.economy.ResourceEconomySummary;
import net.syllyaddons.economy.ResourceProvenance;
import net.syllyaddons.economy.UpgradeCatalog;
import net.syllyaddons.economy.UpgradeDefinition;
import net.syllyaddons.economy.UpgradeEffect;
import net.syllyaddons.routing.GraphDiagnosticType;
import net.syllyaddons.routing.ObservedTerritoryGraphFactory;
import net.syllyaddons.routing.OwnerTaxPolicy;
import net.syllyaddons.routing.RouteDiagnostic;
import net.syllyaddons.routing.RouteEngine;
import net.syllyaddons.routing.RouteResult;
import net.syllyaddons.routing.RoutingRules;
import net.syllyaddons.routing.TerritoryGraph;
import net.syllyaddons.snapshot.SnapshotPayload;

/** Pure Track 6 checker. It explains observations and never performs guild actions. */
public final class EcoAuditor {
    private static final double EPSILON = 1.0e-9;
    private final AuditRules rules;
    private final ObservedTerritoryGraphFactory graphFactory = new ObservedTerritoryGraphFactory();

    public EcoAuditor() {
        this(AuditRules.track6Defaults());
    }

    public EcoAuditor(AuditRules rules) {
        this.rules = Objects.requireNonNull(rules, "rules");
    }

    public AuditReport audit(SnapshotPayload payload, long nowEpochMillis) {
        Objects.requireNonNull(payload, "payload");
        if (nowEpochMillis < 0) throw new IllegalArgumentException("nowEpochMillis must be non-negative");

        List<Draft> drafts = new ArrayList<>();
        List<RouteDiagnostic> diagnostics = new ArrayList<>(payload.analysisDiagnostics());
        EconomyResult economy = payload.economy();
        if (economy == null) {
            diagnostics.add(new RouteDiagnostic(
                    "AUDIT_ECONOMY_UNAVAILABLE",
                    "Production, upkeep, route, and upgrade-margin checks need economy analysis"));
        } else {
            diagnostics.addAll(economy.diagnostics());
            checkDisconnectedProduction(payload, economy, nowEpochMillis, drafts);
            checkRouteFragility(payload, economy, nowEpochMillis, drafts);
            checkLongOrExpensiveRoutes(payload, economy, nowEpochMillis, drafts);
        }

        boolean expensesComplete = economy != null && diagnostics.stream().noneMatch(value ->
                value.code().equals("UPGRADE_EXPENSES_INCOMPLETE")
                        || value.code().equals("UNKNOWN_UPGRADE_KEYS")
                        || value.code().equals("INVALID_UPGRADE_LEVELS")
                        || value.code().equals("EXPENSE_MODEL_UNAVAILABLE"));
        if (expensesComplete) {
            checkNegativeNet(payload, economy, nowEpochMillis, drafts);
            checkTowerUpkeep(payload, economy, nowEpochMillis, drafts);
            checkSurplusAndDeficit(payload, economy, nowEpochMillis, drafts);
        } else {
            diagnostics.add(new RouteDiagnostic(
                    "AUDIT_EXPENSE_CHECKS_WITHHELD",
                    "Negative net, upkeep deficit, and surplus/deficit checks require complete owned-territory upgrade scans"));
        }

        checkStorageAndTreasury(payload, economy, nowEpochMillis, drafts);
        checkEconomicUpgrades(payload, economy, expensesComplete, nowEpochMillis, drafts, diagnostics);

        List<AuditFinding> findings = deduplicate(drafts, payload, nowEpochMillis).stream()
                .sorted(Comparator.comparing(AuditFinding::severity).reversed()
                        .thenComparing(AuditFinding::title)
                        .thenComparing(AuditFinding::rootCauseKey))
                .toList();
        return new AuditReport(
                rules.version(),
                nowEpochMillis,
                payload.observed().sourceRevision(),
                findings,
                diagnostics.stream().distinct().toList());
    }

    private void checkNegativeNet(
            SnapshotPayload payload, EconomyResult economy, long now, List<Draft> findings) {
        for (ResourceEconomySummary summary : economy.summaries().values()) {
            double net = summary.deliveredProduction() - summary.expenses();
            if (net >= -EPSILON) continue;
            List<String> territories = economy.deficits().stream()
                    .filter(value -> value.resource() == summary.resource())
                    .map(ResourceDeficit::consumerTerritory)
                    .distinct()
                    .sorted()
                    .toList();
            findings.add(draft(
                    "resource-balance:" + summary.resource(),
                    AuditFindingType.NEGATIVE_NET_PRODUCTION,
                    AuditSeverity.CRITICAL,
                    pretty(summary.resource()) + " production is negative",
                    "Delivered hourly production does not cover observed hourly upgrade upkeep.",
                    territories,
                    List.of(calculation(
                            "Net production",
                            "delivered production - upgrade upkeep",
                            Map.of("delivered production", summary.deliveredProduction(), "upgrade upkeep", summary.expenses()),
                            net,
                            "/h")),
                    List.of(),
                    List.of(),
                    provenance(economy, value -> value.resource() == summary.resource())));
        }
    }

    private void checkTowerUpkeep(
            SnapshotPayload payload, EconomyResult economy, long now, List<Draft> findings) {
        Map<String, List<ResourceDeficit>> byTerritory = economy.deficits().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        ResourceDeficit::consumerTerritory, LinkedHashMap::new, java.util.stream.Collectors.toList()));
        byTerritory.forEach((territory, deficits) -> {
            List<AuditCalculation> calculations = deficits.stream()
                    .map(deficit -> calculation(
                            pretty(deficit.resource()) + " upkeep shortfall",
                            "required - supplied",
                            Map.of("required", deficit.required(), "supplied", deficit.supplied()),
                            deficit.unmet(),
                            "/h"))
                    .toList();
            findings.add(draft(
                    "tower-upkeep:" + territory,
                    AuditFindingType.UNSUSTAINABLE_TOWER_UPKEEP,
                    AuditSeverity.CRITICAL,
                    territory + " upkeep is unsustainable",
                    "At least one observed upgrade expense cannot be funded by delivered resources and HQ storage.",
                    List.of(territory),
                    calculations,
                    List.of(),
                    List.of(),
                    provenance(economy, value -> value.spending().stream()
                            .anyMatch(spending -> spending.consumerTerritory().equals(territory)))));
        });
    }

    private void checkDisconnectedProduction(
            SnapshotPayload payload, EconomyResult economy, long now, List<Draft> findings) {
        economy.provenance().stream()
                .filter(value -> value.kind() == ProvenanceKind.PRODUCTION && value.undelivered() > EPSILON)
                .collect(java.util.stream.Collectors.groupingBy(
                        ResourceProvenance::sourceTerritory,
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()))
                .forEach((source, lots) -> {
                    List<AuditCalculation> calculations = lots.stream()
                            .map(lot -> calculation(
                                    pretty(lot.resource()) + " not delivered",
                                    "gross production - delivered to HQ",
                                    Map.of("gross production", lot.sourceAmount(), "delivered to HQ", lot.deliveredToHq()),
                                    lot.undelivered(),
                                    "/h"))
                            .toList();
                    List<String> missing = lots.stream()
                            .flatMap(lot -> lot.diagnostics().stream())
                            .map(value -> value.code() + ": " + value.message())
                            .distinct()
                            .toList();
                    findings.add(draft(
                            "route:" + source + ":disconnected",
                            AuditFindingType.PRODUCTION_NOT_REACHING_HQ,
                            AuditSeverity.CRITICAL,
                            source + " production is not reaching HQ",
                            "The route engine found no complete route for observed production from this territory.",
                            List.of(source),
                            calculations,
                            List.of("No route to the observed HQ was selected."),
                            missing,
                            lots.stream().map(EcoAuditor::provenance).toList()));
                });
    }

    private void checkRouteFragility(
            SnapshotPayload payload, EconomyResult economy, long now, List<Draft> findings) {
        EcoSnapshot snapshot = payload.observed();
        if (!snapshot.hqTerritory().isKnown()) return;
        String hq = snapshot.hqTerritory().value();
        TerritoryGraph graph = graphFactory.create(snapshot);
        boolean topologyIncomplete = graph.diagnostics().stream().anyMatch(value ->
                value.type() == GraphDiagnosticType.MISSING_LINK_DATA
                        || value.type() == GraphDiagnosticType.UNKNOWN_LINK
                        || value.type() == GraphDiagnosticType.ASYMMETRIC_LINK);
        List<String> topologyMissing = topologyIncomplete
                ? List.of("Some territory connections are missing; chokepoint classification is provisional.")
                : List.of();

        Map<String, List<ResourceProvenance>> chokepoints = new LinkedHashMap<>();
        for (ResourceProvenance lot : economy.provenance()) {
            if (lot.kind() != ProvenanceKind.PRODUCTION || lot.route().size() < 2) continue;
            List<String> route = lot.route();
            boolean foundChokepoint = false;
            for (int index = 1; index < route.size() - 1; index++) {
                String candidate = route.get(index);
                if (!reachableWithout(graph, lot.sourceTerritory(), hq, candidate)) {
                    chokepoints.computeIfAbsent(candidate, ignored -> new ArrayList<>()).add(lot);
                    foundChokepoint = true;
                }
            }
            if (!foundChokepoint && graph.neighbors(lot.sourceTerritory()).size() <= 1) {
                findings.add(draft(
                        "single-route:" + lot.sourceTerritory(),
                        AuditFindingType.SINGLE_ROUTE_OR_CHOKEPOINT,
                        AuditSeverity.WARNING,
                        lot.sourceTerritory() + " has a single route",
                        "The producing territory has only one observed connection toward HQ.",
                        List.of(lot.sourceTerritory()),
                        List.of(calculation(
                                "Observed connection count",
                                "number of normalized adjacent territories",
                                Map.of("adjacent territories", (double) graph.neighbors(lot.sourceTerritory()).size()),
                                graph.neighbors(lot.sourceTerritory()).size(),
                                " links")),
                        List.of("Route: " + String.join(" -> ", route)),
                        topologyMissing,
                        List.of(provenance(lot))));
            }
        }
        chokepoints.forEach((territory, lots) -> {
            List<String> affected = new ArrayList<>();
            affected.add(territory);
            affected.addAll(lots.stream().map(ResourceProvenance::sourceTerritory).distinct().sorted().toList());
            findings.add(draft(
                    "chokepoint:" + territory,
                    AuditFindingType.SINGLE_ROUTE_OR_CHOKEPOINT,
                    AuditSeverity.WARNING,
                    territory + " is a production chokepoint",
                    "Removing this territory disconnects one or more observed production sources from HQ.",
                    affected,
                    List.of(calculation(
                            "Dependent production lots",
                            "count of routed resource lots disconnected when the territory is removed",
                            Map.of("dependent lots", (double) lots.size()),
                            lots.size(),
                            " lots")),
                    lots.stream().map(value -> "Route: " + String.join(" -> ", value.route())).distinct().toList(),
                    topologyMissing,
                    lots.stream().map(EcoAuditor::provenance).toList()));
        });
    }

    private void checkSurplusAndDeficit(
            SnapshotPayload payload, EconomyResult economy, long now, List<Draft> findings) {
        for (ResourceEconomySummary summary : economy.summaries().values()) {
            if (summary.deficit() <= EPSILON || summary.overflowLoss() <= EPSILON) continue;
            findings.add(draft(
                    "resource-balance:" + summary.resource(),
                    AuditFindingType.SIMULTANEOUS_SURPLUS_AND_DEFICIT,
                    AuditSeverity.WARNING,
                    pretty(summary.resource()) + " is both overflowing and short",
                    "The same hourly calculation leaves unmet upkeep while other lots overflow HQ storage.",
                    economy.deficits().stream()
                            .filter(value -> value.resource() == summary.resource())
                            .map(ResourceDeficit::consumerTerritory)
                            .distinct().sorted().toList(),
                    List.of(
                            calculation("Unmet upkeep", "sum of unmet consumer expenses", Map.of("deficit", summary.deficit()), summary.deficit(), "/h"),
                            calculation("Overflow loss", "production remaining after spending and HQ capacity", Map.of("overflow", summary.overflowLoss()), summary.overflowLoss(), "/h")),
                    List.of(),
                    List.of(),
                    provenance(economy, value -> value.resource() == summary.resource()
                            && (value.overflowLoss() > EPSILON || !value.spending().isEmpty()))));
        }
    }

    private void checkStorageAndTreasury(
            SnapshotPayload payload, EconomyResult economy, long now, List<Draft> findings) {
        EcoSnapshot snapshot = payload.observed();
        if (snapshot.hqTerritory().isKnown()) {
            TerritoryState hq = snapshot.territories().get(snapshot.hqTerritory().value());
            if (hq != null && hq.resources().isKnown()) {
                for (Map.Entry<ResourceType, ResourceBalance> entry : hq.resources().value().entrySet()) {
                    ResourceBalance balance = entry.getValue();
                    if (balance.storageLimit() <= 0) continue;
                    double fraction = (double) balance.stored() / balance.storageLimit();
                    double overflow = economy == null || economy.summaries().get(entry.getKey()) == null
                            ? 0
                            : economy.summaries().get(entry.getKey()).overflowLoss();
                    if (fraction + EPSILON < rules.highStorageFraction() && overflow <= EPSILON) continue;
                    findings.add(draft(
                            "storage:" + entry.getKey(),
                            AuditFindingType.STORAGE_OR_TREASURY_RISK,
                            overflow > EPSILON ? AuditSeverity.WARNING : AuditSeverity.INFO,
                            pretty(entry.getKey()) + " storage is at risk",
                            "Observed HQ storage is near capacity or the hourly projection discards overflow.",
                            List.of(hq.name()),
                            List.of(
                                    calculation(
                                            "HQ storage fill",
                                            "stored / capacity",
                                            Map.of("stored", (double) balance.stored(), "capacity", (double) balance.storageLimit()),
                                            fraction * 100.0,
                                            "%"),
                                    calculation("Projected overflow", "post-spending production above capacity", Map.of("overflow", overflow), overflow, "/h")),
                            List.of(),
                            economy == null ? List.of() : List.of(),
                            economy == null ? List.of() : provenance(economy, value -> value.resource() == entry.getKey()
                                    && value.overflowLoss() > EPSILON)));
                }
            }
        }

        List<TerritoryState> lowTreasury = ownedTerritories(snapshot).stream()
                .filter(territory -> territory.treasury().isKnown())
                .filter(territory -> isLowTreasury(territory.treasury().value()))
                .sorted(Comparator.comparing(TerritoryState::name))
                .toList();
        if (!lowTreasury.isEmpty()) {
            findings.add(draft(
                    "treasury-risk",
                    AuditFindingType.STORAGE_OR_TREASURY_RISK,
                    lowTreasury.stream().anyMatch(value -> value.treasury().value() == TerritoryRating.NONE)
                            ? AuditSeverity.WARNING : AuditSeverity.INFO,
                    lowTreasury.size() + " territories have low treasury",
                    "Low observed treasury weakens the value supplied by treasury bonuses and deserves review.",
                    lowTreasury.stream().map(TerritoryState::name).toList(),
                    List.of(calculation(
                            "Low-treasury territory count",
                            "count of owned territories rated NONE, VERY_LOW, or LOW",
                            Map.of("territories", (double) lowTreasury.size()),
                            lowTreasury.size(),
                            " territories")),
                    lowTreasury.stream().map(value -> value.name() + ": " + value.treasury().value()).toList(),
                    List.of("Treasury ratings are categorical; exact treasury-age thresholds are not exposed."),
                    List.of()));
        }
    }

    private void checkLongOrExpensiveRoutes(
            SnapshotPayload payload, EconomyResult economy, long now, List<Draft> findings) {
        EcoSnapshot snapshot = payload.observed();
        TerritoryGraph graph = graphFactory.create(snapshot);
        Set<String> ownerIds = new HashSet<>();
        if (snapshot.guild().isKnown()) {
            GuildIdentity guild = snapshot.guild().value();
            if (!guild.uuid().isBlank()) ownerIds.add(guild.uuid());
            if (!guild.name().isBlank()) ownerIds.add(guild.name());
        }
        OwnerTaxPolicy taxPolicy = new OwnerTaxPolicy(
                ownerIds, net.syllyaddons.snapshot.ObservedEconomyAnalyzer.ASSUMED_FOREIGN_TAX_RATE);
        RoutingRules routingRules = RoutingRules.research2026_08_29();
        RouteEngine routeEngine = new RouteEngine();
        for (ResourceProvenance lot : economy.provenance()) {
            if (lot.kind() != ProvenanceKind.PRODUCTION || lot.route().isEmpty() || lot.sourceAmount() <= EPSILON) continue;
            int hops = lot.route().size() - 1;
            double taxFraction = lot.taxLoss() / lot.sourceAmount();
            RouteResult cheapest = snapshot.hqTerritory().isKnown()
                    ? routeEngine.find(
                            graph,
                            lot.sourceTerritory(),
                            snapshot.hqTerritory().value(),
                            net.syllyaddons.domain.RoutingMode.CHEAPEST,
                            taxPolicy,
                            routingRules)
                    : null;
            double alternativeTaxLoss = cheapest == null || !cheapest.found()
                    ? lot.taxLoss()
                    : compoundedTaxLoss(lot.sourceAmount(), cheapest);
            double alternativeSaving = lot.taxLoss() - alternativeTaxLoss;
            boolean avoidableExpense = cheapest != null
                    && cheapest.found()
                    && !cheapest.path().equals(lot.route())
                    && alternativeSaving > EPSILON;
            if (hops < rules.longRouteHops()
                    && taxFraction + EPSILON < rules.expensiveRouteTaxFraction()
                    && !avoidableExpense) continue;
            List<AuditCalculation> calculations = new ArrayList<>();
            calculations.add(calculation("Route length", "territories in route - 1", Map.of("route territories", (double) lot.route().size()), hops, " hops"));
            calculations.add(calculation("Route tax loss", "tax loss / gross production", Map.of("tax loss", lot.taxLoss(), "gross production", lot.sourceAmount()), taxFraction * 100.0, "%"));
            List<String> routeFacts = new ArrayList<>();
            routeFacts.add("Selected route: " + String.join(" -> ", lot.route()));
            if (avoidableExpense) {
                calculations.add(calculation(
                        "Estimated tax saved by Cheapest",
                        "selected route tax loss - Cheapest candidate tax loss",
                        Map.of("selected tax loss", lot.taxLoss(), "Cheapest tax loss", alternativeTaxLoss),
                        alternativeSaving,
                        "/h"));
                routeFacts.add("Cheapest candidate: " + String.join(" -> ", cheapest.path()));
            }
            findings.add(draft(
                    "route:" + lot.sourceTerritory() + ":" + lot.resource(),
                    AuditFindingType.LONG_OR_EXPENSIVE_ROUTE,
                    taxFraction >= rules.expensiveRouteTaxFraction() || avoidableExpense ? AuditSeverity.WARNING : AuditSeverity.INFO,
                    lot.sourceTerritory() + " has a costly " + pretty(lot.resource()) + " route",
                    "The selected route is long, loses a large share to estimated tax, has a cheaper candidate, or combines these conditions.",
                    lot.route(),
                    calculations,
                    routeFacts,
                    List.of(
                            "Foreign-edge tax is estimated when no explicit tax schedule is observable.",
                            "Fastest mode may intentionally trade resources for delivery time."),
                    List.of(provenance(lot))));
        }
    }

    private static double compoundedTaxLoss(double gross, RouteResult route) {
        double current = gross;
        for (var step : route.steps()) current -= current * step.taxRate();
        return gross - current;
    }

    private void checkEconomicUpgrades(
            SnapshotPayload payload,
            EconomyResult economy,
            boolean expensesComplete,
            long now,
            List<Draft> findings,
            List<RouteDiagnostic> diagnostics) {
        for (TerritoryState territory : ownedTerritories(payload.observed())) {
            if (!territory.upgrades().isKnown()) continue;
            for (Map.Entry<String, Integer> entry : territory.upgrades().value().entrySet()) {
                UpgradeDefinition definition = UpgradeCatalog.find(entry.getKey()).orElse(null);
                if (definition == null) continue;
                int level = entry.getValue();
                if (level <= 0 || level >= definition.levelCosts().size()) {
                    if (level >= definition.levelCosts().size()) {
                        diagnostics.add(new RouteDiagnostic(
                                "INVALID_UPGRADE_LEVEL",
                                territory.name() + " has out-of-range " + entry.getKey() + " level " + level));
                    }
                    continue;
                }
                if (!definition.hasQuantifiedEconomicEffect() || !territory.resources().isKnown()) continue;

                double currentValue = currentEffectValue(territory, definition.effect());
                double marginalLoss = definition.marginalLoss(currentValue, level);
                double saving = definition.marginalSavingAt(level);
                String root = "upgrade:" + territory.name() + ":" + definition.key();
                List<String> valuationMissing = List.of(
                        "No cross-resource market weights are observable; benefit/upkeep ratios treat resource units equally.",
                        "Treasury growth and strategic/defensive value are not included in upgrade valuation.");

                if (isProduction(definition.effect()) && currentValue <= EPSILON) {
                    findings.add(draft(
                            root,
                            AuditFindingType.DOMINATED_ECONOMIC_UPGRADE,
                            AuditSeverity.WARNING,
                            territory.name() + " has a dominated " + definition.displayName() + " tier",
                            "The upgrade consumes hourly resources but its targeted observed production is zero.",
                            List.of(territory.name()),
                            List.of(
                                    calculation("Targeted production", "sum of targeted observed production", Map.of("production", currentValue), currentValue, "/h"),
                                    calculation("Current upkeep", "Wynntils 4.2.8 level cost", Map.of("level", (double) level), definition.costAt(level), " " + pretty(definition.upkeepResource()) + "/h")),
                            List.of("Upgrade catalog: " + UpgradeCatalog.VERSION),
                            valuationMissing,
                            provenanceForTerritory(economy, territory.name())));
                }

                double ratio = saving <= EPSILON ? Double.POSITIVE_INFINITY : marginalLoss / saving;
                if (Double.isFinite(ratio) && ratio + EPSILON < rules.lowValueBenefitPerUpkeep()) {
                    findings.add(draft(
                            root,
                            AuditFindingType.LOW_VALUE_UPGRADE,
                            AuditSeverity.INFO,
                            territory.name() + " has a low-value " + definition.displayName() + " tier",
                            "The observed marginal benefit is small relative to the hourly upkeep freed by one downgrade.",
                            List.of(territory.name()),
                            List.of(
                                    calculation("Marginal observed benefit", "current value × (1 - previous multiplier / current multiplier)", Map.of("current value", currentValue, "previous multiplier", definition.effectMultipliers().get(level - 1), "current multiplier", definition.effectMultipliers().get(level)), marginalLoss, effectUnit(definition.effect())),
                                    calculation("Marginal upkeep saving", "current level cost - previous level cost", Map.of("current cost", (double) definition.costAt(level), "previous cost", (double) definition.costAt(level - 1)), saving, " " + pretty(definition.upkeepResource()) + "/h"),
                                    calculation("Benefit per upkeep", "marginal benefit / marginal upkeep saving", Map.of("marginal benefit", marginalLoss, "marginal saving", saving), ratio, " ratio")),
                            List.of("Rule threshold: " + rules.lowValueBenefitPerUpkeep() + " benefit per upkeep unit."),
                            valuationMissing,
                            provenanceForTerritory(economy, territory.name())));
                }

                SafeDowngrade downgrade = safeDowngrade(territory, definition, level, economy, expensesComplete);
                if (downgrade.safe()) {
                    findings.add(draft(
                            root,
                            AuditFindingType.POTENTIALLY_SAFE_DOWNGRADE,
                            AuditSeverity.INFO,
                            territory.name() + " may safely downgrade " + definition.displayName(),
                            "A one-level downgrade retains the configured production margin or storage headroom in this snapshot.",
                            List.of(territory.name()),
                            downgrade.calculations(),
                            List.of("This is advisory only; Sylly Addons never changes territory upgrades."),
                            concat(valuationMissing, downgrade.missingInputs()),
                            provenanceForTerritory(economy, territory.name())));
                }
            }
        }
    }

    private SafeDowngrade safeDowngrade(
            TerritoryState territory,
            UpgradeDefinition definition,
            int level,
            EconomyResult economy,
            boolean expensesComplete) {
        if (isStorage(definition.effect())) {
            double capacity = currentEffectValue(territory, definition.effect());
            double stored = currentStoredValue(territory, definition.effect());
            double loss = definition.marginalLoss(capacity, level);
            double after = capacity - loss;
            double headroom = after - stored;
            boolean safe = after > EPSILON && headroom >= after * rules.safeDowngradeRemainingMarginFraction();
            return new SafeDowngrade(safe, List.of(
                    calculation("Capacity after downgrade", "current capacity - marginal capacity loss", Map.of("current capacity", capacity, "capacity loss", loss), after, " units"),
                    calculation("Remaining headroom", "capacity after downgrade - stored", Map.of("capacity after downgrade", after, "stored", stored), headroom, " units")), List.of());
        }
        if (!expensesComplete || economy == null) {
            return new SafeDowngrade(false, List.of(), List.of("Complete upgrade expenses are required for downgrade margins."));
        }
        List<ResourceType> targets = targetResources(territory, definition.effect());
        List<AuditCalculation> calculations = new ArrayList<>();
        boolean any = false;
        boolean safe = true;
        for (ResourceType resource : targets) {
            ResourceEconomySummary summary = economy.summaries().get(resource);
            double territoryProduction = generation(territory, resource);
            if (summary == null || territoryProduction <= EPSILON) continue;
            any = true;
            double productionLoss = definition.marginalLoss(territoryProduction, level);
            double deliveryFraction = deliveredFraction(economy, territory.name(), resource);
            double deliveredLoss = productionLoss * deliveryFraction;
            double currentNet = summary.deliveredProduction() - summary.expenses();
            double afterNet = currentNet - deliveredLoss;
            double requiredMargin = summary.deliveredProduction() * rules.safeDowngradeRemainingMarginFraction();
            safe &= afterNet + EPSILON >= requiredMargin;
            calculations.add(calculation(
                    pretty(resource) + " net after downgrade",
                    "current delivered net - (marginal production loss × delivered fraction)",
                    Map.of("current net", currentNet, "marginal production loss", productionLoss, "delivered fraction", deliveryFraction),
                    afterNet,
                    "/h"));
            calculations.add(calculation(
                    pretty(resource) + " required safety margin",
                    "delivered production × configured margin fraction",
                    Map.of("delivered production", summary.deliveredProduction(), "margin fraction", rules.safeDowngradeRemainingMarginFraction()),
                    requiredMargin,
                    "/h"));
        }
        return new SafeDowngrade(any && safe, calculations, List.of("Delivery loss uses the selected route's observed delivery fraction."));
    }

    private List<AuditFinding> deduplicate(List<Draft> drafts, SnapshotPayload payload, long now) {
        Map<String, MutableFinding> merged = new LinkedHashMap<>();
        for (Draft draft : drafts) {
            merged.computeIfAbsent(draft.rootCauseKey(), ignored -> new MutableFinding(draft)).merge(draft);
        }
        return merged.values().stream().map(value -> value.freeze(evidence(payload, value.territories, now))).toList();
    }

    private static Draft draft(
            String root,
            AuditFindingType type,
            AuditSeverity severity,
            String title,
            String summary,
            List<String> territories,
            List<AuditCalculation> calculations,
            List<String> routeFacts,
            List<String> missing,
            List<AuditProvenanceReference> provenance) {
        return new Draft(root, type, severity, title, summary, calculations, routeFacts, territories, missing, provenance);
    }

    private static AuditCalculation calculation(
            String label, String formula, Map<String, Double> inputs, double result, String unit) {
        return new AuditCalculation(label, formula, inputs, result, unit);
    }

    private static List<AuditProvenanceReference> provenance(
            EconomyResult economy, Predicate<ResourceProvenance> predicate) {
        return economy.provenance().stream().filter(predicate).map(EcoAuditor::provenance).toList();
    }

    private static List<AuditProvenanceReference> provenanceForTerritory(EconomyResult economy, String territory) {
        if (economy == null) return List.of();
        return provenance(economy, value -> value.sourceTerritory().equals(territory));
    }

    private static AuditProvenanceReference provenance(ResourceProvenance value) {
        return new AuditProvenanceReference(
                value.resource(), value.sourceTerritory(), value.route(), value.sourceAmount(), value.deliveredToHq(), value.taxLoss());
    }

    private static boolean reachableWithout(TerritoryGraph graph, String source, String destination, String excluded) {
        if (source.equals(excluded) || destination.equals(excluded)) return false;
        Set<String> visited = new HashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        visited.add(source);
        queue.add(source);
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            if (current.equals(destination)) return true;
            for (String neighbor : graph.neighbors(current)) {
                if (!neighbor.equals(excluded) && visited.add(neighbor)) queue.addLast(neighbor);
            }
        }
        return false;
    }

    private static AuditEvidenceSummary evidence(SnapshotPayload payload, Set<String> territories, long now) {
        List<Evidence> evidence = new ArrayList<>();
        addKnown(evidence, payload.observed().guild());
        addKnown(evidence, payload.observed().hqTerritory());
        addKnown(evidence, payload.observed().routingMode());
        for (String name : territories) {
            TerritoryState territory = payload.observed().territories().get(name);
            if (territory == null) continue;
            addKnown(evidence, territory.owner());
            addKnown(evidence, territory.links());
            addKnown(evidence, territory.resources());
            addKnown(evidence, territory.treasury());
            addKnown(evidence, territory.treasuryBonusPercent());
            addKnown(evidence, territory.upgrades());
        }
        if (evidence.isEmpty()) {
            return new AuditEvidenceSummary(EvidenceKind.UNKNOWN, 0, 0, 0, List.of("not-observed"), List.of("none"));
        }
        EvidenceKind weakest = evidence.stream()
                .map(Evidence::kind)
                .min(Comparator.comparingInt(EvidenceKind::reliability))
                .orElse(EvidenceKind.UNKNOWN);
        long oldest = evidence.stream().mapToLong(Evidence::observedAtEpochMillis).min().orElse(0);
        long newest = evidence.stream().mapToLong(Evidence::observedAtEpochMillis).max().orElse(0);
        return new AuditEvidenceSummary(
                weakest,
                oldest,
                newest,
                Math.max(0, now - oldest),
                evidence.stream().map(Evidence::source).distinct().sorted().toList(),
                evidence.stream().map(Evidence::sourceVersion).distinct().sorted().toList());
    }

    private static <T> void addKnown(List<Evidence> destination, ObservedValue<T> value) {
        if (value.isKnown()) destination.add(value.evidence());
    }

    private static List<TerritoryState> ownedTerritories(EcoSnapshot snapshot) {
        if (!snapshot.guild().isKnown()) return List.of();
        GuildIdentity guild = snapshot.guild().value();
        return snapshot.territories().values().stream()
                .filter(territory -> territory.owner().isKnown())
                .filter(territory -> {
                    var owner = territory.owner().value();
                    return (!guild.uuid().isBlank() && guild.uuid().equals(owner.guildUuid()))
                            || (!guild.name().isBlank() && guild.name().equals(owner.guildName()));
                })
                .toList();
    }

    private static boolean isLowTreasury(TerritoryRating rating) {
        return rating == TerritoryRating.NONE || rating == TerritoryRating.VERY_LOW || rating == TerritoryRating.LOW;
    }

    private static boolean isProduction(UpgradeEffect effect) {
        return effect == UpgradeEffect.RESOURCE_PRODUCTION || effect == UpgradeEffect.EMERALD_PRODUCTION;
    }

    private static boolean isStorage(UpgradeEffect effect) {
        return effect == UpgradeEffect.RESOURCE_STORAGE || effect == UpgradeEffect.EMERALD_STORAGE;
    }

    private static double currentEffectValue(TerritoryState territory, UpgradeEffect effect) {
        if (!territory.resources().isKnown()) return 0;
        return switch (effect) {
            case RESOURCE_PRODUCTION -> territory.resources().value().entrySet().stream()
                    .filter(entry -> entry.getKey() != ResourceType.EMERALDS)
                    .mapToDouble(entry -> entry.getValue().generationPerHour()).sum();
            case EMERALD_PRODUCTION -> generation(territory, ResourceType.EMERALDS);
            case RESOURCE_STORAGE -> territory.resources().value().entrySet().stream()
                    .filter(entry -> entry.getKey() != ResourceType.EMERALDS)
                    .mapToDouble(entry -> entry.getValue().storageLimit()).sum();
            case EMERALD_STORAGE -> territory.resources().value().getOrDefault(ResourceType.EMERALDS, new ResourceBalance(0, 0, 0)).storageLimit();
            case NONE -> 0;
        };
    }

    private static double currentStoredValue(TerritoryState territory, UpgradeEffect effect) {
        if (!territory.resources().isKnown()) return 0;
        return switch (effect) {
            case RESOURCE_STORAGE -> territory.resources().value().entrySet().stream()
                    .filter(entry -> entry.getKey() != ResourceType.EMERALDS)
                    .mapToDouble(entry -> entry.getValue().stored()).sum();
            case EMERALD_STORAGE -> territory.resources().value().getOrDefault(ResourceType.EMERALDS, new ResourceBalance(0, 0, 0)).stored();
            default -> 0;
        };
    }

    private static List<ResourceType> targetResources(TerritoryState territory, UpgradeEffect effect) {
        if (effect == UpgradeEffect.EMERALD_PRODUCTION) return List.of(ResourceType.EMERALDS);
        if (effect != UpgradeEffect.RESOURCE_PRODUCTION || !territory.resources().isKnown()) return List.of();
        return territory.resources().value().keySet().stream()
                .filter(resource -> resource != ResourceType.EMERALDS)
                .sorted()
                .toList();
    }

    private static double generation(TerritoryState territory, ResourceType resource) {
        if (!territory.resources().isKnown()) return 0;
        return territory.resources().value().getOrDefault(resource, new ResourceBalance(0, 0, 0)).generationPerHour();
    }

    private static double deliveredFraction(EconomyResult economy, String territory, ResourceType resource) {
        double gross = economy.provenance().stream()
                .filter(value -> value.kind() == ProvenanceKind.PRODUCTION)
                .filter(value -> value.sourceTerritory().equals(territory) && value.resource() == resource)
                .mapToDouble(ResourceProvenance::sourceAmount).sum();
        if (gross <= EPSILON) return 0;
        double delivered = economy.provenance().stream()
                .filter(value -> value.kind() == ProvenanceKind.PRODUCTION)
                .filter(value -> value.sourceTerritory().equals(territory) && value.resource() == resource)
                .mapToDouble(ResourceProvenance::deliveredToHq).sum();
        return delivered / gross;
    }

    private static String effectUnit(UpgradeEffect effect) {
        return isStorage(effect) ? " capacity" : "/h";
    }

    private static String pretty(ResourceType resource) {
        String lower = resource.name().toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static <T> List<T> concat(List<T> left, List<T> right) {
        List<T> values = new ArrayList<>(left);
        values.addAll(right);
        return values.stream().distinct().toList();
    }

    private record SafeDowngrade(boolean safe, List<AuditCalculation> calculations, List<String> missingInputs) {}

    private record Draft(
            String rootCauseKey,
            AuditFindingType type,
            AuditSeverity severity,
            String title,
            String summary,
            List<AuditCalculation> calculations,
            List<String> routeFacts,
            List<String> territories,
            List<String> missingInputs,
            List<AuditProvenanceReference> provenance) {}

    private static final class MutableFinding {
        private final String root;
        private final Set<AuditFindingType> types = new LinkedHashSet<>();
        private AuditSeverity severity;
        private String title;
        private String summary;
        private final List<AuditCalculation> calculations = new ArrayList<>();
        private final Set<String> routeFacts = new LinkedHashSet<>();
        private final Set<String> territories = new LinkedHashSet<>();
        private final Set<String> missing = new LinkedHashSet<>();
        private final List<AuditProvenanceReference> provenance = new ArrayList<>();

        private MutableFinding(Draft first) {
            root = first.rootCauseKey();
            severity = first.severity();
            title = first.title();
            summary = first.summary();
        }

        private void merge(Draft value) {
            types.add(value.type());
            if (value.severity().ordinal() > severity.ordinal()) {
                severity = value.severity();
                title = value.title();
                summary = value.summary();
            }
            calculations.addAll(value.calculations());
            routeFacts.addAll(value.routeFacts());
            territories.addAll(value.territories());
            missing.addAll(value.missingInputs());
            provenance.addAll(value.provenance());
        }

        private AuditFinding freeze(AuditEvidenceSummary evidence) {
            return new AuditFinding(
                    root,
                    types,
                    severity,
                    title,
                    summary,
                    calculations.stream().distinct().toList(),
                    List.copyOf(routeFacts),
                    territories.stream().sorted().toList(),
                    evidence,
                    List.copyOf(missing),
                    provenance.stream().distinct().toList());
        }
    }
}
