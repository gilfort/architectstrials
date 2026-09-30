# Architect's Trials

A NeoForge mod for Minecraft 26.3. Players open portals with scrolls into themed challenge dimensions,
fight and loot inside hand-built structures and always return without losing their inventory.
All content (themes, structures, tiers) is defined by modpack creators via datapacks and an in-game
editor — no Java code required.

## For modpack creators

### Challenge themes

A challenge theme **is** a dimension. The dimension defines the thematic environment (sky, fog, light,
biome ambience); all challenge instances of the theme are placed inside it.

Defining a theme takes two files in a datapack:

**1. The dimension** — `data/<namespace>/dimension/<theme>.json` (vanilla format, one file per dimension):

```json
{
  "type": "architectstrials:challenge",
  "generator": {
    "type": "minecraft:flat",
    "settings": {
      "biome": "minecraft:the_void",
      "features": false,
      "lakes": false,
      "layers": [],
      "structure_overrides": []
    }
  }
}
```

`architectstrials:challenge` is the default dimension type shipped with the mod: fixed time of day,
no weather, beds and respawn anchors do not work. You may use your own `dimension_type` instead,
e.g. to give a Nether theme a Nether sky. Choose a biome for its ambience (particles, sounds); natural mob
spawning is always disabled in challenge dimensions — enemies only come from the structure's spawn markers.

**2. The theme list** — `data/<namespace>/architectstrials/challenge_dimensions.json`:

```json
{
  "replace": false,
  "values": ["<namespace>:<theme>"]
}
```

All list files of all active datapacks are merged like tags. `"replace": true` discards entries from
lower-priority packs. Entries pointing to a dimension that does not exist are logged and skipped.

> **Restart required:** Minecraft only creates datapack dimensions on server start. After adding a new
> theme dimension, restart the server (or reopen the singleplayer world). `/reload` only picks up changes
> to the theme list.

**Display name:** themes use the lang key `dimension.<namespace>.<theme>`. Datapacks cannot ship lang
files, so provide the translation via a resource pack; otherwise the theme id is shown.

### Challenge structures & tiers

Each theme has one structure pool per tier. When a challenge instance is created, one structure is drawn
once (weighted) from the pool, placed centered in a free slot with its bottom at `structurePlacementY`,
and the instance stays bound to it — later entries never re-roll. The available tiers of a theme are
simply the tiers that have at least one structure.

The in-game editor (later) writes these files for you into `<world>/datapacks/architectstrials_structures/`;
the format for reference:

```
data/<ns>/structure/challenges/<theme>/tier_<n>/<id>.nbt           structure template (vanilla format)
data/<ns>/architectstrials/challenge/<theme>/tier_<n>/<id>.json    metadata (authoritative)
```

```json
{
  "theme": "<ns>:<theme>",
  "tier": 1,
  "structure": "<ns>:challenges/<theme>/tier_1/<id>",
  "name": "Optional display name",
  "author": "Optional",
  "created": 1790000000000,
  "weight": 1,
  "rotation": false
}
```

`weight` (default 1) controls how often a structure is drawn; `rotation` (default false) allows random
rotation and mirroring on placement. Structures may be at most 128 × 128 blocks (X/Z).

### Challenge scrolls & portals

There is exactly one scroll item, `architectstrials:challenge_scroll`. Theme and tier are stored in the data
component `architectstrials:challenge`, so any datapack-driven recipe system can produce scrolls:

```json
"result": {
  "id": "architectstrials:challenge_scroll",
  "components": { "architectstrials:challenge": { "theme": "mypack:nether", "tier": 2 } }
}
```

Right-clicking while looking at a block opens a particle portal in front of that block face (needs 1 × 2
free blocks; the portal may clip into walls). The portal forms briefly, then becomes active. In the solo
default only the player who used the scroll can enter, and the portal closes behind them. The scroll is
consumed only if the portal opened — never when the pool is empty, the instance limit is reached, space is
missing or the scroll is used inside an Architect's Trials dimension. If nobody enters within
`portalTimeoutSeconds`, the portal collapses, the instance is cleaned up and the scroll drops again with a
50 % chance.

