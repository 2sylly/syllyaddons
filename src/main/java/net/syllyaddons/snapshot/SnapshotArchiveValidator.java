package net.syllyaddons.snapshot;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.syllyaddons.domain.EcoSnapshot;
import net.syllyaddons.domain.Evidence;
import net.syllyaddons.domain.ObservedState;
import net.syllyaddons.domain.ObservedValue;
import net.syllyaddons.domain.ResourceBalance;
import net.syllyaddons.domain.ResourceType;
import net.syllyaddons.domain.TerritoryBounds;
import net.syllyaddons.domain.TerritoryState;
import net.syllyaddons.economy.EconomyResult;
import net.syllyaddons.economy.ResourceDeficit;
import net.syllyaddons.economy.ResourceEconomySummary;
import net.syllyaddons.economy.ResourceProvenance;
import net.syllyaddons.economy.SpendingAllocation;
import net.syllyaddons.economy.TaxLedgerStep;
import net.syllyaddons.routing.RouteDiagnostic;

public final class SnapshotArchiveValidator {
    public static final int MAX_FILE_BYTES = 16 * 1024 * 1024;
    private static final int MAX_TERRITORIES = 1_000;
    private static final int MAX_LINKS_PER_TERRITORY = 64;
    private static final int MAX_ROUTE_LENGTH = 1_000;
    private static final int MAX_PROVENANCE_LOTS = 10_000;
    private static final int MAX_DIAGNOSTICS = 10_000;
    private static final int MAX_TEXT = 1_024;
    private static final double MAX_AMOUNT = 1.0e15;
    private static final long MAX_WORLD_COORDINATE = 30_000_000L;
    private static final long MAX_REASONABLE_EPOCH_MILLIS = 4_102_444_800_000L; // 2100-01-01 UTC

    public void validate(SnapshotArchiveContent content) throws SnapshotFormatException {
        if (content == null) fail("Snapshot content is missing");
        if (content.formatVersion() != SnapshotArchiveContent.CURRENT_FORMAT_VERSION) {
            fail("Unsupported .tnsreco format version " + content.formatVersion());
        }
        if (content.createdAtEpochMillis() < 0 || content.createdAtEpochMillis() > MAX_REASONABLE_EPOCH_MILLIS) {
            fail("Snapshot creation time is outside the supported range");
        }
        if (content.sourceVersions() == null || content.sourceVersions().isEmpty()) {
            fail("Snapshot source versions are missing");
        }
        if (content.sourceVersions().size() > 32) fail("Snapshot has too many source-version entries");
        for (Map.Entry<String, String> source : content.sourceVersions().entrySet()) {
            requireText(source.getKey(), "source-version name", 64);
            requireText(source.getValue(), "source-version value", 128);
        }
        if (content.payload() == null) fail("Snapshot payload is missing");
        validateObserved(content.payload().observed());
        validateDiagnostics(content.payload().analysisDiagnostics(), "analysis diagnostics");
        if (content.payload().economy() != null) {
            validateEconomy(content.payload().economy(), content.payload().observed());
        }
    }

    private static void validateObserved(EcoSnapshot snapshot) throws SnapshotFormatException {
        if (snapshot == null) fail("Observed snapshot is missing");
        if (snapshot.schemaVersion() != ObservedState.CURRENT_SCHEMA_VERSION) {
            fail("Unsupported observed-state schema " + snapshot.schemaVersion());
        }
        if (snapshot.createdAtEpochMillis() < 0 || snapshot.createdAtEpochMillis() > MAX_REASONABLE_EPOCH_MILLIS) {
            fail("Observed snapshot time is outside the supported range");
        }
        validateObservedValue(snapshot.guild(), "guild");
        validateObservedValue(snapshot.hqTerritory(), "HQ territory");
        validateObservedValue(snapshot.routingMode(), "routing mode");
        if (snapshot.guild().isKnown()) {
            requireOptionalText(snapshot.guild().value().uuid(), "guild UUID", 128);
            requireText(snapshot.guild().value().name(), "guild name", 128);
            requireOptionalText(snapshot.guild().value().prefix(), "guild prefix", 32);
        }
        if (snapshot.hqTerritory().isKnown()) requireText(snapshot.hqTerritory().value(), "HQ territory", 128);
        Map<String, TerritoryState> territories = snapshot.territories();
        if (territories == null) fail("Territory map is missing");
        if (territories.size() > MAX_TERRITORIES) fail("Snapshot has too many territories");

        for (Map.Entry<String, TerritoryState> entry : territories.entrySet()) {
            String name = entry.getKey();
            requireText(name, "territory name", 128);
            TerritoryState territory = entry.getValue();
            if (territory == null) fail("Territory " + name + " is null");
            if (!name.equals(territory.name())) fail("Territory map key does not match " + territory.name());
            validateTerritory(territory, territories.keySet());
        }
        if (snapshot.hqTerritory().isKnown() && !territories.containsKey(snapshot.hqTerritory().value())) {
            fail("HQ territory is absent from the territory map");
        }
    }

