# Sylly Addons Development Plan

## 1. Goal

Build a private, client-side companion mod for Minecraft 1.21.11 that uses Wynntils data and behavior to provide:

- per-character spell-key profiles;
- guild economy inspection and explanations;
- territory-loss simulation and alerts;
- HQ attack-routing advice;
- read-only snapshots and comparisons;
- a defence-sustainability optimizer.

The first target is a reliable personal-use build. Public distribution is explicitly out of scope until the mod is useful and stable in daily play.

## 2. Private-build scope decisions

These decisions reduce early work and make version-coupled integration acceptable:

- Target **Fabric only** at first.
- Target **Java 21**, Minecraft **1.21.11**, and one exact Wynntils build.
- Pin the tested Wynntils version and record its Git commit or artifact checksum.
- Allow narrowly scoped mixins into Wynntils where no usable public hook exists.
- Do not promise compatibility with newer Wynntils or Minecraft versions.
- Use one language and one visual theme initially.
- Open configuration from Mod Menu and a configurable keybind. A button inside Wynntils settings is deferred.
- Keep configuration files human-readable. Migration machinery is only added when a schema actually changes.
- Do not build an update checker, telemetry, publishing pipeline, public documentation site, or multi-loader abstraction yet.
- Compression and encryption for snapshots are deferred.

## 3. Non-negotiable safety boundary

The addon may observe, cache, calculate, highlight, recommend, export, and notify. It must not:

- change territory upgrades or tower settings;
- apply an economy snapshot;
- change HQ routing;
- queue an attack;
- react to a guild event by changing guild state;
- send automated competitive-menu clicks or packets.

Exact menu-only data is captured passively while the user has the relevant screen open. If required information has not been observed, the UI must say so instead of silently inventing it.

## 4. Technical shape

Keep Wynntils integration thin and keep all important calculations independent of Minecraft:

```text
Wynntils/API adapters
        |
        v
Immutable ObservedState + DataEvidence
        |
        v
Normalized EcoSnapshot
        |
        +----------+------------+-------------+
        v          v            v             v
     Routing     Auditor    Loss impact    Optimizer
        |          |            |             |
        +----------+------------+-------------+
                           |
                           v
                    Screens / HUD / map
```

Suggested package boundaries:

```text
.../compat/wynntils/       Pinned-version adapters and mixins
.../observation/           Passive event and screen capture
.../domain/                Immutable territory, guild, resource, and snapshot types
.../routing/               Graph construction, route selection, taxes, and timing
.../economy/               Production, expenses, reserves, and provenance ledger
.../audit/                 Findings and explanations
.../impact/                Territory-removal simulations and cache
.../optimizer/             Discrete optimization model and recommendations
.../persistence/           Settings, profiles, and .tnsreco files
.../client/input/          Spell-profile input dispatcher
.../client/gui/            Configuration and analysis screens
.../client/map/            Wynntils map integration
.../client/notification/   HUD messages and sounds
```

Core engine code must not import Minecraft or Wynntils classes. Adapters convert external objects to domain records at the boundary.

## 5. Shared data contracts

### Evidence and freshness

Every observed value carries enough metadata to explain its reliability:

```java
enum EvidenceKind {
    LOCAL_EXACT,       // Observed in the current player's UI
    PUBLIC_EXACT,      // Exact public ownership/topology value
    PUBLIC_DELAYED,    // Public value with a known refresh delay
    DERIVED,           // Deterministically calculated from known inputs
    ESTIMATED,
    UNKNOWN
}
```

Store at least:

- evidence kind;
- observation timestamp;
- source name;
- source/version identifier;
- optional reason when unknown or estimated.

### Immutable state

Use immutable snapshots for all calculations. Never let a background calculation read live mutable Wynntils collections.

Minimum snapshot fields:

- schema version and creation time;
- guild identity;
- territory ownership and adjacency;
- HQ and routing mode, if known;
- production and storage;
- tower and economy upgrade levels, if observed;
- resource expenses and reserves;
- evidence/freshness metadata per field group.

### Result contracts

Auditor, impact, advisor, and optimizer results must contain structured facts rather than preformatted paragraphs. The GUI formats them later. A result should include:

- severity;
- affected territories/resources;
- before and after values;
- explanation chain;
- evidence level;
- missing inputs;
- calculation version.

## 6. Development tracks

Tracks are listed separately so work can progress independently where their dependencies allow it.

