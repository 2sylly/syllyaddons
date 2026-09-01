# Defence-sustainability optimizer model and safety rules

Track 10 is a bounded, read-only integer optimizer. Its output is a checklist for a person to inspect and apply
manually. It has no click, command, packet, menu-mutation, or `Apply` path.

## Input gate and fixed configuration

The optimizer requires a current guild, HQ, HQ routing mode, territory topology, production/storage for every owned
territory, exact observed upgrade levels for every owned territory, and HQ storage/capacity. An unknown upgrade key or
invalid level makes the model unavailable because silently assigning it zero upkeep would create a false saving.

The no-change current assignment is calculated first and retained as the baseline even when it violates a requested
constraint. Only these version-pinned, quantified effects from Wynntils 4.2.9 may become downgrade variables:

- `EFFICIENT_RESOURCES` and `RESOURCE_RATE` on each owned territory;
- `EFFICIENT_EMERALDS` and `EMERALD_RATE` on each owned territory;
- `RESOURCE_STORAGE` and `EMERALD_STORAGE` on the HQ only.

Every candidate level is an integer from zero through the currently observed level. Tower damage, attack, health,
defence, stronger minions, multi-attack, aura, volley, XP/tome/emerald seeking, gathering/mob bonuses, PvP bonuses, and
non-HQ storage are fixed. This is intentionally stricter than allowing selected defence constraints: all observed
combat and unquantified strategic choices are preserved.

## Candidate projection

Observed generation and storage already include the current upgrade multipliers. A candidate is projected by ratio:

`candidate value = observed value × candidate multiplier / observed-current multiplier`

Candidate upkeep is rebuilt from the pinned `UpgradeCatalog`; fixed upgrades remain in the totals. The projected
territory inputs are then passed to the normal `EconomyEngine`, including the current ordered graph, HQ, routing mode,
70% unobserved-foreign-tax fallback, opening storage, spending order, tax loss, deficits, and storage caps.

The configured reserve percentage creates a floor for each resource equal to that percentage of currently observed HQ
storage. `Require no deficits` additionally rejects any candidate with unfunded hourly upkeep. An impossible reserve
returns per-resource shortfalls and the closest checked candidate, clearly labelled as not a recommendation.

## Objectives and deterministic ordering

The same hard constraints apply to every objective:

- **Minimum expense:** minimize summed raw resource upkeep, then deficit, then prefer a larger minimum buffer.
- **Repair deficits:** minimize total deficit, then expense, then prefer a larger minimum buffer.
- **Max minimum buffer:** maximize the smallest ending resource buffer, then minimize deficit and expense.
- **Preserve reserves:** maximize minimum headroom above configured reserve floors, then minimize expense and deficit.

Raw emerald, ore, wood, fish, and crop units are summed without market conversion. That is a transparent research
choice, not an assertion that the resources have equal strategic value. Final ties prefer fewer changes and then a
stable lexical assignment signature.

## Bounded search and revalidation

Variables are ordered by maximum possible hourly saving, then territory/key. Levels are visited from lowest to current.
The depth-first search evaluates useful partial assignments with every unassigned variable left at its current level,
so it finds feasible reductions early. Completing the full tree proves optimality; otherwise the result states whether
the node or wall-clock limit stopped it and returns the best known feasible candidate.

Before display, the selected levels are projected again and independently recalculated using a fresh `EconomyEngine`.
Levels, feasibility, expense, deficit, buffer, and storage totals must agree within numeric tolerance. A disagreement
removes the recommendation rather than displaying an unverified checklist.

## User interface and lifecycle

The optimizer runs on a worker thread. Results carry their source state revision and become stale as soon as the
normalized observed state changes. The screen shows:

- input status and active constraints;
- baseline and candidate totals;
- termination reason, evaluated node count, elapsed time, optimality, and revalidation status;
- each manual `territory: upgrade before → after` step with hourly upkeep saving;
- infeasibility or missing-input explanations.

Changing the objective or pressing **Run optimizer** starts a new generation. Older work may finish internally but its
generation token prevents it from replacing the newest view. No optimizer method is registered to container clicks,
commands, packets, or guild mutation events.
