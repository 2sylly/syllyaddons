# Sylly Addons

Personal-use Fabric companion mod for Minecraft 1.21.11 and Wynntils 4.2.8.

The current implementation includes the Track 1 observation pipeline, Track 2 per-character spell profiles, the Track
3 configuration shell, the synthetic portion of Track 4's routing/economy truth engine, Track 5 portable snapshots
and provenance inspection, the Track 6 explainable eco auditor, and the Track 7 cached territory-removal simulator. It
collects and analyses available state without issuing commands, clicking menus, or changing guild state.

## Development setup

Requirements:

- Java 21;
- Minecraft 1.21.11;
- Fabric Loader 0.19.3;
- Fabric API 0.141.5+1.21.11;
- the exact supported Wynntils 4.2.8 Fabric JAR listed in [COMPATIBILITY.md](COMPATIBILITY.md).

Configure the local Wynntils path:

```shell
cp local.properties.example local.properties
```

Edit `local.properties`, then build and test:

```shell
./gradlew test build
```

The remapped mod JAR is written under `build/libs`.

## Track 1 behavior

The observation pipeline currently:

- reads the active Wynntils character ID, class, and guild;
- reads Wynntils territory ownership and bounds;
- fetches the current public territory endpoint for HQ, ownership, links, public resources, treasury, and defences;
- passively captures routes, production, storage, upgrades, alerts, and HQ status when Wynntils parses them in normal UI use;
- passively recognizes an explicitly labelled current HQ routing mode;
- merges partial observations conservatively using evidence quality and timestamps;
- publishes a new state revision only when normalized state or its evidence changes;
- produces field-level missing, stale, and estimated-data findings;
- exposes a read-only F8 data-status screen with every normalized field, evidence source, and observation age;
- retains the latest useful state at `config/syllyaddons/latest-observed-state.json` as historical data.

The public territory request runs every 15 seconds while the player is in a Wynncraft world. Public resource values are
marked delayed because their upstream refresh interval is slower than ownership data.

## Track 2 spell profiles

Press **F7** to open the two-tab profile manager. On the first launch, Sylly Addons imports the four current Wynntils
Quick Cast bindings into an `Imported Wynntils` profile and makes it the global default.

Opening Wynncraft's character-selection menu once locally scans every character card. The **Characters** tab then shows
each card's class, nickname, and level, assigns profiles directly, and can pause automatic switching without losing the
current profile. A card is linked to its stable character ID when selected; this preserves assignments without trusting
an incomplete public account response. Invalid transient IDs are pruned automatically. The character-card region
supports mouse-wheel scrolling and a draggable scrollbar when the screen cannot fit every character. The **Profiles**
tab keeps the profile list and spell editor visible together. It supports:

- temporary, remembered, character, class-fallback, and global selections;
- creating, renaming, duplicating, deleting, and importing profiles;
- keyboard and mouse-button capture for spells 1–4;
- a client-side `[SyllyAddons] Swapped to Profile …!` chat notice when the resolved profile actually changes;
- conflict warnings against current Minecraft and Wynntils controls.

Profile resolution is: temporary/remembered manual override, character assignment, class fallback, global default,
then the current profile. Profiles are stored atomically at `config/syllyaddons/spell-profiles.json`.

During normal gameplay a configured physical input is claimed before native key mappings, preventing a matching
Wynntils binding from casting a second spell. Input is never claimed while chat, a menu, the profile manager, or the
controls screen is open. Wynntils Quick Cast must remain enabled because its own weapon checks, class click direction,
delays, cooldown, and adaptive lag correction are deliberately reused.

## Track 3 settings

Press **F6** or use Sylly Addons' Mod Menu config button to open the searchable settings shell. Profiles, Characters,
Eco Auditor, Territory Impact, Routing Advisor, Optimizer, Snapshots, Notifications, and Compatibility are all
reachable there. Implemented fields save immediately, validate before writing, and provide field and section resets.
Feature sections from later tracks expose their enablement preferences now, ready for their analysis panels.

Settings are stored at `config/syllyaddons/settings.json`. Both settings and spell profiles use atomic replacement and
retain the previous readable file as `.bak`. A broken file is renamed with a `.corrupt-<timestamp>.json` suffix,
replaced with safe defaults, and surfaced as an in-game warning.

## Track 4 routing and economy engine

Track 4 now has an immutable ordered territory graph, explicit topology diagnostics, Fastest and Cheapest candidate
routes, tax and delivery-time accounting, same-owner reachability checks, and per-resource provenance through HQ
expenses and storage. The engine is pure Java and covered by synthetic fixtures plus a small topology observation from
Wynntils 4.2.8.