### Track 0 — Foundation and compatibility spike

**Outcome:** A Fabric development build starts with Wynntils and proves that the chosen integration points work.

Tasks:

- [ ] Create the Fabric 1.21.11/Java 21 Gradle project.
- [ ] Add Fabric API, Mod Menu, and the exact Wynntils development/runtime dependency.
- [ ] Add mod metadata declaring Wynntils as required.
- [ ] Record the pinned Wynntils version and checksum in `COMPATIBILITY.md`.
- [ ] Prove registration on the Wynntils event bus.
- [ ] Read current character ID and class after selection.
- [ ] Read the current guild and public territory collection.
- [ ] Subscribe to or detect territory ownership refreshes.
- [ ] Render one harmless HUD message and one marker/colour prototype on the Wynntils map.
- [ ] Confirm clean behavior when not on Wynncraft, not in a guild, or while Wynntils is still initializing.
- [ ] Add a compatibility guard that disables integration with a clear message on an untested Wynntils version.

Acceptance gate:

- The game launches repeatedly in development.
- Selecting two characters produces the correct stable IDs and classes.
- Territory ownership can be read without issuing a command or menu click.
- A Wynntils update cannot cause an unexplained crash; the addon fails closed.

### Track 1 — Observation and normalized snapshots

**Outcome:** All available data flows into one inspectable, immutable state model.

Tasks:

- [x] Define the domain records for guilds, territories, resources, upgrades, ownership, and routes.
- [x] Implement a Wynntils character adapter.
- [x] Implement a Wynntils guild/territory adapter.
- [x] Consume public HQ, ownership, resource, link, treasury, and defence data.
- [x] Passively capture exact local production, storage, alerts, and upgrades when Wynntils parses the relevant screens.
- [ ] Validate the implemented passive HQ routing-mode recognizer against the live screen.
- [x] Merge partial observations without replacing newer or higher-quality evidence with worse evidence.
- [x] Build a data-status report listing stale and missing fields.
- [x] Expose a read-only debug screen showing normalized values, evidence sources, and observation ages.
- [x] Publish state-change events only when the normalized state actually changes.
- [x] Persist the latest useful local scan for restart recovery, clearly marked as historical.

Acceptance gate:

- A debug screen can show every known field, its source, and its age.
- Opening the territory-management screen enriches the state without the addon sending clicks.
- Missing routing mode or upgrade data remains explicitly unknown.

### Track 2 — Per-character spell profiles

**Outcome:** Daily-use spell mappings switch reliably by character without rewriting Wynntils' saved configuration.

Tasks:

- [x] Define profile, assignment, fallback, and manual-override persistence formats.
- [x] Implement resolution order: character, class fallback, global default, keep current.
- [x] Add keyboard and mouse input capture.
- [x] Add conflict detection against Minecraft and Wynntils bindings.
- [x] Import the current Wynntils spell bindings into a new profile.
- [x] Prototype the preferred input proxy: map physical inputs to spell numbers and call the existing Wynntils spell-casting model.
- [x] Preserve Wynntils casting delays, class click direction, cooldown handling, and invalid-weapon safety where accessible.
- [x] Prevent duplicate casts when a profile input overlaps a native Wynntils binding.
- [x] Keep Wynntils mappings unchanged because the preferred casting proxy works; no fallback adapter is currently needed.
- [x] Add automatic switching after the stable character ID is known.
- [x] Scan every card when the local character-selection menu opens, show class/nickname/level, and link its stable ID
  after selection without trusting incomplete public account data.
- [x] Keep the character-card list usable at small GUI scales with wheel scrolling and a draggable scrollbar.
- [x] Show a client-side chat notice only when resolution changes to a different profile ID.
- [x] Add a two-tab Characters/Profiles manager with direct assignments, inline editing, automatic-switch toggle,
  temporary use, remembered use, class fallback, and global default controls.
- [x] Restore a safe neutral state on disconnect or character selection; fail closed on integration errors.

Acceptance gate:

- Ten switches between different characters select the correct profile every time.
- Keyboard and mouse mappings cast the intended spell once per press.
- Restarting the game leaves Wynntils' original saved bindings unchanged.
- Typing in chat, using menus, and rebinding controls never casts a spell.

### Track 3 — Configuration and working screens

**Outcome:** Everything needed for personal use is configurable without commands.

Tasks:

