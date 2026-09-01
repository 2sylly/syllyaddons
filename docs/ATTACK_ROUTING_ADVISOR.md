# Attack-routing advisor evidence and safety rules

Track 9 adds a read-only decision panel to Wynncraft's existing attack-preparation flow. It does not open the menu,
click an item, send a command or packet, change HQ routing, spend emeralds, or queue a war.

## Passive inputs

The exact supported Wynntils 4.2.9 source already recognizes container titles shaped as
`Attacking: <territory>`. Sylly Addons listens only to Wynntils' post-content and post-slot events, confirms that title,
and reads the visible item names/tooltips. The parser accepts emeralds, Emerald Blocks, Liquid Emeralds, minute/second
durations, clock durations, and an explicitly labelled route/path. Missing text remains missing.

The [official Wynncraft Wiki's guild-war documentation](https://wynncraft.wiki.gg/wiki/War) states that the attack menu
shows target difficulty, cost, timer, and the route used for the timer, and that a left click is the action that spends
emeralds and starts the timer. This is why Track 9 treats inventory contents as evidence but never observes or injects
mouse clicks.

After the player queues an attack themselves, Sylly Addons passively observes Wynntils'
`GuildWarQueuedEvent`. Its timer is compared with the menu timer using a five-second tolerance and displayed for ten
seconds. This validates an already-completed player action; the advisor cannot cause the event.

## Calculation boundary

Both candidates use Track 4's versioned routing graph and research rule set:

- Fastest is FIFO breadth-first search over the observed ordered topology;
- Cheapest is the current A* research hypothesis (`1 + tax` edge weight, `distance / 12831` heuristic, FIFO ties);
- an attack timer is modelled as one base minute plus one minute per route edge;
- base emerald cost is `0 / 200 / 800 / 2000 / 4000` for `0 / 1 / 2 / 3 / 4+` owned territories;
- route tax compounds across intermediate foreign territories; the HQ, own territories, and attacked target are
  excluded.

The base costs, intermediate-route tax behavior, target exemption, and Cheapest/Fastest HQ-routing roles are documented
by the [official Wynncraft Wiki](https://wynncraft.wiki.gg/wiki/War). The precise route algorithms, timer formula, API
connection order, and unobserved diplomacy tax rates are still research assumptions. See
[the Track 4 rule ledger](ROUTING_AND_ECONOMY_RULES.md).

The open menu's current-mode timer and cost are authoritative observations. Because public state does not expose the
attacking guild's current diplomacy tax for every owner, the alternate cost uses the same 70% foreign-tax fallback as
the existing observed-economy analyzer and is always labelled `estimated`.

## Recommendation rules

The panel calculates Fastest's non-negative time saving and signed additional emerald cost relative to Cheapest. User
settings then select one of four explanations:

- no significant difference when both deltas are within the configured negligible thresholds;
- Cheapest adds negligible delay when the time saving is below the configured minimum or negligible-time threshold;
- Fastest is worth it when the saving is meaningful and extra cost is within the configured maximum;
- Cheapest avoids too much extra cost otherwise.

Defaults are a 60-second minimum saving, 32,768-emerald maximum extra cost, 30-second negligible delay, and 64-emerald
negligible cost. `Active operations only` hides historical advice after the attack screen and ten-second queued
validation end; disabling it retains the last panel for at most 30 seconds.

## Refusal and safety contract

The recommendation is unavailable if any of these are missing or inconsistent:

- displayed target, cost, or timer;
- current guild, headquarters, or HQ routing mode;
- target/topology or either candidate route;
- a displayed route that disagrees with the locally calculated current-mode route;
- a displayed timer that differs from the current-mode route model by more than five seconds.

Source-level safety checks cover the Track 9 packages for click, command, and packet APIs. The only integration methods
consume post-observation container events, a post-queue timer event, and world-state changes. Live acceptance still
requires several manual attack-menu comparisons on the pinned Wynntils 4.2.9 build; a disagreement must keep advice
unavailable and produce a new rule/parser revision rather than being silently accepted.
