package net.syllyaddons.observation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.syllyaddons.domain.EvidenceKind;
import net.syllyaddons.domain.ObservedState;
import net.syllyaddons.domain.ObservedValue;

public final class DataHealthService {
    private final FreshnessPolicy freshnessPolicy;

    public DataHealthService(FreshnessPolicy freshnessPolicy) {
        this.freshnessPolicy = Objects.requireNonNull(freshnessPolicy, "freshnessPolicy");
    }

    public DataHealthReport assess(ObservedState state, long nowEpochMillis) {
        Objects.requireNonNull(state, "state");
        List<DataIssue> issues = new ArrayList<>();

        inspect(issues, DataGroup.CHARACTER, "session", "character", state.character(), nowEpochMillis);
        inspect(issues, DataGroup.GUILD, "session", "guild", state.guild(), nowEpochMillis);
        inspect(issues, DataGroup.HEADQUARTERS, "guild", "hqTerritory", state.hqTerritory(), nowEpochMillis);
        inspect(issues, DataGroup.ROUTING_MODE, "guild", "routingMode", state.routingMode(), nowEpochMillis);

        state.territories().forEach((name, territory) -> {
            inspect(issues, DataGroup.OWNERSHIP, name, "owner", territory.owner(), nowEpochMillis);
            inspect(
                    issues,
                    DataGroup.OWNERSHIP,
                    name,
                    "acquiredAtEpochMillis",
                    territory.acquiredAtEpochMillis(),
                    nowEpochMillis);
            inspect(issues, DataGroup.HEADQUARTERS, name, "headquarters", territory.headquarters(), nowEpochMillis);
            inspect(issues, DataGroup.TOPOLOGY, name, "bounds", territory.bounds(), nowEpochMillis);
            inspect(issues, DataGroup.TOPOLOGY, name, "links", territory.links(), nowEpochMillis);
            inspect(issues, DataGroup.PUBLIC_RESOURCES, name, "resources", territory.resources(), nowEpochMillis);
            inspect(issues, DataGroup.LOCAL_ECONOMY, name, "treasury", territory.treasury(), nowEpochMillis);
            inspect(
                    issues,
                    DataGroup.LOCAL_ECONOMY,
                    name,
                    "treasuryBonusPercent",
                    territory.treasuryBonusPercent(),
                    nowEpochMillis);
            inspect(issues, DataGroup.LOCAL_ECONOMY, name, "defences", territory.defences(), nowEpochMillis);
            inspect(issues, DataGroup.LOCAL_ECONOMY, name, "upgrades", territory.upgrades(), nowEpochMillis);
            inspect(issues, DataGroup.LOCAL_ECONOMY, name, "alerts", territory.alerts(), nowEpochMillis);
        });

        return new DataHealthReport(nowEpochMillis, state.revision(), issues);
    }

    private void inspect(
            List<DataIssue> issues,
            DataGroup group,
            String scope,
            String field,
            ObservedValue<?> value,
            long nowEpochMillis) {
        if (!value.isKnown()) {
            issues.add(new DataIssue(
                    DataIssueType.MISSING,
                    group,
                    scope,
                    field,
                    value.evidence().note(),
                    value.evidence()));
            return;
        }

        long age = Math.max(0, nowEpochMillis - value.evidence().observedAtEpochMillis());
        if (age > freshnessPolicy.maxAge(group).toMillis()) {
            issues.add(new DataIssue(
                    DataIssueType.STALE,
                    group,
                    scope,
                    field,
                    "Observed value is older than " + freshnessPolicy.maxAge(group),
                    value.evidence()));
        }

        if (value.evidence().kind() == EvidenceKind.ESTIMATED) {
            issues.add(new DataIssue(
                    DataIssueType.ESTIMATED,
                    group,
                    scope,
                    field,
                    value.evidence().note().isBlank() ? "Value is estimated" : value.evidence().note(),
                    value.evidence()));
        }
    }
}