- [x] Build the configuration shell and navigation.
- [x] Add sections for Profiles, Characters, Eco Auditor, Territory Impact, Routing Advisor, Optimizer, Snapshots, Notifications, and Compatibility.
- [x] Implement settings search.
- [x] Implement field-level reset and section reset.
- [x] Add tooltips and immediate validation.
- [x] Build the profile editor and inline key-capture panel.
- [x] Build a data-status/debug screen early, before polished analysis panels.
- [x] Persist edits atomically and retain a backup of the last readable config.
- [x] Add a configurable open-settings key.
- [x] Integrate with Mod Menu.
- [x] Defer a Wynntils-settings button until all major features work.

Acceptance gate:

- Every implemented setting is reachable visually.
- Invalid input cannot corrupt the configuration file.
- A broken config is quarantined and replaced with defaults with a visible warning.

### Track 4 — Routing and economy truth engine

**Outcome:** A deterministic pure-Java engine reproduces observed Wynncraft resource movement closely enough to support all dependent features.

This is the critical research track. Correctness matters more than feature count.

Tasks:

- [x] Represent territory topology as an immutable graph.
- [x] Normalize asymmetric or temporarily missing links without hiding the anomaly.
- [x] Implement same-owner reachability as a diagnostic only, not as the final route calculation.
- [x] Document the known rules for Cheapest and Fastest modes.
- [x] Implement candidate route selection, delivery time, taxes, and tie-breaking.
- [x] Model production, storage, expenses, HQ delivery, and resource loss.
- [x] Build a provenance ledger that tracks every produced unit through route steps, taxes, destination, and spending.
- [x] Capture real observations as small versioned fixtures.
- [ ] Add golden tests comparing calculated totals/routes with observed Wynncraft values.
- [x] Surface disagreements as calculator diagnostics instead of concealing them.
- [x] Version the rules independently from the snapshot schema.

Validation fixtures should cover:

- a line of territories;
- a fork with equal-cost routes;
- a cycle;
- a disconnected branch;
- multiple entry connections to HQ;
- a territory loss that causes rerouting without disconnection;
- Cheapest and Fastest choosing different routes;
- asymmetric link data;
- unknown enemy routing mode;
- storage and resource-deficit edge cases.

Acceptance gate:

- All synthetic graph tests pass.
- Several captured real guild states match observed routes and totals within a documented tolerance.
- No downstream feature labels a result exact if an unvalidated rule affects it.

### Track 5 — Snapshots and resource provenance

**Outcome:** Current and historical economy states are inspectable and portable.

Tasks:

- [ ] Define versioned `.tnsreco` JSON.
- [ ] Export atomically with format version, creation time, source versions, and checksum.
- [ ] Import into a read-only context.
- [ ] Validate sizes, schema, enum values, links, and numeric bounds defensively.
- [ ] Add migrations only when the format first changes.
- [ ] Add current-versus-snapshot comparison.
- [ ] Build resource-total drill-down by production and expense.
- [ ] Show source territory, complete route, taxes, time, destination, and consumers.
- [ ] Highlight an inspected route on the map.

Acceptance gate:

- Export followed by import preserves all normalized data.
- An imported snapshot cannot alter live game or guild state.
- Corrupt and future-version files fail with useful messages.

### Track 6 — Eco auditor

**Outcome:** The addon explains actionable economic problems rather than merely displaying totals.

Implement checks incrementally:

- [ ] Negative net production.
- [ ] Unsustainable tower upkeep.
- [ ] Production not reaching HQ.
- [ ] Single-route and single-chokepoint fragility.
- [ ] Simultaneous surplus and deficit patterns.
- [ ] Storage and treasury risk.
- [ ] Long or unnecessarily expensive routes.
- [ ] Dominated economic upgrades.
- [ ] Low-value upgrades.
- [ ] Potentially safe downgrades.

For every finding:

- [ ] Include a short summary.
- [ ] Include the arithmetic or route facts that caused it.
- [ ] List affected territories.
- [ ] Show freshness, evidence, and missing inputs.
- [ ] Deduplicate findings that share the same root cause.
- [ ] Link to provenance where relevant.

Acceptance gate:

- Hand-constructed fixtures trigger the intended findings and no unrelated ones.
- Every numeric claim can be traced back to snapshot inputs and calculation steps.

### Track 7 — Territory-impact simulator and cache

**Outcome:** Removing any relevant territory produces a fast, explainable consequence report.

Tasks:

