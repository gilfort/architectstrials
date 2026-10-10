# Challenge Browser

`/at gui` opens an in-game overview of everything your datapacks contain: all themes, their challenge structures
per tier and all sub structures — with their settings, the content of their templates and any problems. From there
you jump straight into the [editor](Building-Challenges#the-editor) with **Load to editor**.

Like all `/at` commands, the browser is for operators only.

## Layout

| Area | Content |
|---|---|
| Left | All themes and, below a line, **Sub Structures**. The number in brackets is the count of entries; a red `!n` shows how many of them have problems. |
| Middle | The challenges of the selected theme, grouped by tier (click a tier header to fold it), or all sub structures. The search field filters by name and id. |
| Right | The details of the selected entry; the action buttons are below them. |

Selecting a theme folds the left column into a narrow strip with an arrow (`←`), selecting a challenge folds the
middle column the same way — the details get the room. Click a strip to unfold its column again. The browser
remembers your last selection.

## Details

**Metadata** — name, id, source pack, theme, tier, weight with the resulting **draw chance** within its tier (e.g.
weight 3 of a total of 8 → 37.5 %), rotation, game mode,
[run settings](Building-Challenges#run-settings-time-limit-and-players) (time limit, players, portal, re-entry), author,
creation date, player effects, mob effects, player attributes and [ore generation](Building-Challenges#natural-ores).

**Template** — what the stored structure contains:

- size (X × Y × Z),
- Player Spawn Markers and Exit Markers (and how many exits need all required mobs defeated),
- enemies per marker type, e.g. `Piglin Brute ×1 (Direct Spawn) – required`, `Zombie ×8 (Spawner)`; trial spawners
  list their normal and ominous mobs separately,
- loot: containers with a loot table (per table), [loot setups](Loot#loot-tool-composed-rewards) and containers
  filled by hand,
- Vault Markers,
- sub structures referenced by [Sub Structure Markers](Markers#sub-structure-marker) — click one to open it.

**Problems** — everything [`/at structure validate`](Commands#editor--structures) reports (blocks, items, mobs,
loot tables or effects that no longer exist), plus references to unknown sub structures. A theme that is not in any
[theme list](Themes-and-Dimensions#2-the-theme-list) is shown in red: its challenges can never be drawn.

For **sub structures**, **Used by** lists every challenge whose Sub Structure Markers place it (as an entry or as
the fallback) — check it before changing or deleting a piece. Click a challenge to open it.

## Sources and read-only entries

The browser shows everything that is in the pool: the structures saved with the editor
(`<world>/datapacks/architectstrials_structures/`) **and** those from other datapacks or mods, e.g. the modpack's
datapacks folder. Entries from other packs show a lock and their pack in grey: the mod never changes files outside
its own pack. Load such an entry into the editor and save it under a theme, tier and id to get an editable copy.

## Load to editor

**Load to editor** loads the selected structure into the shared editor and takes you there (also from a challenge or
another dimension; the way back is your entry point as usual).

- If the editor is **empty**, it is loaded right away.
- If something is built in it, a dialog asks first: everything in the editor will be deleted. It names the players
  currently in the editor and the structure that was last loaded or saved there (unknown after `editor clear`,
  `editor import` or building from scratch). **Clear and load** clears the editor, loads the structure and takes
  you in; **Cancel** keeps everything.

The commands `/at editor clear`, `/at editor load` and `/at editor load sub` work as before; the browser is a
shortcut.

## Up to date

The data is a snapshot taken when the browser opens. **Refresh** fetches a new one. After every datapack reload —
which also follows `editor save`, `structure set` and `structure delete` — the server sends open browsers a fresh
snapshot automatically.

Building a snapshot reads every template file once; with very many large structures, opening the browser right after
a reload can take a moment.
