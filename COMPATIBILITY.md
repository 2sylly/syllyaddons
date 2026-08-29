# Compatibility

The current private build is intentionally pinned to one client stack.

| Component | Supported value |
|---|---|
| Minecraft | 1.21.11 |
| Java | 21 |
| Loader | Fabric Loader 0.19.3 |
| Fabric API | 0.141.5+1.21.11 |
| Wynntils | 4.2.8 Fabric |
| Wynntils SHA-256 | `00369b5950a9522b8feed3122a4ec15dd581347c8fa868670650b76bac1380f6` |

The development dependency is configured in the ignored `local.properties` file. Copy
`local.properties.example` when setting up another machine. Gradle verifies the configured JAR checksum before
compilation.

Wynntils-facing code belongs under `compat/wynntils/v4_2_8`. If the pinned version changes,
validate or replace that adapter instead of allowing Wynntils types to leak into the core domain.
