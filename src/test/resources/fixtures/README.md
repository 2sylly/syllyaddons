# Golden fixtures

Files below this directory are immutable, version-labelled observations captured from a real upstream format. They are
test inputs only and must never be read from or overwritten by the live Minecraft config directory.

- `track4/wynntils-4.2.8-karoc-excerpt.json` is a minimized Wynntils 4.2.8 territory-topology capture. It preserves
  the upstream connection order needed by the FIFO routing parity test and intentionally retains an unknown link to
  exercise topology diagnostics.

Replace a golden fixture only as a reviewed change tied to a pinned Wynntils upgrade. Put temporary or generated test
state in the test method or a temporary directory, not here.
