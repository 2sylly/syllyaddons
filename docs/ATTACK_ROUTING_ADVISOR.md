# Attack-routing advisor evidence and interaction rules

Track 9 adds a decision panel to Wynncraft's existing attack-preparation flow. It appears only on the matching
`Attacking: <territory>` screen. Nothing runs an attack or changes routing in response to a timer, container update,
world event, or recommendation.

## Observed inputs

The exact supported Wynntils 4.2.9 source recognizes container titles shaped as `Attacking: <territory>`. SyllyAddons
listens to Wynntils' post-content and post-slot events, confirms that title, and reads visible item names/tooltips. The
parser accepts emeralds, Emerald Blocks, Liquid Emeralds, minute/second durations, clock durations, and the route glyphs
used by the live Attack item. Missing text remains missing.

The [official Wynncraft Wiki's guild-war documentation](https://wynncraft.wiki.gg/wiki/War) states that the attack menu
shows target difficulty, cost, timer, and the route used for the timer, and that a left click spends emeralds and starts
the timer. The click guard therefore identifies the Attack item by its name plus price/timer or click cues; it never
assumes a fixed inventory slot.

After the player queues an attack, SyllyAddons observes Wynntils' `GuildWarQueuedEvent`. Its timer is retained as
diagnostic validation of the previously displayed menu timer. It does not trigger a click, command, or routing change.

## Routing-mode recovery

Territory management remains the strongest local source for the current HQ routing mode. If that observation is
missing, Track 9 compares the displayed timer and any complete displayed route against both local candidates:

- exactly one matching candidate is recorded as derived routing evidence;
- a displayed timer longer than the locally calculated Fastest timer uniquely identifies Cheapest, even when unknown
  live taxes make the fallback Cheapest A* route differ from the game;
- two matches are ambiguous and produce an HQ-management prompt;
- other zero-match cases produce the HQ-management prompt instead of guessing.

Right-clicking the ambiguity prompt opens the observed HQ using the same `gu territory <headquarters>` path as
[Wynntils 4.2.9's territory-management holder](https://github.com/Wynntils/Wynntils/blob/v4.2.9/common/src/main/java/com/wynntils/screens/territorymanagement/TerritoryManagementHolder.java).
The action is limited to the panel and occurs only on the player's right-click; it does not select or change a mode.

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

The open menu's resolved-current-mode route, timer, and cost are authoritative observations. In particular, an
observed Cheapest route replaces the fallback-tax A* route for that attack comparison. Because public state does not
expose the attacking guild's current diplomacy tax for every owner, the alternate cost uses the same 70% foreign-tax
fallback as the existing observed-economy analyzer and is always labelled `estimated`.

## Recommendation and click guard

Fastest is recommended whenever its calculated queue time is at least one second shorter than Cheapest. There is no
minimum-saving or maximum-extra-cost threshold. The estimated cost delta remains visible so the player can judge it.

**Block click when faster queuing is available** is enabled by default and can be disabled under Routing Advisor. It
engages only when all of the following are true:

- the matching attack screen is still open;
- both candidates passed observation and consistency checks;
- Fastest has a strictly shorter queue;
- the player left-clicked the item positively identified as the Attack action.

The original click is cancelled and a modal states the exact saving. While it is open:

- right-click sends only the HQ-management command described above;
- shift-left-click sends one normal left click to the same container ID and Attack-slot index after revalidating that
  the item is still the Attack action;
- all other mouse clicks are consumed by the modal.

Changing screens invalidates the per-screen confirmation state. If the slot, item, container ID, evidence, setting, or
screen no longer matches, no confirmed attack click is sent.

## Refusal contract

The recommendation and click guard remain unavailable if any of these are missing or inconsistent:

- displayed target, cost, or timer;
- current guild or headquarters;
- target/topology or either candidate route;
- routing that is neither observed nor uniquely inferable;
- a complete displayed route whose own hop count disagrees with its displayed timer;
- a displayed timer shorter than the local shortest path, or a known Fastest timer that differs from that path by more
  than five seconds.

Live acceptance still requires several manual attack-menu comparisons on the pinned Wynntils 4.2.9 build. A
disagreement must keep advice and blocking unavailable and produce a rule/parser revision rather than being silently
accepted.
