package net.syllyaddons.economy;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.syllyaddons.domain.ResourceType;
import net.syllyaddons.routing.RouteDiagnostic;
import net.syllyaddons.routing.RouteEngine;
import net.syllyaddons.routing.RouteResult;
import net.syllyaddons.routing.RouteStep;
import net.syllyaddons.routing.RuleConfidence;

/** Pure calculation engine. It never reads Minecraft state or performs guild actions. */
public final class EconomyEngine {
    private static final double EPSILON = 1.0e-9;
    private final RouteEngine routeEngine;

    public EconomyEngine() {
        this(new RouteEngine());
    }

    public EconomyEngine(RouteEngine routeEngine) {
        this.routeEngine = Objects.requireNonNull(routeEngine, "routeEngine");
    }

    public EconomyResult calculate(EconomyInput input) {
        Objects.requireNonNull(input, "input");
        if (input.graph().node(input.headquarters()) == null) {
            return unavailable(input, "UNKNOWN_HQ", "Headquarters is absent from the territory graph");
        }

        List<MutableLot> lots = new ArrayList<>();
        List<ResourceDeficit> deficits = new ArrayList<>();
        List<RouteDiagnostic> diagnostics = new ArrayList<>();
        RuleConfidence confidence = input.economyRules().confidence();

        if (input.economyRules().openingStorageSpentFirst()) {
            addOpeningStorage(input, lots);
        }

        for (TerritoryEconomyInput territory : input.territories()) {
            if (input.graph().node(territory.territory()) == null) {
                diagnostics.add(new RouteDiagnostic(
                        "UNKNOWN_ECONOMY_TERRITORY",
                        "Economy input references absent territory " + territory.territory()));
                confidence = RuleConfidence.UNKNOWN;
                continue;
            }
            for (Map.Entry<ResourceType, Double> production : territory.productionPerHour().entrySet()) {
                RouteResult route = routeEngine.find(
                        input.graph(),
                        territory.territory(),
                        input.headquarters(),
                        input.routingMode(),
                        input.taxPolicy(),
                        input.routingRules());
                MutableLot lot = productionLot(
                        territory.territory(),
                        production.getKey(),
                        production.getValue(),
                        route,
                        input.economyRules());
                lots.add(lot);
                confidence = RuleConfidence.weakest(confidence, lot.confidence);
                diagnostics.addAll(route.diagnostics());
            }
        }

        if (!input.economyRules().openingStorageSpentFirst()) {
            addOpeningStorage(input, lots);
        }

        Map<ResourceType, Double> expenseTotals = new EnumMap<>(ResourceType.class);
        for (TerritoryEconomyInput territory : input.territories()) {
            for (Map.Entry<ResourceType, Double> expense : territory.expensesPerHour().entrySet()) {
                expenseTotals.merge(expense.getKey(), expense.getValue(), Double::sum);
                double remaining = expense.getValue();
                for (MutableLot lot : lots) {
                    if (lot.resource != expense.getKey() || remaining <= EPSILON) continue;
                    double allocated = Math.min(lot.available, remaining);
                    if (allocated <= EPSILON) continue;
                    lot.available -= allocated;
                    remaining -= allocated;
                    lot.spending.add(new SpendingAllocation(territory.territory(), allocated));
                }
                if (remaining > EPSILON) {
                    deficits.add(new ResourceDeficit(
                            territory.territory(),
                            expense.getKey(),
                            expense.getValue(),
                            expense.getValue() - remaining,
                            remaining));
                }
            }
        }

        for (ResourceType resource : ResourceType.values()) {
            double remainingCapacity = input.hqStorageLimits().getOrDefault(resource, 0.0);
            for (MutableLot lot : lots) {
                if (lot.resource != resource || lot.available <= EPSILON) continue;
                lot.stored = Math.min(lot.available, remainingCapacity);
                remainingCapacity -= lot.stored;
                lot.overflow = lot.available - lot.stored;
                lot.available = 0;
            }
        }

        Map<ResourceType, ResourceEconomySummary> summaries = summarize(lots, expenseTotals, deficits);
        List<ResourceProvenance> provenance = lots.stream().map(MutableLot::freeze).toList();
        if (!input.economyRules().confidence().isExact()) {
            diagnostics.add(new RouteDiagnostic("UNVALIDATED_ECONOMY_RULES", input.economyRules().basis()));
        }
        List<RouteDiagnostic> uniqueDiagnostics = diagnostics.stream().distinct().toList();
        return new EconomyResult(
                input.economyRules().version(),
                input.routingRules().version(),
                summaries,
                provenance,
                deficits,
                confidence,
                uniqueDiagnostics);
    }

