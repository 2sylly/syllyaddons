# Compatibility

The current private build is intentionally pinned to one client stack.

| Component | Supported value |
|---|---|
| Minecraft | 1.21.11 |
| Java | 21 |
| Loader | Fabric Loader 0.19.3 |
| Fabric API | 0.141.5+1.21.11 |
| Wynntils | 4.2.9 Fabric |
| Wynntils SHA-256 | `faf32c32c5ce3af3b7236a19c5b8b8c6fb44695bf4340a9083bd2eae744858ef` |

The development dependency is configured in the ignored `local.properties` file. Copy
`local.properties.example` when setting up another machine. Gradle verifies the configured JAR checksum before
compilation.

Wynntils-facing code belongs under `compat/wynntils/v4_2_9`. If the pinned version changes,
validate or replace that adapter instead of allowing Wynntils types to leak into the core domain.

The 4.2.9 pin uses the official Fabric artifact from the
[Wynntils v4.2.9 release](https://github.com/Wynntils/Wynntils/releases/tag/v4.2.9), tag commit
`9a3562a40feb7574fca1a8045cefc43b62c66007`. Its public adapter API, private map-accessor fields, Quick Cast source,
territory upgrade/item source, and map-screen source were compared directly with 4.2.8 before changing the pin; all
Sylly Addons integration surfaces were unchanged.
