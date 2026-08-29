package net.syllyaddons.config;

import java.util.Objects;
import net.syllyaddons.impact.ImpactSeverity;

public record SyllyConfig(
        int schemaVersion,
        boolean ecoAuditorEnabled,
        int ecoWarningCooldownSeconds,
        boolean territoryImpactEnabled,
        boolean impactOverlayEnabled,
        ImpactOverlayScope impactOverlayScope,
        String impactSelectedEnemy,
        boolean impactDisconnectionsOnly,
        ImpactResourceFilter impactResourceFilter,
        int impactMinimumDelaySeconds,
        ImpactAlertSize impactAlertSize,
        int impactAlertDurationSeconds,
        boolean impactAlertSound,
        ImpactSeverity impactAlertMinimumSeverity,
        boolean routingAdvisorEnabled,
        RoutingAdvisorConfig routingAdvisor,
        boolean optimizerEnabled,
        OptimizerConfig optimizer,
        boolean automaticSnapshotsEnabled,
        int snapshotRetention,
        boolean profileSwapNotifications,
        boolean configurationWarnings) {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final int MIN_ECO_WARNING_COOLDOWN_SECONDS = 5;
    public static final int MAX_ECO_WARNING_COOLDOWN_SECONDS = 600;
    public static final int MIN_SNAPSHOT_RETENTION = 1;
    public static final int MAX_SNAPSHOT_RETENTION = 250;
    public static final int MIN_IMPACT_DELAY_SECONDS = 0;
    public static final int MAX_IMPACT_DELAY_SECONDS = 3_600;
    public static final int MIN_IMPACT_ALERT_DURATION_SECONDS = 2;
    public static final int MAX_IMPACT_ALERT_DURATION_SECONDS = 30;

    public SyllyConfig {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported Sylly Addons settings schema " + schemaVersion);
        }
        impactOverlayScope = Objects.requireNonNull(impactOverlayScope, "impactOverlayScope");
        impactSelectedEnemy = impactSelectedEnemy == null ? "" : impactSelectedEnemy.strip();
        impactResourceFilter = Objects.requireNonNull(impactResourceFilter, "impactResourceFilter");
        impactAlertSize = Objects.requireNonNull(impactAlertSize, "impactAlertSize");
        impactAlertMinimumSeverity = Objects.requireNonNull(
                impactAlertMinimumSeverity, "impactAlertMinimumSeverity");
        routingAdvisor = Objects.requireNonNull(routingAdvisor, "routingAdvisor");
        optimizer = Objects.requireNonNull(optimizer, "optimizer");
        if (ecoWarningCooldownSeconds < MIN_ECO_WARNING_COOLDOWN_SECONDS
                || ecoWarningCooldownSeconds > MAX_ECO_WARNING_COOLDOWN_SECONDS) {
            throw new IllegalArgumentException("Eco warning cooldown must be between "
                    + MIN_ECO_WARNING_COOLDOWN_SECONDS + " and " + MAX_ECO_WARNING_COOLDOWN_SECONDS + " seconds");
        }
        if (snapshotRetention < MIN_SNAPSHOT_RETENTION || snapshotRetention > MAX_SNAPSHOT_RETENTION) {
            throw new IllegalArgumentException("Snapshot retention must be between "
                    + MIN_SNAPSHOT_RETENTION + " and " + MAX_SNAPSHOT_RETENTION);
        }
        if (impactMinimumDelaySeconds < MIN_IMPACT_DELAY_SECONDS
                || impactMinimumDelaySeconds > MAX_IMPACT_DELAY_SECONDS) {
            throw new IllegalArgumentException("Impact delay filter must be between "
                    + MIN_IMPACT_DELAY_SECONDS + " and " + MAX_IMPACT_DELAY_SECONDS + " seconds");
        }
        if (impactAlertDurationSeconds < MIN_IMPACT_ALERT_DURATION_SECONDS
                || impactAlertDurationSeconds > MAX_IMPACT_ALERT_DURATION_SECONDS) {
            throw new IllegalArgumentException("Impact alert duration must be between "
                    + MIN_IMPACT_ALERT_DURATION_SECONDS + " and " + MAX_IMPACT_ALERT_DURATION_SECONDS + " seconds");
        }
    }

    public static SyllyConfig defaults() {
        return new SyllyConfig(
                CURRENT_SCHEMA_VERSION, true, 30, true,
                true, ImpactOverlayScope.OWN_GUILD, "", false, ImpactResourceFilter.ALL, 0,
                ImpactAlertSize.MEDIUM, 8, true, ImpactSeverity.WARNING,
                true, RoutingAdvisorConfig.defaults(), true, OptimizerConfig.defaults(), false, 20, true, true);
    }

    public SyllyConfig withEcoAuditorEnabled(boolean value) {
        return copy(value, ecoWarningCooldownSeconds, territoryImpactEnabled, impactOverlayEnabled,
                impactOverlayScope, impactSelectedEnemy, impactDisconnectionsOnly, impactResourceFilter,
                impactMinimumDelaySeconds, impactAlertSize, impactAlertDurationSeconds, impactAlertSound,
                impactAlertMinimumSeverity, routingAdvisorEnabled, routingAdvisor, optimizerEnabled, optimizer, automaticSnapshotsEnabled,
                snapshotRetention, profileSwapNotifications, configurationWarnings);
    }

    public SyllyConfig withEcoWarningCooldownSeconds(int value) {
        return copy(ecoAuditorEnabled, value, territoryImpactEnabled, impactOverlayEnabled,
                impactOverlayScope, impactSelectedEnemy, impactDisconnectionsOnly, impactResourceFilter,
                impactMinimumDelaySeconds, impactAlertSize, impactAlertDurationSeconds, impactAlertSound,
                impactAlertMinimumSeverity, routingAdvisorEnabled, routingAdvisor, optimizerEnabled, optimizer, automaticSnapshotsEnabled,
                snapshotRetention, profileSwapNotifications, configurationWarnings);
    }

    public SyllyConfig withTerritoryImpactEnabled(boolean value) {
        return copy(ecoAuditorEnabled, ecoWarningCooldownSeconds, value, impactOverlayEnabled,
                impactOverlayScope, impactSelectedEnemy, impactDisconnectionsOnly, impactResourceFilter,
                impactMinimumDelaySeconds, impactAlertSize, impactAlertDurationSeconds, impactAlertSound,
                impactAlertMinimumSeverity, routingAdvisorEnabled, routingAdvisor, optimizerEnabled, optimizer, automaticSnapshotsEnabled,
                snapshotRetention, profileSwapNotifications, configurationWarnings);
    }

    public SyllyConfig withImpactOverlayEnabled(boolean value) {
        return copy(ecoAuditorEnabled, ecoWarningCooldownSeconds, territoryImpactEnabled, value,
                impactOverlayScope, impactSelectedEnemy, impactDisconnectionsOnly, impactResourceFilter,
                impactMinimumDelaySeconds, impactAlertSize, impactAlertDurationSeconds, impactAlertSound,
                impactAlertMinimumSeverity, routingAdvisorEnabled, routingAdvisor, optimizerEnabled, optimizer, automaticSnapshotsEnabled,
                snapshotRetention, profileSwapNotifications, configurationWarnings);
    }

    public SyllyConfig withImpactOverlayScope(ImpactOverlayScope value) {
        return copy(ecoAuditorEnabled, ecoWarningCooldownSeconds, territoryImpactEnabled, impactOverlayEnabled,
                value, impactSelectedEnemy, impactDisconnectionsOnly, impactResourceFilter,
                impactMinimumDelaySeconds, impactAlertSize, impactAlertDurationSeconds, impactAlertSound,
                impactAlertMinimumSeverity, routingAdvisorEnabled, routingAdvisor, optimizerEnabled, optimizer, automaticSnapshotsEnabled,
                snapshotRetention, profileSwapNotifications, configurationWarnings);
    }

    public SyllyConfig withImpactSelectedEnemy(String value) {
        return copy(ecoAuditorEnabled, ecoWarningCooldownSeconds, territoryImpactEnabled, impactOverlayEnabled,
                impactOverlayScope, value, impactDisconnectionsOnly, impactResourceFilter,
                impactMinimumDelaySeconds, impactAlertSize, impactAlertDurationSeconds, impactAlertSound,
                impactAlertMinimumSeverity, routingAdvisorEnabled, routingAdvisor, optimizerEnabled, optimizer, automaticSnapshotsEnabled,
                snapshotRetention, profileSwapNotifications, configurationWarnings);
    }

    public SyllyConfig withImpactDisconnectionsOnly(boolean value) {
        return copy(ecoAuditorEnabled, ecoWarningCooldownSeconds, territoryImpactEnabled, impactOverlayEnabled,
                impactOverlayScope, impactSelectedEnemy, value, impactResourceFilter, impactMinimumDelaySeconds,
                impactAlertSize, impactAlertDurationSeconds, impactAlertSound, impactAlertMinimumSeverity,
                routingAdvisorEnabled, routingAdvisor, optimizerEnabled, optimizer, automaticSnapshotsEnabled, snapshotRetention,
                profileSwapNotifications, configurationWarnings);
    }

    public SyllyConfig withImpactResourceFilter(ImpactResourceFilter value) {
        return copy(ecoAuditorEnabled, ecoWarningCooldownSeconds, territoryImpactEnabled, impactOverlayEnabled,
                impactOverlayScope, impactSelectedEnemy, impactDisconnectionsOnly, value, impactMinimumDelaySeconds,
                impactAlertSize, impactAlertDurationSeconds, impactAlertSound, impactAlertMinimumSeverity,
                routingAdvisorEnabled, routingAdvisor, optimizerEnabled, optimizer, automaticSnapshotsEnabled, snapshotRetention,
                profileSwapNotifications, configurationWarnings);
    }

    public SyllyConfig withImpactMinimumDelaySeconds(int value) {
        return copy(ecoAuditorEnabled, ecoWarningCooldownSeconds, territoryImpactEnabled, impactOverlayEnabled,
                impactOverlayScope, impactSelectedEnemy, impactDisconnectionsOnly, impactResourceFilter, value,
                impactAlertSize, impactAlertDurationSeconds, impactAlertSound, impactAlertMinimumSeverity,
                routingAdvisorEnabled, routingAdvisor, optimizerEnabled, optimizer, automaticSnapshotsEnabled, snapshotRetention,
                profileSwapNotifications, configurationWarnings);
    }

    public SyllyConfig withImpactAlertSize(ImpactAlertSize value) {
        return copy(ecoAuditorEnabled, ecoWarningCooldownSeconds, territoryImpactEnabled, impactOverlayEnabled,
                impactOverlayScope, impactSelectedEnemy, impactDisconnectionsOnly, impactResourceFilter,
                impactMinimumDelaySeconds, value, impactAlertDurationSeconds, impactAlertSound,
                impactAlertMinimumSeverity, routingAdvisorEnabled, routingAdvisor, optimizerEnabled, optimizer, automaticSnapshotsEnabled,
                snapshotRetention, profileSwapNotifications, configurationWarnings);
    }

    public SyllyConfig withImpactAlertDurationSeconds(int value) {
        return copy(ecoAuditorEnabled, ecoWarningCooldownSeconds, territoryImpactEnabled, impactOverlayEnabled,
                impactOverlayScope, impactSelectedEnemy, impactDisconnectionsOnly, impactResourceFilter,
                impactMinimumDelaySeconds, impactAlertSize, value, impactAlertSound, impactAlertMinimumSeverity,
                routingAdvisorEnabled, routingAdvisor, optimizerEnabled, optimizer, automaticSnapshotsEnabled, snapshotRetention,
                profileSwapNotifications, configurationWarnings);
    }

    public SyllyConfig withImpactAlertSound(boolean value) {
        return copy(ecoAuditorEnabled, ecoWarningCooldownSeconds, territoryImpactEnabled, impactOverlayEnabled,
                impactOverlayScope, impactSelectedEnemy, impactDisconnectionsOnly, impactResourceFilter,
                impactMinimumDelaySeconds, impactAlertSize, impactAlertDurationSeconds, value,
                impactAlertMinimumSeverity, routingAdvisorEnabled, routingAdvisor, optimizerEnabled, optimizer, automaticSnapshotsEnabled,
                snapshotRetention, profileSwapNotifications, configurationWarnings);
    }

    public SyllyConfig withImpactAlertMinimumSeverity(ImpactSeverity value) {
        return copy(ecoAuditorEnabled, ecoWarningCooldownSeconds, territoryImpactEnabled, impactOverlayEnabled,
                impactOverlayScope, impactSelectedEnemy, impactDisconnectionsOnly, impactResourceFilter,
                impactMinimumDelaySeconds, impactAlertSize, impactAlertDurationSeconds, impactAlertSound, value,
                routingAdvisorEnabled, routingAdvisor, optimizerEnabled, optimizer, automaticSnapshotsEnabled, snapshotRetention,
                profileSwapNotifications, configurationWarnings);
    }

    public SyllyConfig withRoutingAdvisorEnabled(boolean value) {
        return copy(ecoAuditorEnabled, ecoWarningCooldownSeconds, territoryImpactEnabled, impactOverlayEnabled,
                impactOverlayScope, impactSelectedEnemy, impactDisconnectionsOnly, impactResourceFilter,
                impactMinimumDelaySeconds, impactAlertSize, impactAlertDurationSeconds, impactAlertSound,
                impactAlertMinimumSeverity, value, routingAdvisor, optimizerEnabled, optimizer, automaticSnapshotsEnabled, snapshotRetention,
                profileSwapNotifications, configurationWarnings);
    }

    public SyllyConfig withRoutingAdvisor(RoutingAdvisorConfig value) {
        return copy(ecoAuditorEnabled, ecoWarningCooldownSeconds, territoryImpactEnabled, impactOverlayEnabled,
                impactOverlayScope, impactSelectedEnemy, impactDisconnectionsOnly, impactResourceFilter,
                impactMinimumDelaySeconds, impactAlertSize, impactAlertDurationSeconds, impactAlertSound,
                impactAlertMinimumSeverity, routingAdvisorEnabled, Objects.requireNonNull(value, "value"),
                optimizerEnabled, optimizer, automaticSnapshotsEnabled, snapshotRetention,
                profileSwapNotifications, configurationWarnings);
    }

    public SyllyConfig withOptimizerEnabled(boolean value) {
        return copy(ecoAuditorEnabled, ecoWarningCooldownSeconds, territoryImpactEnabled, impactOverlayEnabled,
                impactOverlayScope, impactSelectedEnemy, impactDisconnectionsOnly, impactResourceFilter,
                impactMinimumDelaySeconds, impactAlertSize, impactAlertDurationSeconds, impactAlertSound,
                impactAlertMinimumSeverity, routingAdvisorEnabled, routingAdvisor, value, optimizer, automaticSnapshotsEnabled,
                snapshotRetention, profileSwapNotifications, configurationWarnings);
    }

    public SyllyConfig withOptimizer(OptimizerConfig value) {
        return copy(ecoAuditorEnabled, ecoWarningCooldownSeconds, territoryImpactEnabled, impactOverlayEnabled,
                impactOverlayScope, impactSelectedEnemy, impactDisconnectionsOnly, impactResourceFilter,
                impactMinimumDelaySeconds, impactAlertSize, impactAlertDurationSeconds, impactAlertSound,
                impactAlertMinimumSeverity, routingAdvisorEnabled, routingAdvisor, optimizerEnabled,
                Objects.requireNonNull(value, "value"), automaticSnapshotsEnabled,
                snapshotRetention, profileSwapNotifications, configurationWarnings);
    }

    public SyllyConfig withAutomaticSnapshotsEnabled(boolean value) {
        return copy(ecoAuditorEnabled, ecoWarningCooldownSeconds, territoryImpactEnabled, impactOverlayEnabled,
                impactOverlayScope, impactSelectedEnemy, impactDisconnectionsOnly, impactResourceFilter,
                impactMinimumDelaySeconds, impactAlertSize, impactAlertDurationSeconds, impactAlertSound,
                impactAlertMinimumSeverity, routingAdvisorEnabled, routingAdvisor, optimizerEnabled, optimizer, value, snapshotRetention,
                profileSwapNotifications, configurationWarnings);
    }

    public SyllyConfig withSnapshotRetention(int value) {
        return copy(ecoAuditorEnabled, ecoWarningCooldownSeconds, territoryImpactEnabled, impactOverlayEnabled,
                impactOverlayScope, impactSelectedEnemy, impactDisconnectionsOnly, impactResourceFilter,
                impactMinimumDelaySeconds, impactAlertSize, impactAlertDurationSeconds, impactAlertSound,
                impactAlertMinimumSeverity, routingAdvisorEnabled, routingAdvisor, optimizerEnabled, optimizer, automaticSnapshotsEnabled,
                value, profileSwapNotifications, configurationWarnings);
    }

    public SyllyConfig withProfileSwapNotifications(boolean value) {
        return copy(ecoAuditorEnabled, ecoWarningCooldownSeconds, territoryImpactEnabled, impactOverlayEnabled,
                impactOverlayScope, impactSelectedEnemy, impactDisconnectionsOnly, impactResourceFilter,
                impactMinimumDelaySeconds, impactAlertSize, impactAlertDurationSeconds, impactAlertSound,
                impactAlertMinimumSeverity, routingAdvisorEnabled, routingAdvisor, optimizerEnabled, optimizer, automaticSnapshotsEnabled,
                snapshotRetention, value, configurationWarnings);
    }

    public SyllyConfig withConfigurationWarnings(boolean value) {
        return copy(ecoAuditorEnabled, ecoWarningCooldownSeconds, territoryImpactEnabled, impactOverlayEnabled,
                impactOverlayScope, impactSelectedEnemy, impactDisconnectionsOnly, impactResourceFilter,
                impactMinimumDelaySeconds, impactAlertSize, impactAlertDurationSeconds, impactAlertSound,
                impactAlertMinimumSeverity, routingAdvisorEnabled, routingAdvisor, optimizerEnabled, optimizer, automaticSnapshotsEnabled,
                snapshotRetention, profileSwapNotifications, value);
    }

    public SyllyConfig reset(SyllyConfigSection section) {
        Objects.requireNonNull(section, "section");
        SyllyConfig defaults = defaults();
        return switch (section) {
            case ECO_AUDITOR -> withEcoAuditorEnabled(defaults.ecoAuditorEnabled())
                    .withEcoWarningCooldownSeconds(defaults.ecoWarningCooldownSeconds());
            case TERRITORY_IMPACT -> withTerritoryImpactEnabled(defaults.territoryImpactEnabled())
                    .withImpactOverlayEnabled(defaults.impactOverlayEnabled())
                    .withImpactOverlayScope(defaults.impactOverlayScope())
                    .withImpactSelectedEnemy(defaults.impactSelectedEnemy())
                    .withImpactDisconnectionsOnly(defaults.impactDisconnectionsOnly())
                    .withImpactResourceFilter(defaults.impactResourceFilter())
                    .withImpactMinimumDelaySeconds(defaults.impactMinimumDelaySeconds())
                    .withImpactAlertSize(defaults.impactAlertSize())
                    .withImpactAlertDurationSeconds(defaults.impactAlertDurationSeconds())
                    .withImpactAlertSound(defaults.impactAlertSound())
                    .withImpactAlertMinimumSeverity(defaults.impactAlertMinimumSeverity());
            case ROUTING_ADVISOR -> withRoutingAdvisorEnabled(defaults.routingAdvisorEnabled())
                    .withRoutingAdvisor(defaults.routingAdvisor());
            case OPTIMIZER -> withOptimizerEnabled(defaults.optimizerEnabled())
                    .withOptimizer(defaults.optimizer());
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
            boolean nextImpactOverlayEnabled,
            ImpactOverlayScope nextImpactOverlayScope,
            String nextImpactSelectedEnemy,
            boolean nextImpactDisconnectionsOnly,
            ImpactResourceFilter nextImpactResourceFilter,
            int nextImpactMinimumDelaySeconds,
            ImpactAlertSize nextImpactAlertSize,
            int nextImpactAlertDurationSeconds,
            boolean nextImpactAlertSound,
            ImpactSeverity nextImpactAlertMinimumSeverity,
            boolean nextRoutingAdvisorEnabled,
            RoutingAdvisorConfig nextRoutingAdvisor,
            boolean nextOptimizerEnabled,
            OptimizerConfig nextOptimizer,
            boolean nextAutomaticSnapshotsEnabled,
            int nextSnapshotRetention,
            boolean nextProfileSwapNotifications,
            boolean nextConfigurationWarnings) {
        return new SyllyConfig(
                CURRENT_SCHEMA_VERSION, nextEcoAuditorEnabled, nextEcoWarningCooldownSeconds,
                nextTerritoryImpactEnabled, nextImpactOverlayEnabled, nextImpactOverlayScope,
                nextImpactSelectedEnemy, nextImpactDisconnectionsOnly, nextImpactResourceFilter,
                nextImpactMinimumDelaySeconds, nextImpactAlertSize, nextImpactAlertDurationSeconds,
                nextImpactAlertSound, nextImpactAlertMinimumSeverity, nextRoutingAdvisorEnabled,
                nextRoutingAdvisor, nextOptimizerEnabled, nextOptimizer, nextAutomaticSnapshotsEnabled, nextSnapshotRetention,
                nextProfileSwapNotifications, nextConfigurationWarnings);
    }
}
