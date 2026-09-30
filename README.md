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

### Commands (operators only)

| Command | Description |
|---|---|
| `/architectstrials theme list` | Lists all loaded themes and their available tiers |
| `/architectstrials theme tp <theme>` | Teleports you into a theme's dimension (void world — use creative/spectator); stores your entry point, keeps your game mode |
| `/architectstrials enter <theme>` | Debug entry into a theme like a real challenge: stores your entry point, switches to Adventure |
| `/architectstrials instance create <theme> <tier>` | Debug: creates an instance — draws a structure, places it in a free slot, runs the marker pass |
| `/architectstrials slot list <theme>` | Lists occupied and clearing slots of a theme dimension |
| `/architectstrials slot allocate <theme>` | Debug: allocates the next free slot |
| `/architectstrials slot free <theme> <index>` | Debug: releases a slot; its content is cleared over the next ticks |
| `/architectstrials exit [targets]` | Leaves any Architect's Trials dimension: back to the entry point, or to the respawn point / world spawn if none is stored |

Players always return to the exact point they entered from, with their previous game mode. Players who log
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

GameTests and their test datapack live in `src/gametest` and are never included in the mod jar.
The dev test datapack also defines the dimension `architectstrials:gametest_theme` for manual testing in
`runClient` (`/architectstrials theme tp architectstrials:gametest_theme`), with a tier-1 pool containing the
vanilla igloo (`/architectstrials instance create architectstrials:gametest_theme 1`).

## License

GPL-3.0 — see [LICENSE](LICENSE).
