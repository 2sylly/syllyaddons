package net.syllyaddons.impact;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.syllyaddons.domain.EcoSnapshot;
import net.syllyaddons.domain.Evidence;
import net.syllyaddons.domain.EvidenceKind;
import net.syllyaddons.domain.ObservedState;
import net.syllyaddons.domain.ObservedValue;
import net.syllyaddons.domain.RoutingMode;
import net.syllyaddons.domain.TerritoryState;
import net.syllyaddons.routing.ObservedTerritoryGraphFactory;

final class ImpactStateFactory {
    private final ObservedTerritoryGraphFactory graphFactory = new ObservedTerritoryGraphFactory();

    ImpactSimulationState create(ObservedState source, RoutingMode mode, String removedTerritory, long nowEpochMillis) {
        ObservedState baseline = withMode(source, mode, nowEpochMillis);
        ObservedState simulated = remove(baseline, removedTerritory);
        return new ImpactSimulationState(
                baseline,
                simulated,
                graphFactory.create(EcoSnapshot.from(baseline, nowEpochMillis)),
                graphFactory.create(EcoSnapshot.from(simulated, nowEpochMillis)),
                removedTerritory);
    }

    ObservedState withMode(ObservedState source, RoutingMode mode, long nowEpochMillis) {
        ObservedValue<RoutingMode> value = source.routingMode().isKnown() && source.routingMode().value() == mode
                ? source.routingMode()
                : ObservedValue.known(
                        mode,
                        new Evidence(
                                EvidenceKind.DERIVED,
                                nowEpochMillis,
                                "territory-impact-simulator",
                                "track-7-v1",
                                "Routing mode branch used for a read-only simulation"));
        return new ObservedState(
                source.schemaVersion(),
                source.revision(),
                source.assembledAtEpochMillis(),
                source.character(),
                source.guild(),
                source.hqTerritory(),
                value,
                source.territories());
    }

    private static ObservedState remove(ObservedState source, String removedTerritory) {
        if (!source.territories().containsKey(removedTerritory)) {
            throw new IllegalArgumentException("Unknown removal target " + removedTerritory);
        }
        Map<String, TerritoryState> territories = new LinkedHashMap<>();
        source.territories().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .filter(entry -> !entry.getKey().equals(removedTerritory))
                .forEach(entry -> territories.put(entry.getKey(), withoutLink(entry.getValue(), removedTerritory)));
        return new ObservedState(
                source.schemaVersion(),
                source.revision(),
                source.assembledAtEpochMillis(),
                source.character(),
                source.guild(),
                source.hqTerritory(),
                source.routingMode(),
                Map.copyOf(territories));
    }

    private static TerritoryState withoutLink(TerritoryState territory, String removedTerritory) {
        if (!territory.links().isKnown() || !territory.links().value().contains(removedTerritory)) return territory;
        List<String> links = territory.links().value().stream()
                .filter(link -> !link.equals(removedTerritory))
                .toList();
        return new TerritoryState(
                territory.name(),
                territory.owner(),
                territory.acquiredAtEpochMillis(),
                territory.headquarters(),
                territory.bounds(),
                ObservedValue.known(links, territory.links().evidence()),
                territory.resources(),
                territory.treasury(),
                territory.treasuryBonusPercent(),
                territory.defences(),
                territory.upgrades(),
                territory.alerts());
    }
}