### Scroll options (multiplayer)

The optional component `architectstrials:options` controls who may enter and how long the portal stays open.
The options are fixed into the instance when the portal opens. All fields are optional; without the component
a scroll is the solo default.

| Field | Values | Default |
|---|---|---|
| `max_players` | `1` = only the scroll user, `n` = up to n distinct players, `0` = unlimited | `1` |
| `portal_open_seconds` | `0` = closes after the first pass-through, `>0` = open that many seconds once active, `-1` = open until the time limit expires | `0` |
| `allow_reentry` | `true` / `false` | `false` |

```json
"components": {
  "architectstrials:challenge": { "theme": "mypack:nether", "tier": 2 },
  "architectstrials:options": { "max_players": 4, "portal_open_seconds": 120, "allow_reentry": true }
}
```

- Anyone may walk into an open portal until `max_players` is reached — there is no party system, and late
  joiners are not rank-checked. Everyone lands on a random spawn marker; nothing is rebalanced.
- The portal closes when its time is up or `max_players` is reached, whichever comes first. With
  `allow_reentry` it stays open for its full time so players can come back.
- Re-entry is for players who left without completing (e.g. after a defeat) and does not count against
  `max_players`. Players who completed the run via an exit can never re-enter.
- The time limit is automatically at least as long as the portal stays open.

### Time limit & cleanup

Every challenge has a time limit, shown to all participants as a boss bar ("Time left: mm:ss", red in the
last minute): the scroll's `architectstrials:time_limit` component (minutes), otherwise
`defaultTimeLimitMinutes` (default 60). It is never shorter than the portal's open time. Participants get chat
warnings at 5 and 1 minute(s) and an action-bar countdown in the last 10 seconds; when time is up, everyone
still inside is sent back — this does **not** count as a completed run.

Time only runs while the server runs (it is counted in server ticks and saved with the world). Players who
log out inside a challenge keep it alive until the time limit; if they log back in in time, they continue,
otherwise they are returned on login.

An instance is removed and its slot cleared once its portal is closed **and** nobody (online or offline)
belongs to it anymore — e.g. after the last player finished, died or ran out of time.

```json
"components": {
  "architectstrials:challenge": { "theme": "mypack:nether", "tier": 2 },
  "architectstrials:time_limit": 30
}
```

### Completion bonus & advancements

Completing a run (walking through an open exit) rolls a **bonus loot table** for the player, separate from
the structure's own loot. Items go straight into the inventory; overflow drops at the player's feet. The
loot context contains the player and their luck.

Loot table lookup: a loot table set on the used exit (look at an Exit Marker in the editor and run
`/architectstrials marker loot_table <id>`) → otherwise the convention
`<ns>:architectstrials/completion/<theme>/tier_<n>` (namespace and path of the theme id) → otherwise no bonus.

Advancements can react to completed runs with the criterion `architectstrials:run_completed`:

```json
"criteria": {
  "five_nether_runs": {
    "trigger": "architectstrials:run_completed",
    "conditions": { "theme": "mypack:nether", "tier": { "min": 2 }, "runs": { "min": 5 } }
  }
}
```

All conditions are optional. `runs` counts the player's completed runs matching `theme` and `tier`
(including the current one). Advancement rewards (e.g. functions) work as usual.

### Ranks

Every player has a rank **per theme**. A scroll of tier *n* can only be used by a player whose
level for that theme is at least *n* (the tooltip shows the requirement in green or red). Players start at
`startingRank` (default 1); with `0`, all scrolls are blocked until the player is upgraded.

Raise levels from anywhere a command runs, typically an advancement reward function:

```mcfunction
# data/mypack/function/nether_unlock_tier_2.mcfunction
architectstrials rank @s mypack:nether set 2
```

Only the player opening a portal is checked — stronger players can open challenges for weaker ones.

### Editor

The mod ships its own void dimension `architectstrials:editor` where builders create structures — by hand,
with WorldEdit, Litematica, a Create schematicannon, anything; only the placed blocks count. There is one
shared build site: everyone in the editor works on the same structure. A world border limits it to exactly
128 × 128 blocks, the maximum structure size. A 3×3 glass-looking Editor Platform at the center gives you
something to start from (it is ignored when saving); structures placed on it end up at `structurePlacementY`.