    private static void addOpeningStorage(EconomyInput input, List<MutableLot> lots) {
        input.openingHqStorage().forEach((resource, amount) -> {
            if (amount > 0) {
                lots.add(MutableLot.openingStorage(
                        input.headquarters(), resource, amount, input.economyRules().confidence()));
            }
        });
    }

    private static MutableLot productionLot(
            String source,
            ResourceType resource,
            double amount,
            RouteResult route,
            EconomyRules rules) {
        if (!route.found()) {
            return MutableLot.undelivered(source, resource, amount, route.confidence(), route.diagnostics());
        }
        double current = amount;
        List<TaxLedgerStep> taxes = new ArrayList<>();
        for (RouteStep step : route.steps()) {
            double taxableBase = rules.taxesCompoundPerRouteStep() ? current : amount;
            double loss = Math.min(current, taxableBase * step.taxRate());
            double after = current - loss;
            taxes.add(new TaxLedgerStep(
                    step.from(), step.to(), current, step.taxRate(), loss, after, step.taxConfidence()));
            current = after;
        }
        return MutableLot.production(
                source,
                resource,
                amount,
                route.path(),
                taxes,
                current,
                route.deliverySeconds(),
                route.confidence(),
                route.diagnostics());
    }

    private static Map<ResourceType, ResourceEconomySummary> summarize(
            List<MutableLot> lots,
            Map<ResourceType, Double> expenseTotals,
            List<ResourceDeficit> deficits) {
        Map<ResourceType, ResourceEconomySummary> result = new EnumMap<>(ResourceType.class);
        for (ResourceType resource : ResourceType.values()) {
            double opening = lots.stream()
                    .filter(lot -> lot.kind == ProvenanceKind.OPENING_HQ_STORAGE && lot.resource == resource)
                    .mapToDouble(lot -> lot.sourceAmount)
                    .sum();
            double gross = lots.stream()
                    .filter(lot -> lot.kind == ProvenanceKind.PRODUCTION && lot.resource == resource)
                    .mapToDouble(lot -> lot.sourceAmount)
                    .sum();
            double delivered = lots.stream()
                    .filter(lot -> lot.kind == ProvenanceKind.PRODUCTION && lot.resource == resource)
                    .mapToDouble(lot -> lot.delivered)
                    .sum();
            double undelivered = lots.stream()
                    .filter(lot -> lot.resource == resource)
                    .mapToDouble(lot -> lot.undelivered)
                    .sum();
            double taxLoss = lots.stream()
                    .filter(lot -> lot.resource == resource)
                    .flatMap(lot -> lot.taxSteps.stream())
                    .mapToDouble(TaxLedgerStep::taxLoss)
                    .sum();
            double spent = lots.stream()
                    .filter(lot -> lot.resource == resource)
                    .flatMap(lot -> lot.spending.stream())
                    .mapToDouble(SpendingAllocation::amount)
                    .sum();
            double stored = lots.stream()
                    .filter(lot -> lot.resource == resource)
                    .mapToDouble(lot -> lot.stored)
                    .sum();
            double overflow = lots.stream()
                    .filter(lot -> lot.resource == resource)
                    .mapToDouble(lot -> lot.overflow)
                    .sum();
            double deficit = deficits.stream()
                    .filter(value -> value.resource() == resource)
                    .mapToDouble(ResourceDeficit::unmet)
                    .sum();
            result.put(resource, new ResourceEconomySummary(
                    resource,
                    opening,
                    gross,
                    taxLoss,
                    delivered,
                    expenseTotals.getOrDefault(resource, 0.0),
                    spent,
                    deficit,
                    stored,
                    overflow,
                    undelivered));
        }
        return result;
    }