    private static void validateTerritory(TerritoryState territory, Set<String> knownTerritories)
            throws SnapshotFormatException {
        validateObservedValue(territory.owner(), territory.name() + " owner");
        validateObservedValue(territory.acquiredAtEpochMillis(), territory.name() + " acquired time");
        validateObservedValue(territory.headquarters(), territory.name() + " HQ flag");
        validateObservedValue(territory.bounds(), territory.name() + " bounds");
        validateObservedValue(territory.links(), territory.name() + " links");
        validateObservedValue(territory.resources(), territory.name() + " resources");
        validateObservedValue(territory.treasury(), territory.name() + " treasury");
        validateObservedValue(territory.treasuryBonusPercent(), territory.name() + " treasury bonus");
        validateObservedValue(territory.defences(), territory.name() + " defences");
        validateObservedValue(territory.upgrades(), territory.name() + " upgrades");
        validateObservedValue(territory.alerts(), territory.name() + " alerts");

        if (territory.owner().isKnown()) {
            requireOptionalText(territory.owner().value().guildUuid(), territory.name() + " owner UUID", 128);
            requireOptionalText(territory.owner().value().guildName(), territory.name() + " owner name", 128);
            requireOptionalText(territory.owner().value().guildPrefix(), territory.name() + " owner prefix", 32);
        }
        if (territory.acquiredAtEpochMillis().isKnown()) {
            long acquired = territory.acquiredAtEpochMillis().value();
            if (acquired < 0 || acquired > MAX_REASONABLE_EPOCH_MILLIS) {
                fail(territory.name() + " acquisition time is outside the supported range");
            }
        }

        if (territory.bounds().isKnown()) validateBounds(territory.name(), territory.bounds().value());
        if (territory.links().isKnown()) {
            List<String> links = territory.links().value();
            if (links.size() > MAX_LINKS_PER_TERRITORY) fail(territory.name() + " has too many links");
            Set<String> seen = new HashSet<>();
            for (String link : links) {
                requireText(link, territory.name() + " link", 128);
                if (!knownTerritories.contains(link)) fail(territory.name() + " links to unknown territory " + link);
                if (territory.name().equals(link)) fail(territory.name() + " contains a self-link");
                if (!seen.add(link)) fail(territory.name() + " contains duplicate link " + link);
            }
        }
        if (territory.resources().isKnown()) {
            if (territory.resources().value().size() > ResourceType.values().length) {
                fail(territory.name() + " has too many resource entries");
            }
            for (Map.Entry<ResourceType, ResourceBalance> resource : territory.resources().value().entrySet()) {
                if (resource.getKey() == null || resource.getValue() == null) {
                    fail(territory.name() + " has a null resource entry");
                }
                ResourceBalance balance = resource.getValue();
                validateAmount(balance.generationPerHour(), territory.name() + " generation");
                validateAmount(balance.stored(), territory.name() + " stored resources");
                validateAmount(balance.storageLimit(), territory.name() + " storage limit");
            }
        }
        if (territory.treasuryBonusPercent().isKnown()) {
            validateAmount(territory.treasuryBonusPercent().value(), territory.name() + " treasury bonus");
        }
        if (territory.upgrades().isKnown()) {
            if (territory.upgrades().value().size() > 256) fail(territory.name() + " has too many upgrades");
            for (Map.Entry<String, Integer> upgrade : territory.upgrades().value().entrySet()) {
                requireText(upgrade.getKey(), territory.name() + " upgrade name", 128);
                if (upgrade.getValue() == null || upgrade.getValue() < 0 || upgrade.getValue() > 10_000) {
                    fail(territory.name() + " has an invalid upgrade level");
                }
            }
        }
        if (territory.alerts().isKnown()) {
            if (territory.alerts().value().size() > 256) fail(territory.name() + " has too many alerts");
            for (String alert : territory.alerts().value()) requireText(alert, territory.name() + " alert", MAX_TEXT);
        }
    }

