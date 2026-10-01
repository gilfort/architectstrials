# Loot

There are no loot marker blocks — vanilla containers and item frames cover everything. In addition, completing
a run grants a separate **completion bonus**.

## Pool loot

Random loot that is rolled fresh for every instance.

1. Place any lootable container: chest, trapped chest, barrel, shulker box, dispenser, dropper, hopper, …
2. Look at it and run `/at marker loot_table <id>` — tab completion lists all loot tables, including those of
   other mods and your datapacks.
3. Check it with `/at marker info`; remove it with `/at marker loot_table clear`.

- The contents are rolled when a player **first opens** the container, so every instance gets fresh loot.
- Changes to the loot table apply immediately — no need to re-save the structure.
- Every placement gives each container a new random seed.
- In the editor, containers with a loot table **cannot be opened** (a chat message shows the loot table):
  opening would roll the loot into the container and drop the reference, turning pool loot into fixed loot on
  the next save.

## Guaranteed loot

Fixed items that are identical in every instance:

- **Containers filled by hand** in the editor.
- **Item frames** with an item (only fixed items; for random wall items use a container).

## Completion bonus

Completing a run (walking through an open exit) rolls a **bonus loot table** for the player, separate from the
structure's loot. Items go straight into the inventory; overflow drops at the player's feet. The loot context
contains the player and their luck (so Luck effects, e.g. from [scroll upgrades](Scrolls#upgrades-at-the-smithing-table), help).

Loot table lookup, first match wins:

1. a loot table set on the **used exit** (`/at marker loot_table <id>` on the Exit Marker),
2. the convention `<ns>:architectstrials/completion/<theme>/tier_<n>` (namespace and path of the theme id),
3. otherwise no bonus.

Example for theme `mypack:crypt`, tier 2 — `data/mypack/loot_table/architectstrials/completion/crypt/tier_2.json`:

```json
{
  "type": "minecraft:chest",
  "pools": [
    {
      "rolls": 1,
      "entries": [
        {
          "type": "minecraft:item",
          "name": "minecraft:emerald",
          "modifier": { "type": "minecraft:set_count", "count": { "type": "minecraft:uniform", "min": 3, "max": 6 } }
        }
      ]
    }
  ]
}
```

> In Minecraft 26.3 loot entry functions are written as `"modifier": { "type": … }`. The old
> `"functions": [ … ]` list is silently ignored.

## Trial spawner rewards

The reward of a [Trial Spawner Marker](Markers#trial-spawner-marker) is also set with
`/at marker loot_table <id>` while looking at the marker.
