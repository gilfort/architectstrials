# Markers

Builders place **marker blocks** instead of final content. When a structure is placed for a challenge, every
marker is resolved into its runtime form. Markers are only available in the creative inventory (no recipes)
and cannot be broken in survival.

| Marker | Becomes |
|---|---|
| Player Spawn Marker | An entry point for players (then air) |
| Exit Marker | The challenge exit (base block + portal) |
| Direct Spawn Marker | Mobs spawned right away (then air) |
| Spawner Marker | A vanilla monster spawner |
| Trial Spawner Marker | A vanilla trial spawner |

When a structure is rotated or mirrored, markers move with it; mobs and spawners are not rotated otherwise.

## Player Spawn Marker

Records a possible entry point: its position and the direction its arrow points (players look that way on
arrival). Every entering player picks a random one independently, so groups may start at different points.

- **Every structure needs at least one** — saving fails without; stored structures without one are skipped.
- Leave two free blocks above it (saving warns otherwise).

## Exit Marker

Becomes the challenge exit: a base block with an animated portal surface above it. Walking through the open
portal **completes the run** ("Run Completed") and returns the player to their entry point. Using an exit is the
only way to complete a run.

- **A redstone signal locks the exit**: powered, only the base remains visible; unpowered, it is open. Wire it to
  any redstone logic (pressure plates, levers behind a boss room, trial spawner comparators, …) to decide when a
  run can be finished.
- **Wider exits:** up to three Exit Markers side by side (same facing, in a line across it) form one combined
  portal of n × (n+1) blocks. A signal at any base locks the whole portal.
- **Keep the portal space free:** the n × (n+1) blocks above the bases are filled with invisible, unbreakable
  portal blocks when the structure is placed. Players walking into them complete the run — the server only
  reacts on contact and checks nothing while nobody uses the exit. Blocks you build into that space stay and
  block the portal there.
