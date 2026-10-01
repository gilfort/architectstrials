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
- **Bonus override:** look at the marker and run `/at marker loot_table <id>` to give this exit its own
  completion bonus (see [Loot](Loot#completion-bonus)).
- Every structure needs at least one Exit Marker.

## Mob markers

The Direct Spawn, Spawner and Trial Spawner Markers define enemies. Right-click one (creative mode + operator)
to open its inventory. Each **mob row** has:

- one **spawn egg** slot — the entity type (eggs from any mod work); the stack size is the count,
- six **equipment** slots — head, chest, legs, feet, main hand, off hand.

Equipment rules:

- Mobs first get their normal setup (a skeleton still gets its bow), then every filled slot replaces the natural
  item. Leave a slot empty to keep the natural equipment.
- Equipment never drops (0 % drop chance) — loot comes from mob loot tables and [loot containers](Loot).
- A mob marker without a spawn egg triggers a warning on save and is removed without spawning anything.

### Direct Spawn Marker

Turns into air and spawns its mobs right away when the structure is placed. They never despawn. Several mobs of
one marker are spread over free spots within 1.5 blocks — build a floor around the marker, or they may fall.

### Spawner Marker

Becomes a vanilla monster spawner for the configured mob. The egg count is the number of mobs **per spawn
cycle**. All other settings (delay, range, max nearby) stay at vanilla defaults.

### Trial Spawner Marker

Becomes a vanilla trial spawner (no vault). Its inventory has **three mob rows** plus a counter:

- **Total mobs** = sum of all egg counts; the counts also act as spawn weights
  (4 zombie eggs + 2 skeleton eggs = 6 mobs, about twice as many zombies).
- **At once** — how many mobs are alive at the same time — set with the +/- buttons (1–32, default 2).
- **Reward:** look at the marker and run `/at marker loot_table <id>`; it is ejected after the wave is cleared.
  Without a loot table there is no reward.
- Vanilla per-player scaling stays active (+2 total and +1 at once per additional player), as do spawn delay,
  range and cooldown.
- These trial spawners **never turn ominous** — Bad Omen and Trial Omen are ignored and not consumed.

Example: a trial spawner with 4 zombies (iron helmet) and 2 skeletons, 3 at once, rewarding dungeon loot:

1. Place the Trial Spawner Marker and right-click it.
2. Row 1: 4 zombie spawn eggs + iron helmet; row 2: 2 skeleton spawn eggs. Press + once (3 at once).
3. Look at it: `/at marker loot_table minecraft:chests/simple_dungeon`.

## Inspecting markers

`/at marker info` shows the loot table of the marker or container you are looking at.