    private static EconomyResult unavailable(EconomyInput input, String code, String message) {
        return new EconomyResult(
                input.economyRules().version(),
                input.routingRules().version(),
                Map.of(),
                List.of(),
                List.of(),
                RuleConfidence.UNKNOWN,
                List.of(new RouteDiagnostic(code, message)));
    }

    private static final class MutableLot {
        private final ProvenanceKind kind;
        private final String source;
        private final ResourceType resource;
        private final double sourceAmount;
        private final List<String> route;
        private final List<TaxLedgerStep> taxSteps;
        private final double delivered;
        private final long deliverySeconds;
        private final double undelivered;
        private final RuleConfidence confidence;
        private final List<RouteDiagnostic> diagnostics;
        private final List<SpendingAllocation> spending = new ArrayList<>();
        private double available;
        private double stored;
        private double overflow;

        private MutableLot(
                ProvenanceKind kind,
                String source,
                ResourceType resource,
                double sourceAmount,
                List<String> route,
                List<TaxLedgerStep> taxSteps,
                double delivered,
                long deliverySeconds,
                double undelivered,
                RuleConfidence confidence,
                List<RouteDiagnostic> diagnostics) {
            this.kind = kind;
            this.source = source;
            this.resource = resource;
            this.sourceAmount = sourceAmount;
            this.route = List.copyOf(route);
            this.taxSteps = List.copyOf(taxSteps);
            this.delivered = delivered;
            this.deliverySeconds = deliverySeconds;
            this.undelivered = undelivered;
            this.confidence = confidence;
            this.diagnostics = List.copyOf(diagnostics);
            this.available = delivered;
        }

        private static MutableLot openingStorage(
                String hq, ResourceType resource, double amount, RuleConfidence confidence) {
            return new MutableLot(
                    ProvenanceKind.OPENING_HQ_STORAGE,
                    hq,
                    resource,
                    amount,
                    List.of(hq),
                    List.of(),
                    amount,
                    0,
                    0,
                    confidence,
                    List.of());
        }

        private static MutableLot production(
                String source,
                ResourceType resource,
                double amount,
                List<String> route,
                List<TaxLedgerStep> taxes,
                double delivered,
                long deliverySeconds,
                RuleConfidence confidence,
                List<RouteDiagnostic> diagnostics) {
            return new MutableLot(
                    ProvenanceKind.PRODUCTION,
                    source,
                    resource,
                    amount,
                    route,
                    taxes,
                    delivered,
                    deliverySeconds,
                    0,
                    confidence,
                    diagnostics);
        }

        private static MutableLot undelivered(
                String source,
                ResourceType resource,
                double amount,
                RuleConfidence confidence,
                List<RouteDiagnostic> diagnostics) {
            return new MutableLot(
                    ProvenanceKind.PRODUCTION,
                    source,
                    resource,
                    amount,
                    List.of(),
                    List.of(),
                    0,
                    0,
                    amount,
                    confidence,
                    diagnostics);
        }

        private ResourceProvenance freeze() {
            double taxLoss = taxSteps.stream().mapToDouble(TaxLedgerStep::taxLoss).sum();
            return new ResourceProvenance(
                    kind,
                    source,
                    resource,
                    sourceAmount,
                    route,
                    taxSteps,
                    taxLoss,
                    delivered,
                    deliverySeconds,
                    spending,
                    stored,
                    overflow,
                    undelivered,
                    confidence,
                    diagnostics);
        }
    }
}
