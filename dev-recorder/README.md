# Sylly Dev Recorder

This is a separate development-only Fabric mod for Minecraft 1.21.11 and Wynntils 4.2.9. It is not part of the normal
SyllyAddons JAR.

Build it from the repository root with `./gradlew -p dev-recorder build`.

## Use

1. Press **F9** to start recording.
2. Reproduce the menu, routing, keybind, or event behavior.
3. Press **F9** again to stop.
4. Find the resulting JSON-lines file under
   `config/sylly-dev-recorder/sessions/session-<UTC timestamp>.jsonl` in the Minecraft instance.

The file is flushed after every event so a game crash should still leave useful evidence. Each line has a timestamp,
elapsed time, sequence number, event type, and ordinary JSON data.

## Captured locally

- window and GUI dimensions plus pinned mod versions;
- screen/container class, title, dimensions, ID, and slot count;
- visible non-empty item IDs, names, counts, and tooltip/lore text;
- screen mouse clicks, coordinates, modifiers, and the hovered slot/item when available;
- Wynntils gameplay input-mapping changes (not raw GUI key presses), container content/slot events, world-state changes,
  and queued-war timer events.

## Privacy boundary

Recording is **off by default** and exists only between the two F9 presses. There is no upload, HTTP client, telemetry,
clipboard access, screenshot capture, chat-message capture, typed-character capture, or raw GUI-key capture. Item lore
and screen titles can still contain player, guild, territory, or inventory information, so review a session before
sharing it.