- [ ] Define immutable baseline and simulated graph states.
- [ ] Remove one territory and recalculate valid routes from scratch.
- [ ] Diff routes to classify disconnected, rerouted, unchanged, and newly critical territories.
- [ ] Calculate delivery-time, tax, HQ-delivery, tower-supply, and chokepoint changes.
- [ ] Calculate both routing modes when the mode is unknown.
- [ ] Separate exact topology results from estimated resource/tower results.
- [ ] Add defensive-mode severity scoring.
- [ ] Add offensive-mode severity scoring.
- [ ] Precompute impacts off the render thread.
- [ ] Cache by a hash of ownership, HQ, topology, routing mode, taxes, and relevant production inputs.
- [ ] Cancel or discard results built from an obsolete state generation.
- [ ] Keep hover rendering constant-time by reading only completed cached results.

Acceptance gate:

- Removing a territory from each routing fixture produces the expected diff.
- Rebuilding the full cache does not cause a visible frame hitch.
- Unknown enemy data cannot appear as exact.

### Track 8 — Map overlays and live alerts

**Outcome:** Impact information is visible at the moment it is useful.

Tasks:

- [ ] Add optional territory impact colouring.
- [ ] Implement grey, yellow, orange, red, and purple severity mapping.
- [ ] Add filters for own guild, selected enemy, visible guilds, disconnections, resources, and delay.
- [ ] Extend territory hover tooltips with cached impact summaries.
- [ ] Add route and provenance highlighting.
- [ ] Detect ownership changes by diffing normalized territory states.
- [ ] Match a reported loss to the last valid pre-loss cache entry.
- [ ] Display minor, warning, critical, and catastrophic alerts.
- [ ] Add size, duration, sound, and minimum-threshold settings.
- [ ] Display the age of the baseline used by the alert.
- [ ] Expire alerts safely on world or guild changes.

Acceptance gate:

- Simulated ownership transitions select the correct cached before-state result.
- Map rendering remains usable with every filter enabled.
- Alerts never imply live precision beyond the source refresh interval.

### Track 9 — HQ attack-routing advisor

**Outcome:** Preparing an attack shows a read-only Cheapest-versus-Fastest comparison.

Tasks:

- [ ] Identify the passive event or screen state that reliably exposes the prospective target.
- [ ] Determine or capture attack cost and timer inputs without clicking or queueing.
- [ ] Calculate both modes using the validated routing rules.
- [ ] Show time saved and additional cost.
- [ ] Detect when Cheapest is preferable for negligible delay.
- [ ] Add minimum saving, maximum extra cost, active-operation-only, and significance thresholds.
- [ ] Mark results unavailable when required inputs are not observable.

Acceptance gate:

- Advice matches several manually checked attack preparations.
- No advisor path sends a click, command, or packet that changes attack state.

### Track 10 — Defence-sustainability optimizer

**Outcome:** The addon proposes a cheaper, manually applicable configuration that preserves selected defence constraints.

Tasks:

- [ ] Define decision variables for allowed economy-upgrade levels.
- [ ] Treat current tower configuration as fixed.
- [ ] Model resource production, upgrade costs, upkeep, routing loss, and reserve constraints.
- [ ] Support objectives for minimum expense, deficit repair, maximum minimum buffer, and reserve preservation.
- [ ] Prefer a small deterministic integer/constraint solver or a well-contained library.
- [ ] Add time and search-node limits.
- [ ] Return the best known feasible result if optimality is not proven.
- [ ] Verify proposed configurations through the normal economy engine.
- [ ] Produce a manual checklist and before/after totals.
- [ ] Never expose an Apply action.

Acceptance gate:

- Tiny cases with enumerable solutions match brute-force results.
- Every recommendation is independently revalidated before display.
- The current configuration is always available as the no-change baseline.
- Infeasible reserve requirements produce an explanation, not an empty result.

### Track 11 — Diagnostics, tests, and personal operations

**Outcome:** Failures are diagnosable without a public support system.

Tasks:

- [ ] Add structured debug logging with sensitive/player-specific values minimized.
- [ ] Add an in-game compatibility and data-health screen.
- [ ] Add a one-click debug bundle containing versions, calculation diagnostics, and a redacted snapshot.
- [ ] Unit-test all pure domain, routing, auditor, impact, and optimizer code.
- [ ] Maintain golden real-world fixtures separately from mutable live state.
- [ ] Add performance tests for all-territory impact-cache rebuilds.
- [ ] Add a manual smoke-test checklist for each pinned Wynntils upgrade.
- [ ] Back up config and profiles before schema changes.
- [ ] Keep mixins minimal and document their exact target and reason.

