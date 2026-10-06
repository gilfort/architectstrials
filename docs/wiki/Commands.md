# Commands

All commands require **operator permissions** (level 2). Every command is available as `/at …` and,
equivalently, as `/architectstrials …` — same sub commands, permissions and tab completion. If another mod also
registers `/at`, the mod loaded last wins; `/architectstrials` always works (use it in functions shipped with a
modpack to be safe).

## Themes & travel

| Command | Description |
|---|---|
| `/at theme list` | Lists all loaded themes and their available tiers |
| `/at theme tp <theme>` | Teleports you into a theme's dimension (void world — use creative/spectator); stores your entry point, keeps your game mode |
| `/at enter <theme>` | Debug entry into a theme like a real challenge: stores your entry point, switches to Adventure |
| `/at exit [targets]` | Leaves any Architect's Trials dimension: back to the entry point, or to the respawn point / world spawn if none is stored |

## Scrolls & ranks

| Command | Description |
|---|---|
| `/at scroll give <theme> <tier> [targets]` | Gives a challenge scroll bound to a theme and tier |
| `/at rank <targets> <theme> set <level>` | Sets the rank of players for a theme |
| `/at rank <targets> <theme> add <amount>` | Changes the rank (negative amounts lower it) |
| `/at rank <targets> <theme> get` | Shows the rank; the command result is the level (usable with `execute store`) |

## Editor & structures

| Command | Description |
|---|---|
| `/at editor enter` | Enters the shared editor dimension |
| `/at editor clear` + `/at editor clear confirm` | Empties the editor (confirm within 30 s) |
| `/at editor import` (+ `confirm` above 100,000 blocks) | Copies the area selected with the Selection Tool into the empty editor; spawners become markers |
| `/at editor save <theme> <tier> <id> [overwrite]` | Saves the editor content as a pool structure |
| `/at editor load <theme> <tier> <id>` | Loads a stored structure into the empty editor |
| `/at structure list [theme] [tier]` | Lists stored structures |
| `/at structure validate [theme] [tier]` | Checks stored structures for missing blocks, items, entity types, loot tables and mob effects (e.g. after removing a mod); lists only structures with problems |
| `/at structure set <theme> <tier> <id> weight <n>` | Sets the draw weight |
| `/at structure set <theme> <tier> <id> rotation <true\|false>` | Allows random rotation and mirroring |
| `/at structure set <theme> <tier> <id> game_mode <adventure\|survival>` | Game mode players enter in (Survival for mining rooms) |
| `/at structure set <theme> <tier> <id> name <text>` | Sets the display name |
| `/at structure delete <theme> <tier> <id>` + `confirm` | Deletes a stored structure (confirm within 30 s) |

## Markers & loot

| Command | Description |
|---|---|
| `/at marker loot_table <id>` | Sets the loot table of the marker or container you are looking at: Exit Marker = completion bonus override, Trial Spawner Marker = reward, lootable container = pool loot |
| `/at marker loot_table clear` | Removes it |
| `/at marker equipment_table <row> <id>`, `… <row> clear` | Sets or removes the equipment loot table of a row of the mob marker you are looking at (rows from 1; Trial Spawner Marker: 4–6 = ominous page) |
| `/at marker ominous_loot_table <id>\|clear` | Sets or removes the reward of the ominous variant of the Trial Spawner Marker you are looking at |
| `/at marker info` | Shows the loot table and [loot setup](Loot#loot-tool-composed-rewards) of the marker or container you are looking at (Trial Spawner Marker: also the ominous setting and reward) |

## Instances & slots (debugging)

| Command | Description |
|---|---|
| `/at instance list` | Lists all instances with state, remaining time and participant count |
| `/at instance create <theme> <tier> [join]` | Creates an instance: draws a structure, places it in a free slot and resolves its markers; `join` lets you enter at a random spawn marker |
| `/at instance close <id>` | Returns the participants and removes an instance |
| `/at slot list <theme>` | Lists occupied and clearing slots of a theme dimension |
| `/at slot allocate <theme>` | Allocates the next free slot |
| `/at slot free <theme> <index>` | Releases a slot; its content is cleared over the next ticks |
