# Scrolls

There is exactly one scroll item, `architectstrials:challenge_scroll`. Everything a scroll does is stored in
data components, so any datapack-driven recipe, loot table or quest reward can produce scrolls.

## Using a scroll

Right-click while looking at a block: a particle portal opens in front of that block face (needs 1 × 2 free
blocks; it may clip into walls). The portal forms briefly, then becomes active. In the solo default only the
player who used the scroll can enter, and the portal closes behind them.

The scroll is consumed only if the portal opened — never when the tier's pool is empty, the instance limit is
reached, space is missing, the player's rank is too low or the scroll is used inside an Architect's Trials
dimension. If nobody enters within `portalTimeoutSeconds` (default 60), the portal collapses, the instance is
cleaned up and the scroll drops again with a 50 % chance.

## Theme and tier

The component `architectstrials:challenge` binds the scroll to a theme and tier:

```json
"result": {
  "id": "architectstrials:challenge_scroll",
  "components": { "architectstrials:challenge": { "theme": "mypack:crypt", "tier": 2 } }
}
```

For testing: `/at scroll give <theme> <tier> [targets]`.

## Time limit

`architectstrials:time_limit` sets the challenge's time limit in **minutes**; without it the server config's
`defaultTimeLimitMinutes` (60) applies. The time limit is never shorter than the portal's open time. See
[Instances & Lifecycle](Instances-and-Lifecycle#time-limit).

```json
"components": {
  "architectstrials:challenge": { "theme": "mypack:crypt", "tier": 2 },
  "architectstrials:time_limit": 30
}
```

## Multiplayer options

`architectstrials:options` controls who may enter and how long the portal stays open. The options are fixed
into the instance when the portal opens. All fields are optional; without the component a scroll is solo.

| Field | Values | Default |
|---|---|---|
| `max_players` | `1` = only the scroll user, `n` = up to n distinct players, `0` = unlimited | `1` |
| `portal_open_seconds` | `0` = closes after the first pass-through, `>0` = open that many seconds once active, `-1` = open until the time limit expires | `0` |
| `allow_reentry` | `true` / `false` | `false` |

```json
"components": {
  "architectstrials:challenge": { "theme": "mypack:crypt", "tier": 2 },
  "architectstrials:options": { "max_players": 4, "portal_open_seconds": 120, "allow_reentry": true }
}
```

- Scrolls for more than one player keep the portal open **at least 15 seconds**, so everyone can get in —
  shorter values (including `0`) are raised automatically.
- Anyone may walk into an open portal until `max_players` is reached — there is no party system, and late
  joiners are not rank-checked. Everyone lands on a random player spawn marker; nothing is rebalanced.
- The portal closes when its time is up or `max_players` is reached, whichever comes first. With
  `allow_reentry` it stays open for its full time so players can come back.
- Re-entry is for players who left without completing (e.g. after being saved by the Dimension Ward) and does
  not count against `max_players`. Players who completed the run can never re-enter.

## Upgrades at the smithing table

Scrolls can be upgraded at the vanilla smithing table with the recipe type `architectstrials:scroll_upgrade`.
Template and addition are freely chosen per recipe — the mod ships no template item. The result is the same
scroll with all its data (theme, tier, time limit, options, earlier upgrades) plus the recipe's upgrades.

```json
{
  "type": "architectstrials:scroll_upgrade",
  "template": "minecraft:paper",
  "base": "architectstrials:challenge_scroll",
  "addition": "minecraft:rabbit_foot",
  "options": { "max_players": 4 },
  "effects": [
    { "target": "player", "effect": "minecraft:luck", "duration": -1 },
    { "target": "mobs", "effect": "minecraft:speed", "duration": 6000, "amplifier": 1 }
  ]
}
```

Place it as `data/<ns>/recipe/<name>.json`.

### Options

`options` (optional) sets `max_players`, `portal_open_seconds` and/or `allow_reentry`; the new values overwrite
the scroll's values.

### Effects

`effects` (optional) adds [mob effects](https://minecraft.wiki/w/Effect) — vanilla or from other mods:

| Field | Description |
|---|---|
| `target` | `player` — every player entering; `mobs` — every mob of the instance |
| `effect` | Effect id, e.g. `minecraft:luck` |
| `duration` | Ticks (20 = 1 second); `-1` = infinite |
| `amplifier` | `0` = level I (default), `1` = level II, … |

- **Player effects** are applied on every entry and show **no particles** (only the icon). They always sit on top
  of an effect of the same type the player already has (e.g. a potion):
  - behind a **finite** scroll effect the player's own effect is parked and resumes in the challenge once the
    scroll effect runs out (a 3-minute potion + a 5-minute scroll effect = 5 minutes scroll effect, then the
    3 minutes of the potion);
  - an **infinite** scroll effect is removed when the player leaves the challenge, and the player's own effect
    comes back with the time it had on entry.
- **Mob effects** are applied to all mobs on the first entry and to every mob that spawns or loads afterwards;
  they keep their particles as a hint to the players.
- Applying an effect the scroll already has replaces it if the new one is **stronger or longer**; otherwise (and
  if a recipe would change nothing at all) the smithing table shows no result.
- There is no upgrade cap — control it with your recipes.

The scroll tooltip lists all upgrades, e.g. "Players: Luck (∞)" and "Enemies: Speed II (05:00)".

> Recipes from other systems usually cannot keep the scroll's data. Create base scrolls there (theme and tier
> in the result components) and upgrade them with this recipe type or KubeJS.

## Ranks

Every player has a **rank per theme**. A scroll of tier *n* can only be used by a player whose rank for that
theme is at least *n*; the tooltip shows the requirement in green or red. Players start at `startingRank`
(default 1); with `0`, all scrolls are blocked until the player is upgraded.

Raise ranks from anywhere a command runs — typically an advancement reward function:

```mcfunction
# data/mypack/function/crypt_unlock_tier_2.mcfunction
at rank @s mypack:crypt set 2
```

Only the player opening a portal is checked — stronger players can open challenges for weaker ones.
See [Advancements & Events](Advancements-and-Events) for unlocking tiers after completed runs.
