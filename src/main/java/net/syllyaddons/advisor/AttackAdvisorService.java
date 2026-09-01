package net.syllyaddons.advisor;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.syllyaddons.config.SyllyConfig;
import net.syllyaddons.config.SyllyConfigService;
import net.syllyaddons.domain.Evidence;
import net.syllyaddons.domain.EvidenceKind;
import net.syllyaddons.domain.ObservedValue;
import net.syllyaddons.observation.ObservationBatch;
import net.syllyaddons.observation.ObservedStateRepository;

/** Thread-safe state holder between passive Wynntils observations and client rendering. */
public final class AttackAdvisorService {
    private final ObservedStateRepository repository;
    private final SyllyConfigService settings;
    private final AttackMenuParser parser;
    private final AttackRoutingAdvisor advisor;
    private AttackAdvisorView latest;

    public AttackAdvisorService(ObservedStateRepository repository, SyllyConfigService settings) {
        this(repository, settings, new AttackMenuParser(), new AttackRoutingAdvisor());
    }

    AttackAdvisorService(
            ObservedStateRepository repository,
            SyllyConfigService settings,
            AttackMenuParser parser,
            AttackRoutingAdvisor advisor) {
        this.repository = java.util.Objects.requireNonNull(repository, "repository");
        this.settings = java.util.Objects.requireNonNull(settings, "settings");
        this.parser = java.util.Objects.requireNonNull(parser, "parser");
        this.advisor = java.util.Objects.requireNonNull(advisor, "advisor");
    }

    public synchronized void observeMenu(String title, List<AttackMenuEntry> entries, long nowEpochMillis) {
        SyllyConfig config = settings.snapshot();
        if (!config.routingAdvisorEnabled()) {
            latest = null;
            return;
        }
        Set<String> territoryNames = repository.snapshot().territories().keySet();
        AttackMenuSnapshot menu = parser.parse(title, entries, territoryNames, nowEpochMillis);
        if (menu.target().isBlank()) return;
        AttackRoutingAdvice advice = advisor.advise(repository.snapshot(), menu, nowEpochMillis);
        if (advice.routingModeInferred()) {
            Evidence evidence = new Evidence(
                    EvidenceKind.DERIVED,
                    nowEpochMillis,
                    "attack-menu-timer",
                    "routing-rules-2026-08-29",
                    "Uniquely matched the displayed attack timer/route to a local routing candidate");
            repository.merge(new ObservationBatch(
                    nowEpochMillis,
                    null,
                    null,
                    null,
                    ObservedValue.known(advice.resolvedRoutingMode(), evidence),
                    java.util.Map.of()));
        }
        latest = new AttackAdvisorView(menu, advice, null, nowEpochMillis);
    }

    public synchronized void observeQueued(String territory, int queuedTimerSeconds, long nowEpochMillis) {
        if (latest == null || territory == null || !latest.menu().target().equalsIgnoreCase(territory)) return;
        int menuTimer = latest.menu().observedTimerSeconds().orElse(-1);
        if (menuTimer < 0) return;
        latest = new AttackAdvisorView(
                latest.menu(),
                latest.advice(),
                new QueueTimerValidation(territory, menuTimer, Math.max(0, queuedTimerSeconds), nowEpochMillis),
                nowEpochMillis);
    }

    public synchronized Optional<AttackAdvisorView> latest() {
        return Optional.ofNullable(latest);
    }

    public synchronized void clear() {
        latest = null;
    }
}
