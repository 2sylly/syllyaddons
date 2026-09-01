# Pinned Wynntils upgrade smoke test

Run this checklist for every proposed change from the pinned Wynntils build. Do not update the version or checksum
until every required item passes. Record the tested Minecraft, Fabric Loader, Fabric API, Wynntils, and Sylly Addons
versions with the result.

## Before launch

- [ ] Copy `settings.json` and `spell-profiles.json`, or confirm their `.bak` files are current.
- [ ] Update the local Wynntils JAR path and checksum only in a development branch.
- [ ] Run `./gradlew clean test performanceTest build`.
- [ ] Confirm the golden fixture remains unchanged unless a deliberate, reviewed live capture replaces it.
- [ ] Review every entry in [MIXINS.md](MIXINS.md) against the new Wynntils bytecode.

## Startup and isolation

- [ ] Launch Minecraft and reach the title screen without a mixin or initializer crash.
- [ ] Open **F6 → Compatibility → Operations & data health**.
- [ ] Confirm the pinned compatibility row is healthy and every expected subsystem appears separately.
- [ ] Disable Eco Auditor, Territory Impact, Routing Advisor, and Optimizer one at a time; each must show Disabled while
  unrelated rows remain healthy or waiting.
- [ ] Export a debug bundle. Inspect it before sharing and confirm it contains no character ID, nickname, guild
  identity, profile name, raw config, or raw log.

## Observation and characters

- [ ] Join Wynncraft and confirm a live observation revision appears.
- [ ] Open the class list once. Confirm exactly the account's characters appear as Class + Nickname + Level.
- [ ] Confirm the character list scrolls at a small window size and provisional entries merge into stable IDs.
- [ ] Open **F8** and verify character, guild, HQ, routing mode, territory topology, resources, and provenance update.
- [ ] Open the territory menu required for local economy/upgrades and verify Missing Data findings clear as expected.

## Spell profiles

- [ ] Open **F7** and switch between the Characters and Profiles tabs.
- [ ] Test keyboard and mouse spell bindings while no GUI/chat is open.
- [ ] Confirm no double cast occurs and Wynntils Quick Cast still supplies its normal delays and checks.
- [ ] Switch characters and confirm the assigned/fallback profile resolves once with the local swap message.
- [ ] Restart and confirm profiles, assignments, nicknames, levels, and automatic switching persist.

## Economy features

- [ ] Open the Eco Auditor and inspect at least one formula/provenance drill-down.
- [ ] Build the territory-impact cache and confirm every target completes without blocking rendering.
- [ ] Open the guild map and confirm impact regions and route highlights align through zoom and pan.
- [ ] Open territory management for a guild with no HQ; confirm map mode centers all held territories and uses the
  closest zoom that keeps every held territory inside the map border.
- [ ] Open an attack menu and confirm the panel exists only on that screen and parses the live Attack item's route,
  `Price`, and `Time to Start` lines.
- [ ] With HQ routing unknown, confirm a uniquely matching timer records the inferred mode; an ambiguous timer asks for
  HQ management without guessing.
- [ ] Right-click the ambiguity prompt and confirm the observed HQ management menu opens without changing routing.
- [ ] With Fastest strictly quicker and click blocking enabled, left-click Attack and confirm the modal shows the exact
  saving. Confirm ordinary clicks remain blocked, right-click opens HQ management, and shift-left-click attacks once.
- [ ] Confirm equal-time, unavailable, disabled-setting, non-Attack-slot, and failed-accessor cases never block a click.
- [ ] Queue one attack and confirm timer validation reports match or a clear Calculation Disagreement.
- [ ] Run the optimizer; confirm baseline retention, bounded termination, independent revalidation, and no Apply action.
- [ ] Export/import/compare a snapshot and confirm imported data stays read-only.

## Failure-path checks

- [ ] With an incomplete menu scan, confirm the operations screen says Missing Data rather than Integration Failure.
- [ ] Exercise a timer mismatch fixture and confirm it says Calculation Disagreement.
- [ ] Confirm a failed optional map accessor disables the map layer without disabling unrelated screens or listeners;
  a failed container-position accessor must fail the click guard open.
- [ ] Restore the pinned build and re-run the normal launch check before merging.

## Current pin

- Minecraft: **1.21.11**
- Wynntils: **4.2.9**
- Wynntils SHA-256: `faf32c32c5ce3af3b7236a19c5b8b8c6fb44695bf4340a9083bd2eae744858ef`

## 4.2.8 → 4.2.9 audit record

- Official release: `v4.2.9`, published 2026-08-29.
- Tag commit: `9a3562a40feb7574fca1a8045cefc43b62c66007`.
- Published Fabric artifact SHA-256 matched the downloaded JAR.
- Every directly imported Wynntils class retained the same public API.
- `AbstractMapScreen` retained every private field used by the optional accessor.
- `QuickCastFeature`, `TerritoryUpgrade`, `TerritoryItem`, and `AbstractMapScreen` source files were unchanged.
- Automated unit, performance, and build checks passed after repinning.
- In-game items above remain manual and must be exercised on the next launch.
