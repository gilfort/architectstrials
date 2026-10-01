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
- `overwrite` replaces an existing id; weight, rotation and name are kept.

To ship your structures with a modpack, copy that one folder into the modpack's datapacks.

## Loading (tier copies)

```
/at editor load <theme> <tier> <id>
```

Loads a stored structure into the **empty** editor (run `editor clear` first) — e.g. to turn a tier-1 build into
a harder tier-3 variant and save it under the new tier.

## Managing stored structures

| Command | Description |
|---|---|
| `/at structure list [theme] [tier]` | Lists stored structures with name, tier, weight and rotation |
| `/at structure set <theme> <tier> <id> weight <n>` | How often the structure is drawn (default 1) |
| `/at structure set <theme> <tier> <id> rotation <true\|false>` | Allow random rotation and mirroring on placement (default false) |
| `/at structure set <theme> <tier> <id> name <text>` | Display name |
| `/at structure delete <theme> <tier> <id>` + `confirm` (within 30 s) | Deletes the structure files |

Editing and deleting only work for structures in the managed datapack. IDs are tab-completed.

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
