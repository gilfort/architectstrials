# Datapack Reference

All JSON formats of the mod at a glance. `<ns>` is your datapack namespace.

## Files

| File | Purpose | Page |
|---|---|---|
| `data/<ns>/dimension/<theme>.json` | Theme dimension (vanilla format) | [Themes & Dimensions](Themes-and-Dimensions) |
| `data/<ns>/dimension_type/<name>.json` | Optional own dimension type (vanilla format) | [Themes & Dimensions](Themes-and-Dimensions#the-dimension-type) |
| `data/<ns>/architectstrials/challenge_dimensions.json` | Theme list | [Themes & Dimensions](Themes-and-Dimensions#2-the-theme-list) |
| `data/<ns>/structure/challenges/<theme>/tier_<n>/<id>.nbt` | Structure template (written by the editor) | [Building Challenges](Building-Challenges) |
| `data/<ns>/architectstrials/challenge/<theme>/tier_<n>/<id>.json` | Structure metadata (written by the editor) | [below](#structure-metadata) |
| `data/<ns>/structure/sub/<id>.nbt` | Sub structure template (written by the editor) | [Building Challenges](Building-Challenges#sub-structures) |
| `data/<ns>/architectstrials/sub/<id>.json` | Sub structure metadata | [below](#sub-structure-metadata) |
| `data/<ns>/loot_table/architectstrials/completion/<theme>/tier_<n>.json` | Completion bonus by convention | [Loot](Loot#completion-bonus) |
| `data/<ns>/recipe/<name>.json` with `architectstrials:scroll_upgrade` | Scroll upgrade | [Scrolls](Scrolls#upgrades-at-the-smithing-table) |
| `data/<ns>/advancement/…` with `architectstrials:run_completed` | Progression | [Advancements & Events](Advancements-and-Events) |

## Theme list

```json
{
  "replace": false,
  "values": ["<ns>:<theme>"]
}
```

## Structure metadata

```json
{
  "theme": "<ns>:<theme>",
  "tier": 1,
  "structure": "<ns>:challenges/<theme>/tier_1/<id>",
  "name": "Optional display name",
  "author": "Optional",
  "created": 1790000000000,
  "weight": 1,
  "rotation": false,
  "game_mode": "adventure",
  "ore_generation": {
    "biome": "minecraft:plains",
    "min_y": -64,
    "max_y": 319,
    "density": 1.0
  }
}
```

| Field | Required | Description |
|---|---|---|
| `theme` | yes | Theme id |
| `tier` | yes | Tier (≥ 1) |
| `structure` | yes | Structure template id |
| `name`, `author`, `created` | no | Informational (`created` = epoch milliseconds) |
| `weight` | no (1) | Draw weight within the pool |
| `rotation` | no (false) | Random rotation and mirroring on placement |
| `game_mode` | no (`adventure`) | Game mode players enter in: `adventure` or `survival` (mining rooms) |
| `player_effects` | no | List of `{effect, amplifier (0), duration (-1 = whole stay)}` every player gets on entry |
| `ore_generation` | no | Natural ores on instance creation: `biome` (required), `min_y` / `max_y` (simulated height of the lowest / highest layer; default: the biome's dimension), `density` (ore attempt factor; default: vanilla density). See [Mining rooms](Building-Challenges#natural-ores) |
| `player_attributes` | no | List of `{attribute, amount, operation (add_value \| add_multiplied_base \| add_multiplied_total)}` while inside |
| `mob_effects` | no | List of `{effect, amplifier (0), duration (-1 = infinite)}` every mob of the instance gets |
| `time_limit` | no (config) | Time limit in seconds, starting with the first entry. See [Run settings](Building-Challenges#run-settings-time-limit-and-players) |
| `max_players` | no (1) | `1` = solo, `n` = up to n players, `0` = unlimited |
| `portal_open_seconds` | no (0) | `0` = closes after the first pass-through, `>0` = seconds open, `-1` = until the time limit expires |
| `allow_reentry` | no (false) | Re-entry after leaving without completing |

## Sub structure metadata

```json
{
  "structure": "<ns>:sub/<id>",
  "name": "Optional display name",
  "author": "Optional",
  "created": 1790000000000
}
```

The sub structure's id is `<ns>:<id>` (file path below `architectstrials/sub/`). Setups live in the Sub Structure
Markers' block entities (saved with the structure):

```json
{
  "entries": [ { "structure": "mypack:alcove_gold", "percent": 20 }, { "structure": "mypack:alcove_trap", "percent": 30 } ],
  "fallback": "mypack:alcove_empty"
}
```

## Scroll components

| Component | Format | Page |
|---|---|---|
| `architectstrials:challenge` | `{ "theme": "<ns>:<theme>", "tier": 2 }` | [Scrolls](Scrolls#theme-and-tier) |
| `architectstrials:modifiers` | `{ "time": -50, "portal_open": 100, "max_players": 2, "allow_reentry": true }` | [Scrolls](Scrolls#time-limit-and-players-come-from-the-challenge) |
| `architectstrials:effects` | list of effect entries (see below) | [Scrolls](Scrolls#effects) |

A complete scroll as a recipe result:

```json
"result": {
  "id": "architectstrials:challenge_scroll",
  "components": {
    "architectstrials:challenge": { "theme": "mypack:crypt", "tier": 2 },
    "architectstrials:modifiers": { "time": -25, "max_players": 2 },
    "architectstrials:effects": [
      { "target": "mobs", "effect": "minecraft:glowing", "duration": -1 }
    ]
  }
}
```

## Effect entry

```json
{ "target": "player", "effect": "minecraft:luck", "duration": -1, "amplifier": 0 }
```

| Field | Required | Description |
|---|---|---|
| `target` | yes | `player` or `mobs` |
| `effect` | yes | Mob effect id (vanilla or modded) |
| `duration` | yes | Ticks; `-1` = infinite |
| `amplifier` | no (0) | `0` = level I |

## Scroll upgrade recipe

```json
{
  "type": "architectstrials:scroll_upgrade",
  "template": "minecraft:paper",
  "base": "architectstrials:challenge_scroll",
  "addition": "minecraft:rabbit_foot",
  "modifiers": { "time": 25 },
  "effects": [ { "target": "player", "effect": "minecraft:luck", "duration": -1 } ]
}
```

| Field | Required | Description |
|---|---|---|
| `template` | no | Ingredient in the template slot |
| `base` | yes | Ingredient in the base slot (the scroll) |
| `addition` | no | Ingredient in the addition slot |
| `modifiers` | no | Any of `time`, `portal_open`, `max_players`, `allow_reentry`; combined with the scroll's (percentages multiply) |
| `effects` | no | List of effect entries |

## Advancement criterion

```json
{
  "trigger": "architectstrials:run_completed",
  "conditions": { "theme": "<ns>:<theme>", "tier": { "min": 2 }, "runs": { "min": 5 } }
}
```

All conditions optional; see [Advancements & Events](Advancements-and-Events).