    private static void validateEconomy(EconomyResult economy, EcoSnapshot observed) throws SnapshotFormatException {
        requireText(economy.economyRulesVersion(), "economy rules version", 128);
        requireText(economy.routingRulesVersion(), "routing rules version", 128);
        if (economy.confidence() == null) fail("Economy confidence is missing");
        if (economy.summaries() == null || economy.summaries().size() > ResourceType.values().length) {
            fail("Economy resource summaries are invalid");
        }
        for (Map.Entry<ResourceType, ResourceEconomySummary> entry : economy.summaries().entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue().resource() != entry.getKey()) {
                fail("Economy summary resource key does not match its value");
            }
            validateSummary(entry.getValue());
        }
        if (economy.provenance() == null || economy.provenance().size() > MAX_PROVENANCE_LOTS) {
            fail("Economy provenance has too many entries");
        }
        for (ResourceProvenance provenance : economy.provenance()) validateProvenance(provenance, observed);
        if (economy.deficits() == null || economy.deficits().size() > MAX_PROVENANCE_LOTS) {
            fail("Economy deficits have too many entries");
        }
        for (ResourceDeficit deficit : economy.deficits()) {
            if (deficit == null || deficit.resource() == null) fail("Economy deficit is invalid");
            requireKnownTerritory(deficit.consumerTerritory(), observed, "deficit consumer");
            validateAmount(deficit.required(), "deficit required");
            validateAmount(deficit.supplied(), "deficit supplied");
            validateAmount(deficit.unmet(), "deficit unmet");
        }
        validateDiagnostics(economy.diagnostics(), "economy diagnostics");
    }

    private static void validateSummary(ResourceEconomySummary summary) throws SnapshotFormatException {
        if (summary.resource() == null) fail("Economy summary resource is missing");
        validateAmount(summary.openingStorage(), "opening storage");
        validateAmount(summary.grossProduction(), "gross production");
        validateAmount(summary.taxLoss(), "tax loss");
        validateAmount(summary.deliveredProduction(), "delivered production");
        validateAmount(summary.expenses(), "expenses");
        validateAmount(summary.spent(), "spent resources");
        validateAmount(summary.deficit(), "resource deficit");
        validateAmount(summary.endingStorage(), "ending storage");
        validateAmount(summary.overflowLoss(), "overflow loss");
        validateAmount(summary.undeliveredProduction(), "undelivered production");
    }

    private static void validateProvenance(ResourceProvenance provenance, EcoSnapshot observed)
            throws SnapshotFormatException {
        if (provenance == null || provenance.kind() == null || provenance.resource() == null
                || provenance.confidence() == null) {
            fail("Economy provenance entry is incomplete");
        }
        requireKnownTerritory(provenance.sourceTerritory(), observed, "provenance source");
        validateAmount(provenance.sourceAmount(), "provenance source amount");
        validateAmount(provenance.taxLoss(), "provenance tax loss");
        validateAmount(provenance.deliveredToHq(), "provenance delivery");
        if (provenance.deliverySeconds() < 0 || provenance.deliverySeconds() > 604_800) {
            fail("provenance delivery time is outside the supported range");
        }
        validateAmount(provenance.storedAtHq(), "provenance stored amount");
        validateAmount(provenance.overflowLoss(), "provenance overflow");
        validateAmount(provenance.undelivered(), "provenance undelivered amount");
        if (provenance.route() == null || provenance.route().size() > MAX_ROUTE_LENGTH) {
            fail("Provenance route is too long");
        }
        for (String routeTerritory : provenance.route()) {
            requireKnownTerritory(routeTerritory, observed, "route territory");
        }
        if (!provenance.route().isEmpty() && !provenance.route().getFirst().equals(provenance.sourceTerritory())) {
            fail("Provenance route does not start at its source");
        }
        if (!provenance.route().isEmpty()
                && observed.hqTerritory().isKnown()
                && !provenance.route().getLast().equals(observed.hqTerritory().value())) {
            fail("Provenance route does not end at the observed HQ");
        }
        if (provenance.taxSteps() == null
                || provenance.taxSteps().size() != Math.max(0, provenance.route().size() - 1)) {
            fail("Provenance tax-step count does not match its route");
        }
        for (int index = 0; index < provenance.taxSteps().size(); index++) {
            TaxLedgerStep step = provenance.taxSteps().get(index);
            if (step == null || step.confidence() == null) fail("Provenance tax step is incomplete");
            if (!step.from().equals(provenance.route().get(index))
                    || !step.to().equals(provenance.route().get(index + 1))) {
                fail("Provenance tax step does not match its route");
            }
            validateAmount(step.amountBefore(), "tax amount before");
            validateRate(step.taxRate(), "tax rate");
            validateAmount(step.taxLoss(), "tax loss");
            validateAmount(step.amountAfter(), "tax amount after");
        }
        if (provenance.spending() == null || provenance.spending().size() > MAX_PROVENANCE_LOTS) {
            fail("Provenance has too many spending entries");
        }
        for (SpendingAllocation allocation : provenance.spending()) {
            if (allocation == null) fail("Provenance spending entry is null");
            requireKnownTerritory(allocation.consumerTerritory(), observed, "spending consumer");
            validateAmount(allocation.amount(), "spending amount");
        }
        validateDiagnostics(provenance.diagnostics(), "provenance diagnostics");
    }

    private static void validateBounds(String territory, TerritoryBounds bounds) throws SnapshotFormatException {
        if (bounds == null) fail(territory + " bounds are null");
        if (bounds.minX() > bounds.maxX() || bounds.minZ() > bounds.maxZ()) fail(territory + " bounds are inverted");
        if (Math.abs((long) bounds.minX()) > MAX_WORLD_COORDINATE
                || Math.abs((long) bounds.maxX()) > MAX_WORLD_COORDINATE
                || Math.abs((long) bounds.minZ()) > MAX_WORLD_COORDINATE
                || Math.abs((long) bounds.maxZ()) > MAX_WORLD_COORDINATE) {
            fail(territory + " bounds exceed the supported world range");
        }
    }

    private static void validateObservedValue(ObservedValue<?> value, String field) throws SnapshotFormatException {
        if (value == null || value.evidence() == null) fail(field + " observation is missing");
        Evidence evidence = value.evidence();
        if (evidence.kind() == null) fail(field + " evidence kind is missing");
        if (evidence.observedAtEpochMillis() < 0 || evidence.observedAtEpochMillis() > MAX_REASONABLE_EPOCH_MILLIS) {
            fail(field + " evidence time is outside the supported range");
        }
        requireText(evidence.source(), field + " evidence source", 256);
        requireText(evidence.sourceVersion(), field + " evidence version", 128);
        if (evidence.note() == null || evidence.note().length() > MAX_TEXT) fail(field + " evidence note is too long");
        if (value.isKnown() == (evidence.kind() == net.syllyaddons.domain.EvidenceKind.UNKNOWN)) {
            fail(field + " value and evidence kind disagree");
        }
    }

    private static void validateDiagnostics(List<RouteDiagnostic> diagnostics, String field)
            throws SnapshotFormatException {
        if (diagnostics == null || diagnostics.size() > MAX_DIAGNOSTICS) fail(field + " are invalid");
        for (RouteDiagnostic diagnostic : diagnostics) {
            if (diagnostic == null) fail(field + " contain null");
            requireText(diagnostic.code(), field + " code", 128);
            requireText(diagnostic.message(), field + " message", MAX_TEXT);
        }
    }

    private static void requireKnownTerritory(String name, EcoSnapshot observed, String field)
            throws SnapshotFormatException {
        requireText(name, field, 128);
        if (!observed.territories().containsKey(name)) fail(field + " references unknown territory " + name);
    }

    private static void requireText(String value, String field, int maximumLength) throws SnapshotFormatException {
        if (value == null || value.isBlank()) fail(field + " is blank");
        if (value.length() > maximumLength) fail(field + " is too long");
    }

    private static void requireOptionalText(String value, String field, int maximumLength)
            throws SnapshotFormatException {
        if (value == null) fail(field + " is null");
        if (value.length() > maximumLength) fail(field + " is too long");
    }

    private static void validateRate(double value, String field) throws SnapshotFormatException {
        if (!Double.isFinite(value) || value < 0 || value > 1) fail(field + " must be between 0 and 1");
    }

    private static void validateAmount(double value, String field) throws SnapshotFormatException {
        if (!Double.isFinite(value) || value < 0 || value > MAX_AMOUNT) fail(field + " is outside the supported range");
    }

    private static void fail(String message) throws SnapshotFormatException {
        throw new SnapshotFormatException(message);
    }
}
