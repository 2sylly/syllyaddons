# Territory-impact rule ledger

Track 7 asks one read-only question: what changes if a named territory is removed from the currently observed graph?
It never edits ownership, routes, HQ settings, towers, or upgrades.

## Simulation boundary

For every target, the simulator creates a new immutable observation with that territory and every reference to it
removed. It rebuilds a normalized graph and reruns the Track 4 route and economy engines from scratch. A route that
still contains the removed territory can therefore never leak into the result.

Owned territories are classified per routing mode as:

- `UNCHANGED`: the selected before/after paths are identical;
- `REROUTED`: a different valid path is selected;
- `DISCONNECTED`: a previously routed source, or the removed source itself, has no route to HQ;
- `NEWLY_CRITICAL`: the removal creates one or more new territory separators between that source and HQ.

Chokepoints are computed with an undirected low-link traversal, not by trusting only the currently selected path. When
the live routing mode is unknown, complete Cheapest and Fastest baseline/removal branches are calculated separately.

## Certainty

- Connectivity and chokepoints are `EXACT_OBSERVATION` only when every connection list has public-exact or local-exact
  evidence and graph normalization reports no anomaly.
- Selected routes are estimated because the Track 4 route ordering and tax rules still require live parity captures.
- HQ delivery, tax, tower supply, deficits, storage, and both severity scores are estimated.
- Foreign targets receive no enemy HQ, production, upgrade, tower, or strategic values. Their reports explicitly list
  those inputs as unavailable and score only disruption to the observed guild network.

## Severity rules

Scores are capped at 100 and map to `MINOR` below 20, `WARNING` from 20, `CRITICAL` from 45, and `CATASTROPHIC` from
70. Every contribution retains its input, weight, cap formula, and resulting points in the report.

Defensive scoring weights HQ loss, dependent disconnections, reroutes, new chokepoints, added delivery time, lost HQ
delivery, and increased tower deficits. Offensive scoring weights structural disconnections, reroutes, new
chokepoints, added delivery time, lost delivery, and added tax. The removed territory's own tautological disconnection
does not add severity; its actual production and expense effects remain in the economy delta.

These weights are Sylly Addons advisory rules, not Wynncraft server values.

## Cache contract

The SHA-256 key includes guild ownership, ordered links, territory bounds, HQ, selected/unknown routing mode, rule and
tax versions, production, storage, and upgrades. Evidence age and repository revision do not invalidate an otherwise
identical calculation.

Every request runs on a background executor. A changed key increments the cache generation and cancels prior work.
Every target boundary and final publication checks the generation again, so even a task that ignores interruption
cannot replace newer results. UI and future map-hover callers only use `lookupCompleted`, an immutable map lookup that
does no routing or economy work.
