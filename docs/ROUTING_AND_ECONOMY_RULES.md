# Routing and economy rule ledger

This document describes rule set `routing-research-2026-08-29.1` and economy rule set
`economy-research-2026-08-29.1`. They are independent of the observation snapshot schema so that a corrected rule can
be selected without rewriting captured data.

## Evidence boundary

The [official Wynncraft API repository](https://github.com/Wynncraft/WynncraftAPI) identifies the territory API but does
not document the server's route-selection implementation or promise that a returned connection array has the same
order as the game's internal connection list. The
[Wynntils 4.2.8 source](https://github.com/Wynntils/Wynntils/tree/v4.2.8) retains server-provided trading routes,
production, and storage for display; no client-side server-equivalent route/economy calculator was found there.

The current Cheapest hypothesis came from user-supplied community research:

- A* search;
- Euclidean territory-centre distance divided by `12831.0` as the heuristic;
- edge selection cost `1 + taxRate`;
- no secondary tie-breaker, leaving equal-priority candidates FIFO;
- connection-list order may determine FIFO results.

The implementation deliberately preserves each observed connection array. Whether the API and the server expose the
same order remains **unverified**. Consequently the shipped research rules never produce an `exact` result, even when
all input fields are present.

## Implemented routing rules

`Fastest` uses FIFO breadth-first search with a cost of one per edge. `Cheapest` uses A* with the hypothesis above. Both
algorithms visit neighbours in observed order and retain the first predecessor when a later candidate has equal cost.
Delivery time is currently modelled as `60 seconds * edge count`.

Topology is normalized as an undirected graph. A missing reverse edge is appended after source-declared edges so the
graph remains usable, while an `ASYMMETRIC_LINK` diagnostic prevents silent exactness. Unknown targets, self-links,
duplicates, and entirely missing connection lists receive their own diagnostics. Same-owner reachability is available
only as a connectivity diagnostic; route selection may cross other owners.

Tax rates are inputs rather than hard-coded truth. An explicit policy is exact with respect to its supplied numbers. A
fallback owner policy can assume zero tax through owned territory and a caller-selected foreign rate, but every such
quote is tagged `RESEARCH_ASSUMPTION`.

## Implemented economy rules

The pure-Java economy engine creates a ledger lot for each resource produced by each territory. A lot records:

1. source territory and gross hourly production;
2. selected route and delivery time;
3. amount before and after every tax step;
4. tax loss and amount delivered to HQ;
5. every territory expense paid from that lot;
6. amount retained in HQ storage, overflow, or amount left undelivered by a disconnected graph.

The research rule set compounds each edge's tax against the amount remaining after the previous edge. Opening HQ
storage pays expenses before newly delivered production; expenses are processed in input-territory order; remaining
resources are capped by the supplied HQ storage limit. These choices are deterministic and testable, but are not yet
confirmed as current server semantics.

## Confidence contract

A result is exact only if all of the following hold:

- algorithm and economy rule sets were explicitly validated;
- every tax on the selected route was explicitly supplied;
- no graph repair or missing topology can affect the result;
- the route exists and no other diagnostic was produced.

The default research rule sets intentionally fail the first condition. Unknown routing mode returns both Cheapest and
Fastest candidates and selects neither.

## Fixtures and missing validation

Synthetic fixtures cover lines, FIFO forks, cycles, disconnected branches, multiple HQ entries, rerouting after a tax
change, Cheapest-versus-Fastest divergence, asymmetric topology, unknown routing mode, compounding tax provenance,
storage overflow, and resource deficits.

`src/test/resources/fixtures/track4/wynntils-4.2.8-karoc-excerpt.json` is a small topology-only observation captured
from Wynntils 4.2.8. Ownership was deliberately omitted. It verifies preservation of real connection ordering and
anomaly reporting, but it is **not** a golden route/economy fixture.

Track 4 still needs live, side-by-side captures of the game's chosen routes, tax values, delivery timing, production,
expenses, and storage totals. Those expected outputs will become versioned golden fixtures; disagreements must update
diagnostics or produce a new rule version instead of being hidden by a tolerance change.
