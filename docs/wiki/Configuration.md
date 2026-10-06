# Configuration

The server config lives in `<world>/serverconfig/architectstrials-server.toml` (per world). In singleplayer it can
also be edited via the Mods menu.

Content — themes, structures, tiers, scrolls — is **never** configured here; that is done with datapacks (see
[Datapack Reference](Datapack-Reference)). The config only holds global options.

| Option | Default | Description |
|---|---|---|
| `slotSpacing` | 2048 | Distance between instance slots in a theme dimension. Only applies to theme dimensions without instances, so existing instances never move. |
| `maxConcurrentInstances` | 4 | Maximum simultaneous instances per theme dimension; `0` = unlimited. At the limit, scrolls fail without being consumed. Every running instance brings its own mobs and spawners — raise it if your server can handle more. |
| `structurePlacementY` | 64 | Y coordinate the bottom of every challenge structure is placed at (also the height of the editor platform and the target height of `/at theme tp` and `/at enter`). |
| `startingRank` | 1 | Rank every player starts with in every theme; `0` blocks all scrolls until the player is upgraded. |
| `defaultTimeLimitMinutes` | 60 | Time limit of challenges whose scroll defines none. |
| `portalTimeoutSeconds` | 60 | Seconds an opened portal waits for its player before collapsing (the scroll may then drop again, see below). |
| `unusedPortalScrollDropChance` | 0.5 | Chance (0.0–1.0) that the scroll drops back when a portal collapses unused. |
