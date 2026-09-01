# Eco auditor rule ledger

Track 6 is read-only. It reports observations and calculations but never clicks an upgrade, changes a route, sends a
guild command, or modifies territory configuration.

## Input boundary

- Upgrade names, levels, hourly costs, storage bonuses, and production multipliers are pinned to the supported
  [Wynntils 4.2.9 `TerritoryUpgrade`](https://github.com/Wynntils/Wynntils/blob/9a3562a40feb7574fca1a8045cefc43b62c66007/common/src/main/java/com/wynntils/models/territories/type/TerritoryUpgrade.java)
  and [`TerritoryItem`](https://github.com/Wynntils/Wynntils/blob/9a3562a40feb7574fca1a8045cefc43b62c66007/common/src/main/java/com/wynntils/models/items/items/gui/TerritoryItem.java)
  source. The catalog version is `wynntils-4.2.9-territory-upgrades`. These two source files are unchanged from 4.2.8.
- Upgrade expense checks run only when every owned territory has a locally observed upgrade list and every upgrade key
  exists in that catalog.
- Foreign tax remains an estimate when the game does not expose an explicit edge tax schedule. The default analyzer
  uses the Track 4 research assumption of 70% foreign tax.
- Evidence timestamps, weakest evidence kind, source names, and source versions remain attached to every finding.

## Default thresholds and formulas

- Negative net production: `delivered production - observed upgrade upkeep < 0`.
- Upkeep deficit: a consumer's `required - supplied > 0` after the Track 4 spending calculation.
- Undelivered production: `gross production - delivered to HQ > 0` with no complete selected route.
- Chokepoint: removing an intermediate territory makes its production source unreachable from HQ in the normalized
  graph. A producer with one observed connection is also single-route fragile.
- Simultaneous surplus/deficit: the same resource has both positive unmet upkeep and positive overflow.
- Storage risk: current HQ fill is at least 90%, or the hourly calculation produces overflow. Treasury risk covers
  `NONE`, `VERY_LOW`, and `LOW` ratings.
- Costly route: at least six hops, at least 35% gross production lost to tax, or an estimated Cheapest candidate saves
  tax versus the selected route. The latter can be an intentional Fastest-mode tradeoff and is labelled accordingly.
- Dominated production upgrade: its targeted observed production is zero while its current level has upkeep.
- Low-value economic tier: its one-level marginal observed benefit divided by marginal upkeep saving is below 0.25.
- Potentially safe production downgrade: estimated net after the one-level output loss retains at least 20% of current
  delivered production. A storage downgrade must retain at least 20% headroom.

Every numeric conclusion is stored as a structured calculation containing its label, formula, named inputs, result,
and unit. The UI displays all of them.

## Recommendation limits

The low-value ratio treats resource units equally because no reliable live cross-resource value vector is observable.
Upgrade findings do not include strategic defence, territory importance, treasury growth, future route changes, or a
guild's intended loadout. They are review prompts, not instructions. Findings explicitly list these missing inputs and
all automatic notices are informational client-side chat messages.
