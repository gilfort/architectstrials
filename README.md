# Architect's Trials

[![Build](https://github.com/gilfort/architectstrials/actions/workflows/build.yml/badge.svg)](https://github.com/gilfort/architectstrials/actions/workflows/build.yml)

A NeoForge mod for Minecraft 26.3. Players open portals with scrolls into themed challenge dimensions, fight and
loot inside hand-built structures and always return without losing their inventory. All content (themes,
structures, tiers, scrolls) is defined by modpack creators via datapacks and an in-game editor — no Java code
required.

**📖 Documentation: [Wiki](https://github.com/gilfort/architectstrials/wiki)** — quick start, guides and the
complete datapack and command reference.

## Features

- **Themes are dimensions** — sky, fog, light and biome ambience per theme, defined by datapack.
- **In-game editor** — build challenges in a shared void dimension and save them into tiered structure pools
  with one command.
- **Markers** — player spawns, redstone-lockable and camouflaged exits, mobs with custom equipment (optionally
  required to open the exits), spawners, trial spawners, vaults and randomly rolled sub structures; configured
  with ghost slots (no real items needed, JEI drag & drop) and copied with the Spawn Marker Tool.
- **Varied rooms** — sub structures with chances, natural ore generation per instance, Survival mining rooms,
  challenge-wide effects and attributes.
- **Loot** — pool loot via vanilla loot tables (fresh per run), fixed loot, completion bonus per theme and tier.
- **Scrolls** — theme, tier, time limit and multiplayer options as data components; crafting and tier upgrades,
  upgrades with options and effects at the smithing table.
- **Progression** — ranks per theme, an advancement trigger for completed runs and an event for other mods.
- **Safe** — instances in separate slots, time limits with boss bar, and a death protection that always brings
  players home with their full inventory.

## Installation

Requires NeoForge for Minecraft 26.3. Put the mod jar into the `mods` folder of client and server. Commands are
available as `/at …` (or `/architectstrials …`) for operators.

## For developers

```bash
./gradlew build              # mod jar -> build/libs/
./gradlew runClient
./gradlew runGameTestServer  # runs all GameTests headless
```

GameTests and their test datapack live in `src/gametest` and are never included in the mod jar. The test datapack
also defines the dimension `architectstrials:gametest_theme` for manual testing in `runClient`
(`/at instance create architectstrials:gametest_theme 1 join`) and a few scroll upgrade recipes.

The wiki is generated from [`docs/wiki/`](docs/wiki) by a GitHub Action on every push to `main` — edit the
Markdown files there, never the wiki directly.

## License

GPL-3.0 — see [LICENSE](LICENSE).
