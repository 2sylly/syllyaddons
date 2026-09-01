# `.tnsreco` snapshot format

Track 5 uses UTF-8 JSON with the extension `.tnsreco`. Format version 1 is intended for portable, read-only inspection;
it is not a configuration file and importing one has no path to the live observation repository or any guild action.

## Version 1 envelope

```json
{
  "formatVersion": 1,
  "createdAtEpochMillis": 1787994000000,
  "sourceVersions": {
    "minecraft": "1.21.11",
    "wynntils": "4.2.9",
    "syllyaddons": "0.1.0-dev",
    "routingRules": "routing-research-2026-08-29.1",
    "economyRules": "economy-research-2026-08-29.1"
  },
  "payload": {
    "observed": {},
    "economy": null,
    "analysisDiagnostics": []
  },
  "checksumSha256": "64 lowercase hexadecimal characters"
}
```

The checksum covers the canonical JSON representation of every root field except `checksumSha256`. Object keys are
sorted recursively, array order is preserved, and the UTF-8 canonical bytes are hashed with SHA-256. Reformatting or
reordering object keys does not invalidate the checksum; changing values or array order does.

`payload.observed` preserves the normalized `EcoSnapshot`, including evidence and ordered territory links.
`payload.economy` contains the rule-versioned Track 4 result and resource-provenance ledger when sufficient inputs were
available. It is `null` when HQ, guild, routing mode, or territory inputs are missing. `analysisDiagnostics` explains
missing inputs and every estimation boundary.

## Defensive import

Before a context is returned, the importer checks:

- regular-file status and a 16 MiB maximum size;
- exact format and observed-state schema versions;
- required and unknown root fields;
- checksum syntax and constant-time checksum equality;
- creation/evidence timestamps and bounded text fields;
- at most 1,000 territories and bounded link, upgrade, alert, provenance, and diagnostic lists;
- territory-map keys, links, duplicate/self/unknown targets, HQ membership, and world-coordinate bounds;
- enum presence, resource keys, finite non-negative numeric values, tax rates, and delivery-time limits;
- provenance sources, complete routes, tax steps, HQ destination, consumers, and spending amounts.

Unknown future format versions fail with a direct message. Version 1 has no migration because no older format exists;
a migration will be added only when the format first changes.

## Writing and retention

Exports are written to a unique temporary file in the destination directory and then replaced with an atomic move when
the filesystem supports it. Manual exports are named with UTC millisecond precision and the observed revision.
Automatic exports are throttled to once per five minutes and only automatic files beyond the configured retention
limit are deleted; manual snapshots are never pruned automatically.

## Inspector behavior

Settings → Snapshots opens a scrollable local-file list. Dropping a portable `.tnsreco` file into the opened snapshot
folder and pressing Refresh imports it. The inspector can:

- compare normalized ownership, topology, resources, HQ, and economy totals with current observations;
- select each resource and production source;
- display gross production, complete route, every tax step, delivery time, destination, consumers, storage, overflow,
  undelivered amount, confidence, and diagnostics;
- open Wynntils' guild map with the inspected route drawn in cyan.

The current research rule versions remain estimated as documented in
[`ROUTING_AND_ECONOMY_RULES.md`](ROUTING_AND_ECONOMY_RULES.md); exporting them does not upgrade their confidence.
