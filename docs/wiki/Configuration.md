# Configuration

The server config lives in `<world>/serverconfig/architectstrials-server.toml` (per world). In singleplayer it can
also be edited via the Mods menu.

Content — themes, structures, tiers, scrolls — is **never** configured here; that is done with datapacks (see
[Datapack Reference](Datapack-Reference)). The config only holds global options.

| Option | Default | Description |
|---|---|---|
| `slotSpacing` | 2048 | Distance between instance slots in a theme dimension. Only applies to theme dimensions without instances, so existing instances never move. |
| `maxConcurrentInstances` | 0 | Maximum simultaneous instances per theme dimension; `0` = unlimited. At the limit, scrolls fail without being consumed. |
| `structurePlacementY` | 64 | Y coordinate the bottom of every challenge structure is placed at (also the height of the editor platform). |
| `startingRank` | 1 | Rank every player starts with in every theme; `0` blocks all scrolls until the player is upgraded. |
| `defaultTimeLimitMinutes` | 60 | Time limit of challenges whose scroll defines none. |
| `portalTimeoutSeconds` | 60 | Seconds an opened portal waits for its player before collapsing (the scroll then drops with a 50 % chance). |
