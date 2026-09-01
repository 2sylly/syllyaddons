package net.syllyaddons.snapshot;

import java.util.List;
import java.util.Map;
import net.syllyaddons.domain.Evidence;
import net.syllyaddons.domain.EvidenceKind;
import net.syllyaddons.domain.GuildIdentity;
import net.syllyaddons.domain.ObservedState;
import net.syllyaddons.domain.ObservedValue;
import net.syllyaddons.domain.ResourceBalance;
import net.syllyaddons.domain.ResourceType;
import net.syllyaddons.domain.RoutingMode;
import net.syllyaddons.domain.TerritoryBounds;
import net.syllyaddons.domain.TerritoryOwner;
import net.syllyaddons.domain.TerritoryState;

final class SnapshotTestFixtures {
    private static final Evidence EVIDENCE =
            new Evidence(EvidenceKind.LOCAL_EXACT, 1_000, "fixture", "1", "test fixture");

    private SnapshotTestFixtures() {}

    static ObservedState state(long revision, long mineGeneration, long hqStored) {
        TerritoryOwner owner = new TerritoryOwner("guild-id", "Fixture Guild", "FIX");
        TerritoryState mine = territory(
                "Mine",
                owner,
                false,
                new TerritoryBounds(0, 0, 100, 100),
                List.of("HQ"),
                Map.of(ResourceType.ORE, new ResourceBalance(mineGeneration, 0, 100)));
        TerritoryState hq = territory(
                "HQ",
                owner,
                true,
                new TerritoryBounds(101, 0, 200, 100),
                List.of("Mine"),
                Map.of(ResourceType.ORE, new ResourceBalance(2, hqStored, 100)));
        return new ObservedState(
                ObservedState.CURRENT_SCHEMA_VERSION,
                revision,
                1_000,
                ObservedValue.unknown("not needed"),
                ObservedValue.known(new GuildIdentity("guild-id", "Fixture Guild", "FIX"), EVIDENCE),
                ObservedValue.known("HQ", EVIDENCE),
                ObservedValue.known(RoutingMode.CHEAPEST, EVIDENCE),
                Map.of("Mine", mine, "HQ", hq));
    }

    static SnapshotArchiveContent content(ObservedState state, long createdAt) {
        return new SnapshotCaptureService(new ObservedEconomyAnalyzer())
                .capture(
                        state,
                        createdAt,
                        Map.of(
                                "minecraft", "1.21.11",
                                "wynntils", "4.2.9",
                                "syllyaddons", "0.1.0-dev"));
    }

    private static TerritoryState territory(
            String name,
            TerritoryOwner owner,
            boolean headquarters,
            TerritoryBounds bounds,
            List<String> links,
            Map<ResourceType, ResourceBalance> resources) {
        return new TerritoryState(
                name,
                ObservedValue.known(owner, EVIDENCE),
                ObservedValue.unknown("not observed"),
                ObservedValue.known(headquarters, EVIDENCE),
                ObservedValue.known(bounds, EVIDENCE),
                ObservedValue.known(links, EVIDENCE),
                ObservedValue.known(resources, EVIDENCE),
                ObservedValue.unknown("not observed"),
                ObservedValue.unknown("not observed"),
                ObservedValue.unknown("not observed"),
                ObservedValue.known(Map.of(), EVIDENCE),
                ObservedValue.known(List.of(), EVIDENCE));
    }
}
