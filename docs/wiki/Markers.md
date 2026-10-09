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
| Vault Marker | A vanilla vault (reward behind a key, once per player) |
| Sub Structure Marker | A rolled [sub structure](#sub-structure-marker) (then air) |

When a structure is rotated or mirrored, markers move with it; mobs and spawners are not rotated otherwise.

## Ghost slots

All item slots of the marker GUIs — spawn eggs, equipment, random equipment lists, the vault key and the cells of
the [Loot Tool](Loot#loot-tool-composed-rewards) — are **ghost slots**: they only hold a *copy*. The item on your
cursor is never used up, and nothing can be taken out of a ghost slot (hoppers neither insert nor extract).

| Action | Spawn egg, loot cell, vault key (with count) | Equipment |
|---|---|---|
| Left click with an item | copy with the cursor's count (same item: added) | copy (1×, with name, enchantments, …) |
| Right click with an item | one more of that item | same as left click |
| Left click, empty hand | +1 | — (empty equipment slot: opens its random list) |
| Right click, empty hand | −1 (at 0 the slot is empty) | clears the slot |
| Shift + click, empty hand | clears the slot | clears the slot |
| Shift + click in your inventory | copy into the first free matching slot | same |
| Mouse wheel over the slot | +1 / −1 | — |
| Middle click (creative) | copies the content onto your cursor (vanilla) | same |

Counts are capped at the item's stack size (spawn eggs: 64). With [JEI](https://www.curseforge.com/minecraft/mc-mods/jei)
installed you can also **drag items from the JEI list** onto a ghost slot: matching slots light up, dropping sets
one item, holding **Shift** while dropping sets a full stack.

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
- **Camouflage:** right-click the Exit Marker with a block (no sneaking) to give the exit that block's look, in the
  state it would be placed in (log axis, orientation). Allowed are opaque full blocks without block entity, also
  from other mods; anything else is rejected with a message. While locked or sealed the exit looks exactly like the
  block; open, its glowing lines are drawn on top. In the editor the marker keeps its door and direction arrow on
  top of the camouflage. The camouflage is only visual (hardness, sound, light and collision stay those of the
  exit), turns with rotated structures and can't be changed by players. Remove it in the marker GUI (empty hand →
  **Remove camouflage**). Sneak + right-click still places blocks against the marker. Each base of a wider exit
  has its own camouflage.
- **Bonus override:** look at the marker and run `/at marker loot_table <id>` to give this exit its own
  completion bonus (see [Loot](Loot#completion-bonus)). The [Loot Tool](Loot#loot-tool-composed-rewards)
  composes a bonus from several loot tables and items instead; it takes precedence over the loot table.
- Every structure needs at least one Exit Marker.

## Mob markers

The Direct Spawn, Spawner and Trial Spawner Markers define enemies. Right-click one (creative mode + operator)
to open its inventory. Each **mob row** has:

- one **spawn egg** slot — the entity type (eggs from any mod work); the count is the number of mobs
  (click 8 times instead of using 8 real eggs, see [ghost slots](#ghost-slots)),
- six **equipment** slots — head, chest, legs, feet, main hand, off hand (fixed item or random list, see below).

Each equipment slot is one of three things:

- **Empty** — the mob keeps its natural equipment (a skeleton still gets its bow). Empty slots show a small dice
  icon in their top-right corner.
- **A fixed item** — click with the item on the slot (or shift-click it in your inventory). It is always used; the
  slot shows "100%". To turn it into a random slot, clear it first (right click with an empty hand).
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

### Spawn Marker Tool

The **Spawn Marker Tool** (creative tab, no recipe) copies the complete configuration of a Direct Spawn, Spawner
or Trial Spawner Marker onto other markers — all rows with counts, fixed equipment, random lists, equipment loot
tables, the Required setting and, for trial spawners, both pages, "at once", "Ominous: allowed" and the rewards.

- **Shift + right-click** a marker: copies its configuration into the tool. The tooltip shows the marker type and
  the copied mobs (e.g. `3× Zombie`).
- **Left-click** a marker of the **same type**: replaces its whole configuration with the copy. Other marker types
  are refused with a message (a Spawner Marker copy only fits Spawner Markers).
- **Right-click**: opens the marker GUI. The tool never breaks blocks.

## Vault Marker

Becomes a vanilla **vault**: players unlock it with a key and get a reward — every player once per instance (each
new instance places a fresh vault). Right-click the marker to set it up:

- **Normal / Ominous** (top right) — the vault's look and its default key and reward.
- **Key slot** ([ghost slot](#ghost-slots)) — any item, including count and components (name, enchantments, …);
  that many are consumed. Empty = the vanilla key of the variant (Trial Key / Ominous Trial Key).
- **Reward** — compose it with the [Loot Tool](Loot#loot-tool-composed-rewards) (rolled per player on unlock; the
  floating preview shows possible rewards). Without a setup: the loot table set with `/at marker loot_table <id>`,
  otherwise the vanilla trial chamber reward of the variant.

Players clicking a vault in a challenge with the wrong item or an empty hand see what opens it, e.g. "Opens with:
2× Crypt Key". [Importing](Building-Challenges#importing-areas-of-the-world) a trial chamber converts its vaults into Vault
Markers with variant, key and loot table.

## Sub Structure Marker

Places a **sub structure** – a reusable piece saved with `/at editor save sub <ns>:<id>` – when the challenge is
placed, so one room can look different in every run (see [Building Challenges](Building-Challenges#sub-structures)).
Right-click the marker (empty hand or the Sub Structure Tool) to set it up:

- **Sub structures** — up to 6 sub structure ids, each with a chance in percent (together at most 100 %).
- **Fallback** — placed when none of them is rolled ("consolation prize"); empty = then nothing is placed.
- **Offset area** (−X, +X, −Y, +Y, −Z, +Z, 0–64 blocks) — without offsets the sub structure starts exactly at the
  marker; with offsets it starts at a random block of that area around the marker.
- **Save** stores everything; unknown ids or more than 100 % are rejected with a message.

Orientation: the marker faces the way you looked when placing it (top arrow). Facing **north** places the sub
structure as it was built, east turns it 90° clockwise, south 180°, west 90° counter-clockwise. The marker is the
sub structure's lowest north-west corner as built. When the parent structure is rotated or mirrored, marker facing
and offset area turn with it, so the piece always sits the same way relative to the room.

Rules when the challenge is placed:

- The sub structure **replaces** the blocks it overlaps, including other markers (they are then gone).
- It **fails** if it would overwrite a **Player Spawn Marker** or an **Exit Marker**, or reach beyond the slot area
  (128 × 128 structure + 16 blocks margin per side). Then the marker rolls **once more**; a second failure places
  nothing. Turn on `logFailedSubStructures` in the [server config](Configuration) to see failures in the log.
- Markers inside the sub structure (spawns, exits, mobs, loot, vaults, …) work as usual — a sub structure can add
  extra spawn points or exits. The challenge itself still needs its own Player Spawn Marker.
- No nesting: Sub Structure Markers inside sub structures place nothing (saving such a sub structure is refused).
- Sub structures are placed before [natural ores](Building-Challenges#natural-ores), so they get ores too.

The **Sub Structure Tool** copies setups like the Loot Tool: shift + right-click a marker copies its setup, left
click pastes an independent copy into another marker (the offsets of the target stay).

## Inspecting markers

`/at marker info` shows the loot table of the marker or container you are looking at; for Trial Spawner Markers
also the ominous setting and the ominous reward, for mob markers the equipment tables of their rows.
