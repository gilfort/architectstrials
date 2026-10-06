# Building Challenges

Challenge structures are built in the **editor**, a void dimension shipped with the mod, and saved as a pool
structure of a theme and tier. Use any building tool you like — by hand, WorldEdit, Litematica, a Create
schematicannon — only the placed blocks count.

## The editor

- One **shared build site**: everyone in the editor works on the same structure.
- A **world border** limits it to exactly **128 × 128 blocks**, the maximum structure footprint.
- A 3 × 3 glass-looking **Editor Platform** at the center gives you something to start from. It is ignored
  when saving; whatever stands on it ends up at the structure placement height (`structurePlacementY`).
- Your game mode stays unchanged and there is no death protection in the editor.
- Entering puts you on the platform, or on top of whatever has been built at the center (e.g. after loading or
  importing a structure). Builders who end up inside blocks after a load or import are moved there too.

| Command | Description |
|---|---|
| `/at editor enter` | Enter the editor |
| `/at exit` | Leave it (back to where you entered) |
| `/at editor clear` + `/at editor clear confirm` (within 30 s) | Empty the whole editor; all builders are put back onto the fresh platform |

Lootable containers with a loot table cannot be opened in the editor — that would roll the loot and remove
the reference (see [Loot](Loot#pool-loot)).

## Saving

```
/at editor save <theme> <tier> <id> [overwrite]
```

Example: `/at editor save mypack:crypt 2 spider_den`

- Captures everything inside the border, trimmed to what was actually built. The platform is ignored.
- **Kept:** blocks, block entities (container contents, loot tables, marker settings) and decoration entities —
  item frames, armor stands, paintings. **Not kept:** mobs — use [mob markers](Markers#mob-markers).
- **Required:** at least one Player Spawn Marker and one Exit Marker; saving fails otherwise.
- **Warnings only:** a Player Spawn Marker without two free blocks above it, a mob marker without a spawn egg.
- The files go into the managed datapack `<world>/datapacks/architectstrials_structures/`, which is enabled and
  reloaded automatically — the structure is in the pool immediately.
- **After mod updates:** `/at structure validate` lists structures that refer to blocks, items, mobs, loot tables
  or effects that no longer exist.
- `overwrite` replaces an existing id; weight, rotation and name are kept.

To ship your structures with a modpack, copy that one folder into the modpack's datapacks.

## Loading (tier copies)

```
/at editor load <theme> <tier> <id>
```

Loads a stored structure into the **empty** editor (run `editor clear` first) — e.g. to turn a tier-1 build into
a harder tier-3 variant and save it under the new tier.

## Importing areas of the world

Existing builds — bastions, nether fortresses, ancient cities, large caves, your own survival builds — can be
copied straight into the editor and turned into a challenge there.

1. Take the **Selection Tool** from the creative tab.
2. **Left click** a block: corner 1. **Right click** a block: corner 2. The tool never breaks blocks. While you
   hold it, both corners and the selected box are drawn as an outline — white if the box can be imported, red if
   it is too large. The tooltip shows the size.
   **Shift + right click** opens the corner editor: change every coordinate by typing or with -/+, or set a corner
   to your position with "Here". Handy for corners high up in the air — set the corner on the ground, then raise
   its Y value.
3. Run `/at editor import` (the editor must be empty — `editor clear` first). The tool can be in your hand or
   anywhere in your inventory.

What happens:

- The box is copied 1:1 like a saved structure: blocks, block entities (unopened containers keep their loot
  table and become [pool loot](Loot#pool-loot)), item frames, armor stands and paintings. **Mobs are not copied.**
- It is placed into the editor like `editor load`: centered, bottom at the placement height.
- **Spawners become markers:** a monster spawner becomes a [Spawner Marker](Markers#spawner-marker) (spawn egg
  of its mob, stack size = spawn count, equipment and equipment loot table taken over); a trial spawner becomes a
  [Trial Spawner Marker](Markers#trial-spawner-marker) (its three most frequent mobs as rows, egg counts summing up
  to its total mobs, "at once" and the reward taken over — several weighted reward tables become a
  [loot setup](Loot#loot-tool-composed-rewards) with matching chances; a vanilla ominous configuration fills the
  ominous page, with ominous still blocked until you allow it). Mobs without a spawn egg are listed in
  the result message and leave their row empty.
- The result message reports how many blocks were copied and how many spawners were converted.

Limits:

- At most **128 × 128** blocks footprint; the height must fit into the editor above the placement height.
- Areas of Architect's Trials dimensions (challenges, editor) cannot be selected.
- Selections with more than **100,000 blocks** ask for confirmation (`/at editor import confirm` within
  30 seconds) because copying happens in one go and may freeze the server for a few seconds.

Then add Player Spawn and Exit Markers, adjust loot and enemies and save it with `editor save` as usual.

## Managing stored structures

| Command | Description |
|---|---|
| `/at structure list [theme] [tier]` | Lists stored structures with name, tier, weight, rotation and game mode |
| `/at structure set <theme> <tier> <id> weight <n>` | How often the structure is drawn (default 1) |
| `/at structure set <theme> <tier> <id> rotation <true\|false>` | Allow random rotation and mirroring on placement (default false) |
| `/at structure set <theme> <tier> <id> game_mode <adventure\|survival>` | Game mode players enter in (default Adventure) |
| `/at structure set <theme> <tier> <id> name <text>` | Display name |
| `/at structure delete <theme> <tier> <id>` + `confirm` (within 30 s) | Deletes the structure files |

Editing and deleting only work for structures in the managed datapack. IDs are tab-completed.

## Mining rooms (Survival challenges)

By default players enter a challenge in **Adventure** mode and cannot break or place blocks. Set a structure to
**Survival** with `/at structure set <theme> <tier> <id> game_mode survival` (or `"game_mode": "survival"` in its
JSON) to build mining rooms:

- Players can mine everything and keep the drops — including ores, chests, spawners and trial spawners — and
  place blocks freely. Every instance is a fresh copy, so nothing affects other runs or the saved structure.
- Always protected: exit bases, the portal space above them and the block directly below every Player Spawn
  Marker (so late joiners never fall into a hole). Explosions skip these blocks too.
- Leaving restores the player's own game mode, as always; the Dimension Ward keeps the inventory, including
  everything mined.

### Natural ores

Instead of placing ore veins by hand, let every instance get its own ores: add an `ore_generation` block to the
structure's JSON (see [Datapack Reference](Datapack-Reference#structure-metadata)).

```json
"ore_generation": {
  "biome": "minecraft:plains",
  "min_y": -64,
  "max_y": 319
}
```

- The **ore features of the biome** are used (its underground ores step): coal, iron, copper, gold, diamonds, …
  plus dirt, gravel, granite and similar blobs, and ores other mods add to that biome. Springs, geodes, fossils
  and dripstone are not placed. A Nether biome such as `minecraft:nether_wastes` gives quartz, Nether gold and
  ancient debris in netherrack.
- **Height:** the structure's lowest layer counts as `min_y`, its highest as `max_y`, everything in between is
  stretched linearly. So diamonds sit at the bottom and coal goes up to the top of the room, wherever the instance
  is placed. Without `min_y` / `max_y` the range of the biome's dimension is used (Overworld −64..319, Nether
  0..127, End 0..255).
- **Only replaceable blocks change**, exactly as in world generation: Overworld ores replace stone (deepslate
  variants in deepslate), Nether ores replace netherrack. Markers, chests, bricks, planks, glass … stay as built.
- **Amount:** by default as many ores per block as in vanilla. `"density": 3` triples the attempts for a rich mine,
  `0.5` halves them.
- Every instance gets a different distribution; the saved structure is never changed. Ores are generated right
  after the structure is built (a few chunks per tick) and before the markers are resolved.
- An unknown biome only logs a warning; the challenge then has no ores.

Example: a cave carved out of stone and deepslate (deepslate in the lower part), `game_mode: survival`, an
`ore_generation` block with `minecraft:plains`, a Player Spawn Marker on a ledge and an Exit Marker behind a
redstone-locked door that opens once the players reach the bottom.

## Challenge effects and attributes

A challenge can give every player its own rules — night vision in a dark cave, slowness in a swamp — independent of
the scroll. Add them to the structure's JSON (see [Datapack Reference](Datapack-Reference#challenge-structure-metadata)):

```json
"player_effects": [
  {"effect": "minecraft:night_vision"},
  {"effect": "minecraft:slowness", "amplifier": 0, "duration": 1200}
],
"player_attributes": [
  {"attribute": "minecraft:movement_speed", "amount": -0.02, "operation": "add_value"}
]
```

- Applied once on every entry, also to late joiners and on re-entry. Without `duration` (or with `-1`) an effect
  lasts the **remaining time of the instance**.
- Nothing is re-applied: milk removes an effect for the rest of that stay.
- The scroll wins: if the scroll gives the same effect, its effect is on top and the challenge effect takes over
  once it runs out. The player's own effect (e.g. from a potion) waits behind both.
- Leaving — by any way — removes all effects and attribute modifiers of the challenge and the scroll and gives the
  player's own effects back with the duration they had on entry.
- Modded effects and attributes work the same; unknown ids are skipped with a warning in the log.

## Structure pools and tiers

Each theme has one structure pool per tier. When a challenge instance is created, one structure is drawn
(weighted) from the pool of the scroll's tier, placed centered in a free slot with its bottom at
`structurePlacementY`, and the instance stays bound to it — later entries never re-roll. A theme's tiers are
simply the tiers with at least one structure.

The file layout (for reference — the editor writes it for you):

```
data/<ns>/structure/challenges/<theme>/tier_<n>/<id>.nbt           structure template (vanilla format)
data/<ns>/architectstrials/challenge/<theme>/tier_<n>/<id>.json    metadata (authoritative)
```

See [Datapack Reference](Datapack-Reference#structure-metadata) for the metadata format. Structures may be at
most 128 × 128 blocks (X/Z).

## Tips

- **Rotation:** only enable it for structures that work from every direction. Markers, paintings and item
  frames rotate with the structure.
- **Paintings** of any size keep their exact position, also when the structure is rotated or mirrored.
- **Testing a single structure:** give it a high weight temporarily (`structure set … weight 100`) and use
  `/at instance create <theme> <tier> join`.
