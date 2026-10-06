# Loot

There are no loot marker blocks — vanilla containers and item frames cover everything. In addition, completing
a run grants a separate **completion bonus**. For rewards that combine several loot tables and items with your
own chances, use the [Loot Tool](#loot-tool-composed-rewards).

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

1. a [loot setup](#loot-tool-composed-rewards) on the **used exit**,
2. a loot table set on the **used exit** (`/at marker loot_table <id>` on the Exit Marker),
3. the convention `<ns>:architectstrials/completion/<theme>/tier_<n>` (namespace and path of the theme id),
4. otherwise no bonus.

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

## Loot Tool (composed rewards)

The **Loot Tool** (creative tab, no recipe) composes a reward from loot tables and fixed items with your own
chances — for example "70 % dungeon loot, 30 % a diamond, plus one extra emerald". It works on everything that
gives loot:

- lootable containers (chests, trapped chests, barrels, shulker boxes, …),
- [Trial Spawner Markers](Markers#trial-spawner-marker) (normal and ominous reward),
- [Exit Markers](Markers#exit-marker) (completion bonus).

| Action | Effect |
|--------|--------|
| Right click | Opens the setup GUI of the block |
| Shift + right click | Copies the block's setup into the tool |
| Left click | Pastes the copied setup (it stays in the tool until you copy something else) |

The tool never breaks blocks, also not in creative mode.

### How a setup is rolled

A setup has up to **five groups** and a **consolation list**, each a page in the GUI with up to nine entries.

- An entry is either a **loot table** (click "+ Loot table" in an empty cell for a searchable list;
  block drop tables like `minecraft:blocks/…` are left out) or a
  **fixed item** (put the item into the cell's slot).
- Every entry has a **chance** (0.1 % steps) and a **roll range** (min–max, up to 64): a drawn entry rolls its
  loot table, or gives its item, that many times.
- **Every group draws at most one entry.** The chances of a group add up to at most 100 %; the rest is the
  chance that the group gives nothing (shown below the grid). Groups are independent of each other.
- The **consolation list** is given completely — all its entries, without chances — if the groups produced
  **no item**: no group drew an entry, or the drawn loot tables came out empty.
- Loot tables can come out empty: chest tables with empty entries, mob tables with counts from 0 or
  "killed by player" conditions. Fixed items or chest tables are the safe choice for guaranteed rewards.

Groups are rolled in order (1 to 5), each drawing once; then the consolation list follows if nothing came out.
The order does not change any chances — it only decides in which order the items are created. A roll therefore
gives anything from nothing (→ consolation) up to one entry from each of the five groups.

**Five groups of nine entries are not the same as one group of 45:** one group gives *at most one* entry,
five groups give *up to five*. Rule of thumb: what should exclude each other ("either … or") goes into **one**
group; what may drop independently ("and maybe also") goes into **different** groups.

#### Example 1 — main reward plus a side reward

| Page | Entries |
|------|---------|
| Group 1 | iron sword 60 %, diamond sword 30 %, netherite sword 10 % |
| Group 2 | `minecraft:chests/simple_dungeon` 100 % |

Always **exactly one** sword **and** dungeon loot. With all four entries in one group, players would get either
a sword or the dungeon loot, never both.

#### Example 2 — independent rare bonuses

| Page | Entries |
|------|---------|
| Group 1 | `minecraft:chests/simple_dungeon` 100 % |
| Group 2 | enchanted golden apple 5 % |
| Group 3 | diamond 20 %, rolls 1–3 |

The base loot always drops; the apple in 5 % of the rolls, 1–3 diamonds in 20 %, both together in 1 %. Each
bonus has its own chance and a rare find never blocks another one — in a single group the apple would exclude
the diamonds.

#### Example 3 — jackpot or consolation

| Page | Entries |
|------|---------|
| Group 1 | elytra 2 %, totem of undying 10 % (88 % nothing) |
| Consolation | emerald, rolls 3–3 |

2 % elytra, 10 % totem, otherwise (88 %) three emeralds. Nobody leaves empty-handed, and the jackpot is not
watered down: whoever hits it gets no emeralds on top.

### Where setups apply

- **Containers:** the setup replaces the container's loot table and is rolled when a player first opens it, like
  [pool loot](#pool-loot) (fresh for every instance; hand-placed items stay). Clearing the setup removes it again.
- **Trial Spawner Markers:** the GUI has a Normal/Ominous switch. A setup takes precedence over
  `/at marker loot_table`; for the ominous reward the order is ominous setup → ominous loot table → normal reward.
  The items are ejected like vanilla rewards.
- **Vault Markers:** a setup is the vault's reward, rolled for every player who unlocks it (see
  [Vault Marker](Markers#vault-marker)).
- **Exit Markers:** a setup is the completion bonus of that exit and takes precedence over all loot tables
  (see [Completion bonus](#completion-bonus)).

Loot tables in a setup are rolled with the opening/completing player and their luck where there is one.
`/at marker info` lists the setups of the block you are looking at.

## Trial spawner rewards

The reward of a [Trial Spawner Marker](Markers#trial-spawner-marker) is also set with
`/at marker loot_table <id>` while looking at the marker.