The proposed server algorithm and economy semantics are still research assumptions. Results carry a rule version,
confidence, and diagnostics and cannot be labelled exact under the default rules. See
[the routing/economy rule ledger](docs/ROUTING_AND_ECONOMY_RULES.md) for the precise boundary and remaining live golden
captures.

## Track 5 snapshots and provenance

Open **Settings → Snapshots → Open snapshots** to export the current state, import local `.tnsreco` files read-only,
compare them with current observations, and inspect resource provenance by source. The inspector shows the complete
route, per-step tax, delivery time, HQ destination, consumers, storage, overflow, confidence, and diagnostics. **Show
on map** opens Wynntils' guild map with the inspected route highlighted.

Snapshots include source/rule versions and a canonical SHA-256 checksum, are replaced atomically, and are rejected for
future schemas, corruption, oversized input, invalid enums, bad links, or unbounded numeric data. Automatic snapshots
are optional, limited to once every five minutes, and prune only older `auto-` files according to the configured
retention count. Manual exports are never automatically removed. See [the format specification](docs/TNSRECO_FORMAT.md).

## Track 6 eco auditor

Open **Settings → Eco Auditor → Open auditor** to inspect actionable findings. The report covers negative production,
upkeep deficits, undelivered production, chokepoints, simultaneous overflow/deficit, storage/treasury risk, costly
routes, dominated or low-value economic upgrades, and potentially safe one-level downgrades. Each finding keeps its
formula, numeric inputs, affected territories, evidence age/source, known missing inputs, and production provenance.
Findings with the same root cause are merged rather than repeated. Routed provenance can be highlighted on the
Wynntils guild map.

The passive territory-menu scan now supplies exact Wynntils 4.2.8 upgrade levels and version-pinned hourly costs.
Expense-dependent checks are withheld until all owned territory upgrade lists are known. Tax, server spending order,
cross-resource value, and upgrade strategic value remain explicit assumptions. Warning/critical findings produce one
cooldown-controlled chat summary when the Eco Auditor setting is enabled. See
[the auditor rule ledger](docs/ECO_AUDITOR_RULES.md).

## Track 7 territory-impact simulator

Open **Settings → Territory Impact → Open simulator** to inspect the cached consequence of removing any observed
territory. Each result shows disconnected, rerouted, unchanged, and newly critical owned routes; before/after delivery
time; per-resource HQ delivery, tax, tower-supply, deficit, and storage deltas; and separate defensive and offensive
severity scores. If the live HQ routing mode is unknown, Cheapest and Fastest are simulated independently.

The cache key covers ownership, ordered topology, bounds, HQ, routing mode, tax assumptions, production, storage, and
upgrade inputs. Builds run off the render thread, old work is cancelled and generation-checked, and consumers can only
look up immutable completed maps in constant time. A previous completed map can remain visible while a new generation
builds, but is labelled stale. Connectivity and chokepoints can be exact when topology evidence is complete; selected
routes and all economy/severity results remain estimated. Enemy economy is never invented. See
[the impact rule ledger](docs/TERRITORY_IMPACT_RULES.md).

## Safety

Track 1 has no code path that sends a command, clicks a container slot, queues an attack, changes HQ routing, or modifies
guild configuration. Container and advancement handlers consume post-observation events only.

## Current limitations

- The F8 data-status screen is intentionally a plain provenance inspector; it is also reachable from Compatibility.
- The routing-mode text recognizer is deliberately strict and still needs validation against the live HQ screen.
- Track 4's route ordering, current tax semantics, delivery timing, expense order, and storage behavior still need
  side-by-side live golden captures before the engine can report exact server parity.
- Track 6 upgrade recommendations compare observed marginal output or headroom against version-pinned upkeep. They do
  not know player strategy, defence plans, treasury growth, or cross-resource market weights and are always advisory.
- Track 7 offensive scores describe structural disruption to the observed guild's network. Enemy HQ, upgrades,
  production, tower supply, and strategic intent remain unavailable and cannot appear as exact.
- Track 2 still needs repeated live character-switch and keyboard/mouse casting validation; the persistence and
  resolver paths are covered by automated tests.
- The official territory endpoint is needed for fields that Wynntils 4.2.8 downloads but does not retain in its public
  territory profile model.
- The compatibility guard disables the addon if the Wynntils version or JAR checksum differs from the tested build.
