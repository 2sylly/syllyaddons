package net.syllyaddons.impact;

import java.util.Locale;
import net.syllyaddons.config.ImpactOverlayScope;
import net.syllyaddons.config.ImpactResourceFilter;
import net.syllyaddons.config.SyllyConfig;
import net.syllyaddons.domain.ObservedState;
import net.syllyaddons.domain.ResourceType;
import net.syllyaddons.domain.TerritoryOwner;
import net.syllyaddons.domain.TerritoryState;

/** Pure, allocation-light filter used before drawing cached impact regions. */
public final class ImpactOverlayFilter {
    private static final double EPSILON = 1.0e-9;

    public boolean matches(TerritoryImpactReport report, ObservedState state, SyllyConfig config) {
        if (!config.territoryImpactEnabled() || !config.impactOverlayEnabled()) return false;
        TerritoryState territory = state.territories().get(report.removedTerritory());
        if (territory == null || !territory.owner().isKnown()) return false;
        TerritoryOwner owner = territory.owner().value();
        if (!matchesScope(report, owner, config.impactOverlayScope(), config.impactSelectedEnemy())) return false;
        if (config.impactDisconnectionsOnly() && !hasDisconnection(report)) return false;
        if (!matchesResource(report, config.impactResourceFilter())) return false;
        return maximumDelay(report) >= config.impactMinimumDelaySeconds();
    }

    private static boolean matchesScope(
            TerritoryImpactReport report,
            TerritoryOwner owner,
            ImpactOverlayScope scope,
            String selectedEnemy) {
        return switch (scope) {
            case OWN_GUILD -> report.ownerRelation() == OwnerRelation.OWN_GUILD;
            case VISIBLE_GUILDS -> owner.isOwned();
            case SELECTED_ENEMY -> report.ownerRelation() == OwnerRelation.FOREIGN_GUILD
                    && matchesOwner(owner, selectedEnemy);
        };
    }

    private static boolean matchesOwner(TerritoryOwner owner, String selection) {
        String selected = selection == null ? "" : selection.strip().toLowerCase(Locale.ROOT);
        if (selected.isEmpty()) return false;
        return owner.guildUuid().toLowerCase(Locale.ROOT).equals(selected)
                || owner.guildName().toLowerCase(Locale.ROOT).equals(selected)
                || owner.guildPrefix().toLowerCase(Locale.ROOT).equals(selected);
    }

    public static boolean hasDisconnection(TerritoryImpactReport report) {
        return report.modes().values().stream()
                .flatMap(mode -> mode.routeImpacts().stream())
                .anyMatch(route -> route.changes().contains(RouteChangeKind.DISCONNECTED));
    }

    private static boolean matchesResource(TerritoryImpactReport report, ImpactResourceFilter filter) {
        if (filter == ImpactResourceFilter.ALL) return true;
        ResourceType resource = filter.resource().orElseThrow();
        return report.modes().values().stream()
                .map(mode -> mode.resourceDeltas().get(resource))
                .anyMatch(ImpactOverlayFilter::changed);
    }

    private static boolean changed(ResourceImpactDelta delta) {
        return delta != null && (Math.abs(delta.deliveredDeltaPerHour()) > EPSILON
                || Math.abs(delta.taxLossDeltaPerHour()) > EPSILON
                || Math.abs(delta.towerSupplyDeltaPerHour()) > EPSILON
                || Math.abs(delta.deficitDeltaPerHour()) > EPSILON
                || Math.abs(delta.endingStorageDelta()) > EPSILON);
    }

    public static long maximumDelay(TerritoryImpactReport report) {
        return report.modes().values().stream()
                .flatMap(mode -> mode.routeImpacts().stream())
                .mapToLong(route -> Math.max(0, route.deliveryDeltaSeconds()))
                .max()
                .orElse(0);
    }
}