Commands (operators): `/architectstrials editor enter` (your game mode stays unchanged, no death
protection), `/architectstrials exit` to leave, and `/architectstrials editor clear` followed by
`/architectstrials editor clear confirm` within 30 seconds to empty the whole editor (all builders are put back
onto the fresh platform).

**Saving.** `/architectstrials editor save <theme> <tier> <id>` captures everything inside the border, trimmed
to what was actually built (the platform is ignored; item frames, armor stands and paintings are kept, mobs
are not — use spawn markers). A structure needs at least one Player Spawn Marker and one Exit Marker; spawn
markers without two free blocks above them and mob markers without a spawn egg only produce a warning. The files go into the managed datapack
`<world>/datapacks/architectstrials_structures/`, which is enabled and reloaded automatically — the structure
is in the pool immediately. To ship your structures, copy that one folder into your modpack. Add `overwrite`
to replace an existing id (weight, rotation and name are kept).

**Tier copies.** `/architectstrials editor load <theme> <tier> <id>` loads a stored structure into the empty
editor (after `editor clear`), e.g. to turn a tier-1 build into a harder tier-3 variant and save it again.

**Managing.** `/architectstrials structure list [theme] [tier]`,
`/architectstrials structure set <theme> <tier> <id> weight <n> | rotation <true|false> | name <text>` and
`/architectstrials structure delete <theme> <tier> <id>` (+ `confirm` within 30 s). Editing and deleting only
work for structures in the managed datapack.

### Markers

Builders place marker blocks instead of final content; they are resolved when a structure is placed.
Markers are only available in the creative inventory (no recipes).

| Marker | Effect on placement |
|---|---|
| Player Spawn Marker | Records a possible entry point (position + the direction its arrow points) and turns into air. Every player entering picks a random one independently, so groups may start at different points. **Every structure needs at least one**; structures without are skipped with a warning. |
| Exit Marker | Becomes the challenge exit: a base block with an animated portal surface above it. Up to three bases side by side (same facing, in a line across it) form one combined portal of n × (n+1) blocks; a signal at any base locks the whole portal. Walking through the open portal completes the run ("Run Completed") and returns the player to their entry point. **A redstone signal locks the exit** (only the base remains visible); unpowered it is open. Wire it to any redstone logic to decide when a run can be finished. Using an exit is the only way to complete a run. |
| Direct Spawn Marker | Turns into air and spawns the configured mobs right away. They never despawn. |
| Spawner Marker | Becomes a vanilla monster spawner for the configured mob. The egg count is the number of mobs per spawn cycle. All other spawner settings stay at vanilla defaults. |

**Configuring mob markers.** Right-click a Direct Spawn or Spawner Marker (creative mode + operator) to open its
inventory: one spawn egg slot (the entity type — eggs from any mod work; the stack size is the count) and six
equipment slots (head, chest, legs, feet, main hand, off hand). Mobs first get their normal setup (a skeleton
still gets its bow), then every filled slot replaces the natural item. Equipment never drops (0 % drop chance) —
loot comes from mob loot tables and loot containers. When the structure is rotated or mirrored, only the marker
position moves. A mob marker without a spawn egg triggers a warning on save and is removed without spawning
anything.

### Commands (operators only)

