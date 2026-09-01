# Wynntils mixin inventory

Sylly Addons has one client-only mixin. The inventory is intentionally exact: adding another mixin requires updating
this document, the pinned compatibility checks, and the Wynntils upgrade smoke test.

## `AbstractMapScreenAccessor`

- **Target:** `com.wynntils.screens.maps.AbstractMapScreen` in Wynntils 4.2.8.
- **Type:** accessor only; it does not inject, overwrite, redirect, or change execution.
- **Fields read:** `mapCenterX`, `mapCenterZ`, `centerX`, `centerZ`, `zoomRenderScale`, `renderX`, `renderY`,
  `renderWidth`, and `renderHeight`.
- **Reason:** Wynntils 4.2.8 does not expose the world-to-screen transform or rendered viewport required to align a
  read-only route/impact layer with its guild map.
- **Consumers:** `RouteHighlightController` and `TerritoryImpactOverlayController`.
- **Failure behavior:** the mixin is optional. Both consumers check that the accessor was applied before registering
  their render callback, so an accessor failure disables only Sylly Addons' map layer; settings, profiles,
  observation, snapshots, auditor, attack advisor, and optimizer remain available.
- **Remapping:** disabled because the target is a version-pinned Wynntils class, not a Minecraft mapped class.

No mixin accesses gameplay actions, container clicks, commands, attack queueing, or guild configuration.
