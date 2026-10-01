# Architect's Trials

Architect's Trials is a NeoForge mod for **Minecraft 26.3**. Players use **challenge scrolls** to open portals
into themed challenge dimensions, fight their way through hand-built structures, loot them and always return
home with their inventory — nobody truly dies inside a challenge.

Everything players experience is defined by **you, the modpack creator**, with datapacks and an in-game editor.
No Java code is required.

> This wiki is the single source of truth for the mod. It is generated from `docs/wiki/` in the
> [repository](https://github.com/gilfort/architectstrials) — changes go through pull requests together with
> the code.

## How it fits together

| Concept | What it is | Page |
|---|---|---|
| Theme | A dimension that provides the atmosphere (sky, fog, light, biome) | [Themes & Dimensions](Themes-and-Dimensions) |
| Structure | A build saved from the editor, belonging to a theme and a tier | [Building Challenges](Building-Challenges) |
| Marker | Editor block that becomes spawn points, exits, mobs or spawners | [Markers](Markers) |
| Loot | Vanilla containers with loot tables, fixed items, completion bonus | [Loot](Loot) |
| Scroll | The item that opens a portal to a theme and tier | [Scrolls](Scrolls) |
| Instance | One placed copy of a structure that a group plays through | [Instances & Lifecycle](Instances-and-Lifecycle) |

## Quick start: your first challenge in 10 minutes

You need operator permissions (cheats enabled in singleplayer). All commands work as `/at …` and as
`/architectstrials …`.

**1. Create a theme.** Put these two files into a datapack (e.g. `<world>/datapacks/mypack/`):

`data/mypack/dimension/crypt.json`
```json
{
  "type": "architectstrials:challenge",
  "generator": {
    "type": "minecraft:flat",
    "settings": { "biome": "minecraft:the_void", "features": false, "lakes": false, "layers": [], "structure_overrides": [] }
  }
}
```

`data/mypack/architectstrials/challenge_dimensions.json`
```json
{ "replace": false, "values": ["mypack:crypt"] }
```

Restart the world (Minecraft only creates new dimensions on startup). `/at theme list` should show
`mypack:crypt`.

**2. Build.** `/at editor enter` takes you to the shared editor dimension. Build something on the glass
platform. Place at least one **Player Spawn Marker** (where players arrive) and one **Exit Marker** (where they
complete the run) from the creative tab. Optionally add a **Direct Spawn Marker** with zombie spawn eggs and a
chest with `/at marker loot_table minecraft:chests/simple_dungeon`.

**3. Save.** `/at editor save mypack:crypt 1 first_room` — the structure is now in the tier-1 pool.

**4. Play.** `/at exit`, then `/at scroll give mypack:crypt 1` and right-click a block with the scroll. Walk
through the portal, clear the room and leave through the exit to complete the run.

## Where to go next

- Shape the atmosphere: [Themes & Dimensions](Themes-and-Dimensions)
- Enemies, spawners and exits: [Markers](Markers)
- Multiplayer scrolls, time limits and upgrades: [Scrolls](Scrolls)
- Progression with ranks and advancements: [Scrolls](Scrolls#ranks), [Advancements & Events](Advancements-and-Events)
- All JSON formats at a glance: [Datapack Reference](Datapack-Reference)