Acceptance gate:

- A failed integration can be distinguished from missing data and from a calculation disagreement.
- The addon can disable one broken subsystem while leaving unrelated features usable.

## 7. Milestones and ordering

### Milestone A — Integration proof

Tracks: **0**, minimum **1**, and a small part of **3**.

Deliverable:

- launchable Fabric mod;
- pinned Wynntils compatibility;
- character/guild/territory debug display;
- settings key and compatibility guard;
- map-rendering prototype.

Stop/go decision: continue only after character identity, territory ingestion, event timing, and map hooks are proven in the real client.

### Milestone B — Personal daily-driver spell profiles

Tracks: **2** and required **3** work.

Deliverable:

- profile editor;
- character assignments and fallbacks;
- automatic switching;
- manual picker;
- reliable casting without saved-binding churn.

This is the earliest independently useful build.

### Milestone C — Trustworthy economy baseline

Tracks: **1**, **4**, and **5**.

Deliverable:

- normalized live state;
- data-health screen;
- snapshots;
- basic routing and resource ledger;
- provenance drill-down;
- real-state validation fixtures.

Do not begin the optimizer until this milestone is trustworthy.

### Milestone D — Defensive intelligence

Tracks: **6**, **7**, and the defensive subset of **8**.

Deliverable:

- initial auditor;
- own-guild territory-loss impact;
- impact cache;
- map colours/tooltips;
- live own-loss alerts.

### Milestone E — Offensive estimates and routing advice

Tracks: offensive **7/8** and **9**.

Deliverable:

- enemy impact estimates with evidence labels;
- dual-mode calculation for unknown routing;
- read-only HQ attack-routing advice.

### Milestone F — Optimization

Track: **10**, supported by mature **4–6**.

Deliverable:

- constrained defence-sustainability optimizer;
- verified manual checklist;
- explicit feasibility and optimality status.

### Deferred milestone — Possible release preparation

Only consider this after sustained personal use. It would add:

- NeoForge/Architectury support;
- compatibility across multiple Wynntils versions;
- localization;
- accessibility and broad UI polish;
- packaging, changelogs, licenses, and update metadata;
- clean-room installation testing and public documentation.

## 8. Suggested first implementation slice

Build this narrow end-to-end slice before constructing the full settings system:

1. Launch beside the pinned Wynntils build.
2. Observe a stable character ID and class.
3. Resolve that ID to one hard-coded temporary spell profile.
4. Cast one selected spell through the proposed Wynntils integration.
5. Observe public territory ownership into an immutable snapshot.
6. Render one territory with a diagnostic colour on the Wynntils map.
7. Export that snapshot to JSON.

This slice deliberately touches every risky boundary while avoiding premature feature depth.

## 9. Risk register

| Risk | Impact | Mitigation |
|---|---:|---|
| Wynntils internals change | High | Pin one version, isolate adapters, fail closed, document mixins |
| Spell proxy differs from Quick Cast | High | Validate delays/safety/class behavior; retain a second integration option |
| Routing rules are incomplete | Critical | Golden fixtures, evidence labels, disagreement diagnostics, no false exactness |
| Exact upgrade data is unavailable until a menu is opened | Medium | Passive scans, historical cache, visible “scan required” state |
| Public resource data is delayed | Medium | Carry timestamps and state the source refresh limit in results |
| Map hooks cause crashes or frame loss | Medium | Cached immutable render data, no calculations on render thread |
| Optimizer proposes an invalid setup | High | Re-run every candidate through the authoritative economy engine |
| Background results race with ownership changes | High | State generations, cancellation, immutable snapshots |
| Scope expands before the engine is trusted | High | Milestone gates and optimizer/HQ comparison deferral |

## 10. Definition of done for the private build

The private build is successful when:

- spell profiles work reliably across normal character switching;
- no feature changes guild state or saved Wynntils bindings unexpectedly;
- all calculations state their freshness and evidence quality;
- own-guild production and routing agree with several manually checked states;
- loss-impact results are available without render-thread stalls;
- snapshots round-trip safely and remain read-only;
- every recommendation has an inspectable explanation;
- incompatibility disables affected features cleanly instead of crashing the client.
