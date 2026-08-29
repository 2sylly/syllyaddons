# Map overlay and loss-alert evidence rules

Track 8 renders Track 7 results at the point of use. It does not run routing, economy, or removal simulation work from
the guild-map render callback. Each frame takes one immutable cache view and performs only map lookups, filtering, and
drawing.

## Map severity

| Colour | Meaning |
| --- | --- |
| Grey | The completed report contains no changed route or non-zero resource delta. |
| Yellow | Minor scored impact. |
| Orange | Warning scored impact. |
| Red | Critical scored impact. |
| Purple | Catastrophic scored impact. |

The map can be limited to the observed player's guild, one foreign guild selected by exact name/tag/UUID, or all
observed owners. Optional predicates require a disconnection, a non-zero delta for one resource, or a minimum positive
delivery delay. Predicates compose; they do not change the underlying score. Hover text names the cache revision and
age and labels an older cache while a replacement generation is building.

## Ownership-loss matching

Ownership transitions are computed only between consecutive normalized states in the same known character and guild
session. Both owners must be known. Case differences in a guild UUID or name do not create a transition.

A transition becomes a loss alert only when all of the following hold:

1. the previous owner matches the observed player's guild;
2. the completed territory report has the exact previous state revision;
3. the report's complete Track 7 cache key matches a freshly derived key for that previous state; and
4. the report meets the configured severity threshold.

A report from an older revision is not treated as “close enough.” This can suppress an alert while a baseline is still
building, which is preferable to attributing a stale result to a new loss.

The alert shows the elapsed age of the cache build and the interval between the old/new owner evidence timestamps.
That interval is the available timing precision: the ownership change was observed during it, not necessarily at its end.
The large alert states this explicitly. Unknown session state, a character/world boundary, or a guild identity change
clears all queued alerts. Alert expiry is local and does not mutate any game or guild state.