| Command | Description |
|---|---|
| `/architectstrials theme list` | Lists all loaded themes and their available tiers |
| `/architectstrials theme tp <theme>` | Teleports you into a theme's dimension (void world — use creative/spectator); stores your entry point, keeps your game mode |
| `/architectstrials enter <theme>` | Debug entry into a theme like a real challenge: stores your entry point, switches to Adventure |
| `/architectstrials instance list` | Lists all instances with state, remaining time and participant count |
| `/architectstrials instance close <id>` | Returns the participants and removes an instance |
| `/architectstrials instance create <theme> <tier> [join]` | Debug: creates an instance — draws a structure, places it in a free slot, runs the marker pass; `join` lets you enter at a random spawn marker |
| `/architectstrials marker loot_table <id>\|clear` | Sets or removes the loot table of the marker you are looking at (Exit Marker: completion bonus override) |
| `/architectstrials rank <targets> <theme> set <level>` / `add <amount>` / `get` | Sets, changes or reads ranks (usable in functions; `get` returns the level) |
| `/architectstrials scroll give <theme> <tier> [targets]` | Gives a challenge scroll bound to a theme and tier |
| `/architectstrials slot list <theme>` | Lists occupied and clearing slots of a theme dimension |
| `/architectstrials slot allocate <theme>` | Debug: allocates the next free slot |
| `/architectstrials slot free <theme> <index>` | Debug: releases a slot; its content is cleared over the next ticks |
| `/architectstrials editor enter` | Enters the shared editor dimension |
| `/architectstrials editor clear` (+ `confirm`) | Empties the editor after confirmation within 30 s |
| `/architectstrials editor save <theme> <tier> <id> [overwrite]` | Saves the editor content as a pool structure |
| `/architectstrials editor load <theme> <tier> <id>` | Loads a stored structure into the empty editor |
| `/architectstrials structure list\|set\|delete …` | Lists, edits (weight, rotation, name) or deletes stored structures |
| `/architectstrials exit [targets]` | Leaves any Architect's Trials dimension: back to the entry point, or to the respawn point / world spawn if none is stored |

Players always return to the exact point they entered from, with their previous game mode, turned around
(they step back out of the portal they walked into) and with a short rune echo of the portal behind them. Players who log
in inside a challenge dimension whose challenge is over are returned automatically.

### Slots & server config

Every challenge instance gets its own slot in its theme dimension. Slots lie on a square spiral around the
origin, `slotSpacing` blocks apart, so parallel groups never see each other. A slot owns a 160 × 160 area
(maximum structure footprint 128 × 128 plus margin). Released slots are cleared over several ticks
(blocks, block entities and entities, without drops) before they are reused; clearing interrupted by a
server stop resumes on the next start.

`serverconfig/architectstrials-server.toml` (per world, also editable via the Mods menu in singleplayer):

| Option | Default | Description |
|---|---|---|
| `slotSpacing` | 2048 | Distance between slots. Only applies to theme dimensions without instances, so existing instances never move. |
| `maxConcurrentInstances` | 0 | Maximum simultaneous instances per theme dimension; `0` = unlimited. |
| `structurePlacementY` | 64 | Y coordinate the bottom of every challenge structure is placed at. |
| `startingRank` | 1 | Rank every player starts with in every theme; `0` blocks scrolls until upgraded. |
| `defaultTimeLimitMinutes` | 60 | Time limit of challenges whose scroll defines none. |
| `portalTimeoutSeconds` | 60 | Seconds an opened portal waits for its player before collapsing. |

### Death protection (Dimension Ward)

Nobody ever truly dies inside a challenge. When a player with a stored entry point would die in a challenge
dimension — from any cause, including the void and `/kill` — they are returned to their entry point with
their full inventory, full health and hunger, all effects removed and a short damage immunity. The vanilla
totem animation plays. Real totems (and other mods' death protection) always take precedence.

The protection is a state, not a status effect: it cannot be removed by milk or commands and has no icon.
Communicate it to players via your modpack's questbook or wiki. A death never counts as a completed run.

## For developers

```bash
./gradlew build              # mod jar -> build/libs/
./gradlew runClient
./gradlew runGameTestServer  # runs all GameTests headless
```

Other mods can react to completed runs via `com.gilfort.architectstrials.run.RunCompletedEvent` (NeoForge event bus).

GameTests and their test datapack live in `src/gametest` and are never included in the mod jar.
The dev test datapack also defines the dimension `architectstrials:gametest_theme` for manual testing in
`runClient` (`/architectstrials theme tp architectstrials:gametest_theme`), with a tier-1 pool containing a small
spawn-marker platform with a 2×3 exit and a completion bonus of 5 emeralds
(`/architectstrials instance create architectstrials:gametest_theme 1 join`).

## License

GPL-3.0 — see [LICENSE](LICENSE).