- **Needs required mobs:** right-click the Exit Marker (empty hand) and switch on **Needs all required mobs
  defeated**. The exit is then sealed — locked look, no portal — until every [required mob](#direct-spawn-marker)
  of the instance is defeated, and opens with a sound and message. Exits without the setting work as before.
- **Bonus override:** look at the marker and run `/at marker loot_table <id>` to give this exit its own
  completion bonus (see [Loot](Loot#completion-bonus)). The [Loot Tool](Loot#loot-tool-composed-rewards)
  composes a bonus from several loot tables and items instead; it takes precedence over the loot table.
- Every structure needs at least one Exit Marker.

## Mob markers

The Direct Spawn, Spawner and Trial Spawner Markers define enemies. Right-click one (creative mode + operator)
to open its inventory. Each **mob row** has:

- one **spawn egg** slot — the entity type (eggs from any mod work); the stack size is the count,
- six **equipment** slots — head, chest, legs, feet, main hand, off hand (fixed item or random list, see below).

Each equipment slot is one of three things:

- **Empty** — the mob keeps its natural equipment (a skeleton still gets its bow). Empty slots show a small dice
  icon in their top-right corner.
- **A fixed item** — drag or shift-click an item into the slot. It is always used; the slot shows "100%". To turn
  it into a random slot, take the item out first.
- **A random list** — **click an empty slot** (with nothing on the cursor) to open its list: up to 9 items, each
  with a chance in percent (one decimal, e.g. `0.5`). Exactly one item is drawn per mob, the remainder up to 100 %
  means **nothing** — the slot is then empty, even if the mob would naturally carry something. A slot with a list
  shows its most likely item plus the dice icon; click it to edit the list, "Back" returns to the marker.

Example — zombies with 90 % no weapon, 3 % each fishing rod, stone sword or bow, 1 % iron spear: click the
main hand slot and enter fishing rod 3, stone sword 3, bow 3, iron spear 1 → "Nothing: 90 %".

Further rules:

- Chances can never add up to more than 100 % — a value that would exceed it is reduced automatically.
- Lists are rolled **per mob**: for every directly spawned mob and for every spawn of a spawner or trial spawner.
- **Equipment loot table per row:** `/at marker equipment_table <row> <id>` (rows count from 1; on the Trial
  Spawner Marker rows 4–6 are the ominous page) gives every mob of the row the equipment of a loot table of type
  `minecraft:equipment`, e.g. `minecraft:equipment/trial_chamber_melee`. Imported vanilla spawners keep theirs.
  Order on spawn: natural equipment → equipment table → fixed items → random lists.
- Equipment never drops (0 % drop chance) — loot comes from mob loot tables and [loot containers](Loot).
- A mob marker without a spawn egg triggers a warning on save and is removed without spawning anything.

### Direct Spawn Marker

Turns into air and spawns its mobs right away when the structure is placed. They never despawn. Several mobs of
one marker are spread over free spots within 1.5 blocks — build a floor around the marker, or they may fall.

**Required mobs:** the **Required** button in the marker's GUI (top right) makes all its mobs *required*, e.g. a
boss. Required mobs glow for the whole challenge time (milk removes the glow for good — they are still required).
Exits set to need them stay **sealed** until every required mob of the instance is gone; walking into a sealed exit
shows "Defeat all marked enemies (2/5)". Any final removal counts — killed by players, by other mobs or the void,
`/kill`, or a conversion (a zombie turning into a drowned). Only the Direct Spawn Marker has this option: spawners
never stop spawning.

### Spawner Marker

Becomes a vanilla monster spawner for the configured mob. The egg count is the number of mobs **per spawn
cycle**. All other settings (delay, range, max nearby) stay at vanilla defaults.

### Trial Spawner Marker

Becomes a vanilla trial spawner (no vault). Its inventory has **two pages** — normal and ominous — with
**three mob rows** each, switched with the "Page" button. Per page:

- **Total mobs** = sum of the page's egg counts; the counts also act as spawn weights
  (4 zombie eggs + 2 skeleton eggs = 6 mobs, about twice as many zombies).
- **At once** — how many mobs are alive at the same time — set with the +/- buttons (1–32, default 2).

Rewards (look at the marker):

- `/at marker loot_table <id>` — ejected after a cleared wave. Without one there is no reward.
- `/at marker ominous_loot_table <id>` — reward of the ominous variant; without one the normal reward is used.
- With the [Loot Tool](Loot#loot-tool-composed-rewards) you can compose both rewards from several loot tables
  and items with own chances; a setup takes precedence over the loot table.

Vanilla per-player scaling stays active (+2 total and +1 at once per additional player), as do spawn delay,
range and cooldown.

#### Ominous variant

The "Ominous" button toggles whether the spawner may turn ominous:

- **blocked** (default): the spawner never turns ominous — Bad Omen and Trial Omen are ignored and not consumed.
- **allowed**: like vanilla, it turns ominous when a player with Bad Omen or Trial Omen comes into range (Bad Omen
  becomes Trial Omen). It then spawns the **ominous page** — e.g. wither skeletons with netherite swords instead
  of skeletons — with that page's "at once" value, drops vanilla ominous items (potions, arrows, …) during the
  fight and ejects the ominous reward. If the ominous page is empty, the normal rows are used.

Example: a trial spawner with 4 zombies (iron helmet) and 2 skeletons, 3 at once, rewarding dungeon loot, and an
ominous variant with 3 wither skeletons and nether fortress loot:

1. Place the Trial Spawner Marker and right-click it.
2. Normal page — row 1: 4 zombie spawn eggs + iron helmet; row 2: 2 skeleton spawn eggs. Press + once (3 at once).
3. Switch to the ominous page — row 1: 3 wither skeleton spawn eggs. Toggle "Ominous: allowed".
4. Look at the marker: `/at marker loot_table minecraft:chests/simple_dungeon` and
   `/at marker ominous_loot_table minecraft:chests/nether_bridge`.

## Inspecting markers

`/at marker info` shows the loot table of the marker or container you are looking at; for Trial Spawner Markers
also the ominous setting and the ominous reward, for mob markers the equipment tables of their rows.
