# Wynntils mixin inventory

Sylly Addons has two client-only accessor mixins. The inventory is intentionally exact: adding another mixin requires updating
this document, the pinned compatibility checks, and the Wynntils upgrade smoke test.

## `AbstractMapScreenAccessor`

- **Target:** `com.wynntils.screens.maps.AbstractMapScreen` in Wynntils 4.2.9.
- **Type:** accessor only; it does not inject, overwrite, redirect, or change execution.
- **Fields read:** `mapCenterX`, `mapCenterZ`, `centerX`, `centerZ`, `zoomRenderScale`, `renderX`, `renderY`,
  `renderWidth`, `renderHeight`, `mapWidth`, and `mapHeight`.
- **Reason:** Wynntils 4.2.9 does not expose the world-to-screen transform or rendered viewport required to align a
  read-only route/impact layer with its guild map.
- **Consumers:** `RouteHighlightController`, `TerritoryImpactOverlayController`, and
  `TerritoryManagementAutoFocusController`.
- **Failure behavior:** the mixin is optional. Both consumers check that the accessor was applied before registering
  their render callback, so an accessor failure disables only Sylly Addons' map layer; settings, profiles,
  observation, snapshots, auditor, attack advisor, and optimizer remain available.
- **Remapping:** disabled because the target is a version-pinned Wynntils class, not a Minecraft mapped class.

## `AbstractContainerScreenAccessor`

- **Target:** Minecraft 1.21.11's `AbstractContainerScreen`.
- **Type:** accessor only; it does not inject, overwrite, redirect, or change execution.
- **Fields read:** `leftPos` and `topPos`.
- **Reason:** the optional attack-click guard must translate the mouse location into an inventory slot without assuming
  a fixed slot number.
- **Consumer:** `AttackAdvisorOverlayController`.
- **Failure behavior:** if the accessor is unavailable, route advice remains visible and normal clicks pass through; the
  optional click guard cannot identify the Attack item and therefore does not block anything.
- **Remapping:** enabled because this targets a Minecraft mapped class.

Neither mixin injects gameplay behavior. The controller may act only after the user explicitly right-clicks the guarded
dialog/prompt or shift-left-clicks the confirmation described in the Track 9 safety rules.
