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
- [ ] Open an attack menu and compare the observed/current timer and cost with the passive advisor.
- [ ] Queue one attack manually and confirm timer validation reports match or a clear Calculation Disagreement.
- [ ] Run the optimizer; confirm baseline retention, bounded termination, independent revalidation, and no Apply action.
- [ ] Export/import/compare a snapshot and confirm imported data stays read-only.

## Failure-path checks

- [ ] With an incomplete menu scan, confirm the operations screen says Missing Data rather than Integration Failure.
- [ ] Exercise a timer mismatch fixture and confirm it says Calculation Disagreement.
- [ ] Confirm a failed optional map accessor disables the map layer without disabling unrelated screens or listeners.
- [ ] Restore the pinned build and re-run the normal launch check before merging.

## Current pin

- Minecraft: **1.21.11**
- Wynntils: **4.2.8**
- Wynntils SHA-256: `00369b5950a9522b8feed3122a4ec15dd581347c8fa868670650b76bac1380f6`
