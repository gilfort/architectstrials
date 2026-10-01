# Themes & Dimensions

A challenge **theme is a dimension**. The dimension provides the atmosphere — sky, fog, light, biome
ambience — and all challenge instances of the theme are placed inside it, each in its own slot far away from
the others (see [Instances & Lifecycle](Instances-and-Lifecycle)).

## Defining a theme

A theme takes two files in a datapack.

### 1. The dimension

`data/<namespace>/dimension/<theme>.json` — the vanilla dimension format, one file per theme:

```json
{
  "type": "architectstrials:challenge",
  "generator": {
    "type": "minecraft:flat",
    "settings": {
      "biome": "minecraft:the_void",
      "features": false,
      "lakes": false,
      "layers": [],
      "structure_overrides": []
    }
  }
}
```

- An empty flat generator is recommended: structures are placed into the void.
- The **biome** sets the ambience (particles, sounds, music). Choose e.g. `minecraft:soul_sand_valley` for a
  ghostly nether theme.
- Natural mob spawning is always disabled in challenge dimensions, whatever the biome — enemies only come from
  the structure's [mob markers](Markers#mob-markers).

### 2. The theme list

`data/<namespace>/architectstrials/challenge_dimensions.json`:

```json
{
  "replace": false,
  "values": ["<namespace>:<theme>"]
}
```

All list files of all active datapacks are merged like tags. `"replace": true` discards entries from
lower-priority packs. Entries pointing to a dimension that does not exist are logged and skipped.

> **Restart required:** Minecraft only creates datapack dimensions on server start. After adding a new theme
> dimension, restart the server (or reopen the singleplayer world). `/reload` only picks up changes to the
> theme list.

### Display name

Themes use the lang key `dimension.<namespace>.<theme>`. Datapacks cannot ship lang files, so provide the
translation via a resource pack; otherwise the theme id is shown (e.g. on scrolls).

```json
{ "dimension.mypack.crypt": "The Crypt" }
```

## The dimension type

`architectstrials:challenge` is the dimension type shipped with the mod:

- fixed time of day, no weather (`has_ceiling` is set),
- beds and respawn anchors do not work, raids and pillager patrols are disabled,
- light blue sky and fog colors.

You may use your own `dimension_type` instead, e.g. to give a Nether theme a Nether sky. Copy
[`challenge.json`](https://github.com/gilfort/architectstrials/blob/main/src/main/resources/data/architectstrials/dimension_type/challenge.json)
into your datapack as `data/<namespace>/dimension_type/<name>.json`, adjust it and reference it from the
dimension's `"type"`. Keep the bed and respawn anchor rules — challenges are not meant to be a home.

## Time of day

Since Minecraft 26.x the time of day is data-driven: **environment attributes** in the `dimension_type` plus
**timelines** driven by a **world clock**. `/time set` has no effect in `architectstrials:challenge` because it
has a fixed time and no clock. To give a theme its own time of day, use your own dimension type and either

- keep `"has_fixed_time": true` and set static visual attributes such as `minecraft:visual/sun_angle`,
  `minecraft:visual/sky_light_factor`, `minecraft:gameplay/sky_light_level`, `minecraft:visual/star_brightness`,
  `minecraft:visual/sunrise_sunset_color`, `minecraft:visual/sky_color` and `minecraft:visual/fog_color` for a
  permanent dusk or night, or
- use `"has_fixed_time": false`, `"timelines": "#minecraft:in_overworld"` and
  `"default_clock": "minecraft:overworld"` to follow the overworld's day cycle (then `/time set` changes the
  overworld too).

> Tested presets (dusk, night, nether-like, …) will be added here once they have been tried out in game.

## Tiers

A theme has no fixed number of tiers: its tiers are simply the tiers that have at least one stored structure.
`/at theme list` shows every theme with its available tiers. See
[Building Challenges](Building-Challenges#structure-pools-and-tiers).
