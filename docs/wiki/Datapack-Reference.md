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
  "game_mode": "adventure"
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

## Scroll components

| Component | Format | Page |
|---|---|---|
| `architectstrials:challenge` | `{ "theme": "<ns>:<theme>", "tier": 2 }` | [Scrolls](Scrolls#theme-and-tier) |
| `architectstrials:time_limit` | minutes, e.g. `30` | [Scrolls](Scrolls#time-limit) |
| `architectstrials:options` | `{ "max_players": 4, "portal_open_seconds": 120, "allow_reentry": true }` | [Scrolls](Scrolls#multiplayer-options) |
| `architectstrials:effects` | list of effect entries (see below) | [Scrolls](Scrolls#effects) |

A complete scroll as a recipe result:

```json
"result": {
  "id": "architectstrials:challenge_scroll",
  "components": {
    "architectstrials:challenge": { "theme": "mypack:crypt", "tier": 2 },
    "architectstrials:time_limit": 30,
    "architectstrials:options": { "max_players": 4, "portal_open_seconds": 60 },
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
  "options": { "max_players": 4 },
  "effects": [ { "target": "player", "effect": "minecraft:luck", "duration": -1 } ]
}
```

| Field | Required | Description |
|---|---|---|
| `template` | no | Ingredient in the template slot |
| `base` | yes | Ingredient in the base slot (the scroll) |
| `addition` | no | Ingredient in the addition slot |
| `options` | no | Any of `max_players`, `portal_open_seconds`, `allow_reentry` |
| `effects` | no | List of effect entries |

## Advancement criterion

```json
{
  "trigger": "architectstrials:run_completed",
  "conditions": { "theme": "<ns>:<theme>", "tier": { "min": 2 }, "runs": { "min": 5 } }
}
```

All conditions optional; see [Advancements & Events](Advancements-and-Events).
