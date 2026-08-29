package net.syllyaddons.routing;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.syllyaddons.domain.EcoSnapshot;
import net.syllyaddons.domain.TerritoryOwner;
import net.syllyaddons.domain.TerritoryState;

public final class ObservedTerritoryGraphFactory {
    public TerritoryGraph create(EcoSnapshot snapshot) {
        List<TerritoryNode> nodes = new ArrayList<>();
        snapshot.territories().entrySet().stream()
                .sorted(Comparator.comparing(java.util.Map.Entry::getKey))
                .forEach(entry -> nodes.add(fromState(entry.getValue())));
        return TerritoryGraph.from(nodes);
    }

    private static TerritoryNode fromState(TerritoryState state) {
        TerritoryOwner owner = state.owner().isKnown() ? state.owner().value() : null;
        String ownerId = "";
        if (owner != null) {
            ownerId = owner.guildUuid().isBlank() ? owner.guildName() : owner.guildUuid();
        }
        return new TerritoryNode(
                state.name(),
                ownerId,
                state.bounds().isKnown() ? state.bounds().value() : null,
                state.links().isKnown() ? state.links().value() : List.of(),
                state.links().isKnown());
    }
}
