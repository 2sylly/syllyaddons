package net.syllyaddons.config;

import java.util.Objects;

public record SyllyConfig(
        int schemaVersion,
        boolean ecoAuditorEnabled,
        int ecoWarningCooldownSeconds,
        boolean territoryImpactEnabled,
        boolean routingAdvisorEnabled,
        boolean optimizerEnabled,
        boolean automaticSnapshotsEnabled,
        int snapshotRetention,
        boolean profileSwapNotifications,
        boolean configurationWarnings) {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final int MIN_ECO_WARNING_COOLDOWN_SECONDS = 5;
    public static final int MAX_ECO_WARNING_COOLDOWN_SECONDS = 600;
    public static final int MIN_SNAPSHOT_RETENTION = 1;
    public static final int MAX_SNAPSHOT_RETENTION = 250;

    public SyllyConfig {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported Sylly Addons settings schema " + schemaVersion);
        }
        if (ecoWarningCooldownSeconds < MIN_ECO_WARNING_COOLDOWN_SECONDS
                || ecoWarningCooldownSeconds > MAX_ECO_WARNING_COOLDOWN_SECONDS) {
            throw new IllegalArgumentException("Eco warning cooldown must be between "
                    + MIN_ECO_WARNING_COOLDOWN_SECONDS + " and " + MAX_ECO_WARNING_COOLDOWN_SECONDS + " seconds");
        }
        if (snapshotRetention < MIN_SNAPSHOT_RETENTION || snapshotRetention > MAX_SNAPSHOT_RETENTION) {
            throw new IllegalArgumentException("Snapshot retention must be between "
                    + MIN_SNAPSHOT_RETENTION + " and " + MAX_SNAPSHOT_RETENTION);
        }
    }

    public static SyllyConfig defaults() {
        return new SyllyConfig(CURRENT_SCHEMA_VERSION, true, 30, true, true, true, false, 20, true, true);
    }

    public SyllyConfig withEcoAuditorEnabled(boolean value) {
        return copy(value, ecoWarningCooldownSeconds, territoryImpactEnabled, routingAdvisorEnabled,
                optimizerEnabled, automaticSnapshotsEnabled, snapshotRetention, profileSwapNotifications,
                configurationWarnings);
    }

    public SyllyConfig withEcoWarningCooldownSeconds(int value) {
        return copy(ecoAuditorEnabled, value, territoryImpactEnabled, routingAdvisorEnabled,
                optimizerEnabled, automaticSnapshotsEnabled, snapshotRetention, profileSwapNotifications,
                configurationWarnings);
    }

    public SyllyConfig withTerritoryImpactEnabled(boolean value) {
        return copy(ecoAuditorEnabled, ecoWarningCooldownSeconds, value, routingAdvisorEnabled,
                optimizerEnabled, automaticSnapshotsEnabled, snapshotRetention, profileSwapNotifications,
                configurationWarnings);
    }

    public SyllyConfig withRoutingAdvisorEnabled(boolean value) {
        return copy(ecoAuditorEnabled, ecoWarningCooldownSeconds, territoryImpactEnabled, value,
                optimizerEnabled, automaticSnapshotsEnabled, snapshotRetention, profileSwapNotifications,
                configurationWarnings);
    }

    public SyllyConfig withOptimizerEnabled(boolean value) {
        return copy(ecoAuditorEnabled, ecoWarningCooldownSeconds, territoryImpactEnabled, routingAdvisorEnabled,
                value, automaticSnapshotsEnabled, snapshotRetention, profileSwapNotifications,
                configurationWarnings);
    }

    public SyllyConfig withAutomaticSnapshotsEnabled(boolean value) {
        return copy(ecoAuditorEnabled, ecoWarningCooldownSeconds, territoryImpactEnabled, routingAdvisorEnabled,
                optimizerEnabled, value, snapshotRetention, profileSwapNotifications, configurationWarnings);
    }

    public SyllyConfig withSnapshotRetention(int value) {
        return copy(ecoAuditorEnabled, ecoWarningCooldownSeconds, territoryImpactEnabled, routingAdvisorEnabled,
                optimizerEnabled, automaticSnapshotsEnabled, value, profileSwapNotifications,
                configurationWarnings);
    }

    public SyllyConfig withProfileSwapNotifications(boolean value) {
        return copy(ecoAuditorEnabled, ecoWarningCooldownSeconds, territoryImpactEnabled, routingAdvisorEnabled,
                optimizerEnabled, automaticSnapshotsEnabled, snapshotRetention, value, configurationWarnings);
    }

    public SyllyConfig withConfigurationWarnings(boolean value) {
        return copy(ecoAuditorEnabled, ecoWarningCooldownSeconds, territoryImpactEnabled, routingAdvisorEnabled,
                optimizerEnabled, automaticSnapshotsEnabled, snapshotRetention, profileSwapNotifications, value);
    }

    public SyllyConfig reset(SyllyConfigSection section) {
        Objects.requireNonNull(section, "section");
        SyllyConfig defaults = defaults();
        return switch (section) {
            case ECO_AUDITOR -> withEcoAuditorEnabled(defaults.ecoAuditorEnabled())
                    .withEcoWarningCooldownSeconds(defaults.ecoWarningCooldownSeconds());
            case TERRITORY_IMPACT -> withTerritoryImpactEnabled(defaults.territoryImpactEnabled());
            case ROUTING_ADVISOR -> withRoutingAdvisorEnabled(defaults.routingAdvisorEnabled());
            case OPTIMIZER -> withOptimizerEnabled(defaults.optimizerEnabled());
            case SNAPSHOTS -> withAutomaticSnapshotsEnabled(defaults.automaticSnapshotsEnabled())
                    .withSnapshotRetention(defaults.snapshotRetention());
            case NOTIFICATIONS -> withProfileSwapNotifications(defaults.profileSwapNotifications())
                    .withConfigurationWarnings(defaults.configurationWarnings());
            case PROFILES, CHARACTERS, COMPATIBILITY -> this;
        };
    }

    private SyllyConfig copy(
            boolean nextEcoAuditorEnabled,
            int nextEcoWarningCooldownSeconds,
            boolean nextTerritoryImpactEnabled,
            boolean nextRoutingAdvisorEnabled,
            boolean nextOptimizerEnabled,
            boolean nextAutomaticSnapshotsEnabled,
            int nextSnapshotRetention,
            boolean nextProfileSwapNotifications,
            boolean nextConfigurationWarnings) {
        return new SyllyConfig(
                CURRENT_SCHEMA_VERSION,
                nextEcoAuditorEnabled,
                nextEcoWarningCooldownSeconds,
                nextTerritoryImpactEnabled,
                nextRoutingAdvisorEnabled,
                nextOptimizerEnabled,
                nextAutomaticSnapshotsEnabled,
                nextSnapshotRetention,
                nextProfileSwapNotifications,
                nextConfigurationWarnings);
    }
}
