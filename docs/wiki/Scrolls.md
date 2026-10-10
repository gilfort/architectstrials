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
cleaned up and the scroll drops again with the chance set in `unusedPortalScrollDropChance` (default 50 %, see
[Configuration](Configuration)).

## Theme and tier

The component `architectstrials:challenge` binds the scroll to a theme and tier:

```json
"result": {
  "id": "architectstrials:challenge_scroll",
  "components": { "architectstrials:challenge": { "theme": "mypack:crypt", "tier": 2 } }
}
```

For testing: `/at scroll give <theme> <tier> [targets]`.

## Crafting scrolls and raising the tier

Players can progress without commands: a recipe of type `architectstrials:scroll_tier` (smithing table) defines one
step of a theme. Write one short JSON per step into `data/<ns>/recipe/`:

```json
{
  "type": "architectstrials:scroll_tier",
  "theme": "mypack:crypt",
  "tier": 1,
  "template": "minecraft:paper",
  "addition": "minecraft:bone"
}
```

- `tier: 1` turns the **Blank Challenge Scroll** into a tier 1 scroll of the theme.
- `tier: 2` (and higher) takes a scroll of the theme with tier 1 (one below) and raises it; modifiers, effects and
  name are kept.
- No result if the theme has no challenge of that tier yet, so recipes for future tiers can be added in advance.
- `template` and `addition` are normal ingredients (items or `#tags`); each step can use different ones.
- The recipe book and recipe viewers show every step like any other smithing recipe.
- A scroll whose `architectstrials:challenge` component has no `tier` counts as tier 1.

Example pack with three steps: `crypt_tier_1.json` (paper + bone), `crypt_tier_2.json` (paper + bone block),
`crypt_tier_3.json` (paper + wither skeleton skull).

## Time limit and players come from the challenge

Every challenge defines how it is played — its time limit, how many players may enter, how long the portal stays
open and whether re-entry is allowed (see [Building Challenges](Building-Challenges#run-settings-time-limit-and-players)).
A 2-minute loot room and an hour-long boss room need different times, so the scroll does not carry them. It can only
**modify** the values of the challenge it opens, with the component `architectstrials:modifiers`:

```json
"components": {
  "architectstrials:challenge": { "theme": "mypack:crypt", "tier": 2 },
  "architectstrials:modifiers": { "time": 25, "max_players": 2, "allow_reentry": true }
}
```

| Field | Effect | Example |
|---|---|---|
| `time` | Time limit in percent | `25` = ×1.25, `-50` = ×0.5 (a 4-minute room gets 2 minutes, an hour 30 minutes) |
| `portal_open` | Positive portal open time of the challenge in percent (`0` and `-1` stay as they are) | `100` = twice as long |
| `max_players` | Additional players; `0` = unlimited | `2` turns a 2-player room into a 4-player room |
| `allow_reentry` | `true` switches re-entry on; a scroll can never switch it off | |

- All fields are optional; without the component the challenge's own values apply.
- Percentages must be above −100. The time limit never drops below one second.
- The tooltip lists the modifiers ("Time: +25 %", "Players: +2"); the actual time depends on the challenge drawn
  when the portal opens.
- The options are fixed into the instance when the portal opens. **The time limit starts when the first player
  enters**, not when the portal opens — forming, placement and waiting for the first player do not count. See
  [Instances & Lifecycle](Instances-and-Lifecycle#time-limit).

### Who may enter

- Scrolls for more than one player keep the portal open **at least 15 seconds**, so everyone can get in —
  shorter values (including `0`) are raised automatically.
- Anyone may walk into an open portal until the player limit is reached — there is no party system, and late
  joiners are not rank-checked. Everyone lands on a random player spawn marker; nothing is rebalanced.
- The portal closes when its time is up, the player limit is reached or the time limit expires, whichever comes
  first. With re-entry it stays open for its full time so players can come back.
- Re-entry is for players who left without completing (e.g. after being saved by the Dimension Ward) and does
  not count against the player limit. Players who completed the run can never re-enter.

## Upgrades at the smithing table

Scrolls can be upgraded at the vanilla smithing table with the recipe type `architectstrials:scroll_upgrade`.
Template and addition are freely chosen per recipe — the mod ships no template item. The result is the same
scroll with all its data (theme, tier, modifiers, earlier upgrades) plus the recipe's upgrades.

```json
{
  "type": "architectstrials:scroll_upgrade",
  "template": "minecraft:paper",
  "base": "architectstrials:challenge_scroll",
  "addition": "minecraft:rabbit_foot",
  "modifiers": { "time": 25, "max_players": 2 },
  "effects": [
    { "target": "player", "effect": "minecraft:luck", "duration": -1 },
    { "target": "mobs", "effect": "minecraft:speed", "duration": 6000, "amplifier": 1 }
  ]
}
```

Place it as `data/<ns>/recipe/<name>.json`.

### Modifiers

`modifiers` (optional) adds [modifiers](#time-limit-and-players-come-from-the-challenge) to the scroll's own:
percentages **multiply** (two upgrades with `"time": 25` give +56.25 %, two with `-50` give −75 %), additional players
add up (unlimited wins), re-entry stays on once switched on.

### Effects

`effects` (optional) adds [mob effects](https://minecraft.wiki/w/Effect) — vanilla or from other mods:

| Field | Description |
|---|---|
| `target` | `player` — every player entering; `mobs` — every mob of the instance |
| `effect` | Effect id, e.g. `minecraft:luck` |
| `duration` | Ticks (20 = 1 second); `-1` = permanent (players: the remaining time of the instance; mobs: infinite) |
| `amplifier` | `0` = level I (default), `1` = level II, … |

- **Player effects** are applied once on every entry and show **no particles** (only the icon). A permanent
  effect lasts the remaining time of the instance; nothing is re-applied, so milk removes it for the rest of that
  stay. They always sit on top of an effect of the same type the player already has (e.g. a potion), which is
  parked behind them: behind a finite scroll effect it resumes once the scroll effect runs out (a 3-minute potion +
  a 5-minute scroll effect = 5 minutes scroll effect, then the 3 minutes of the potion). A
  [challenge effect](Building-Challenges#challenge-effects-and-attributes) of the same type waits between the two.
- **Leaving** removes all player effects of the scroll and gives the player's own effects back with the time they
  had on entry.
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
