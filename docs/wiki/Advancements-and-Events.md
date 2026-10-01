# Advancements & Events

## Advancement criterion `architectstrials:run_completed`

Fires when a player completes a run (walks through an open exit).

| Condition | Type | Description |
|---|---|---|
| `player` | entity predicate | Optional conditions on the player |
| `theme` | identifier | Only runs of this theme |
| `tier` | number range | Tier of the run, e.g. `2` or `{ "min": 2 }` |
| `runs` | number range | How many runs the player has completed matching `theme` and `tier`, **including this one** |

All conditions are optional.

### Example: unlock tier 2 after five tier-1 runs

`data/mypack/advancement/crypt/unlock_tier_2.json`:

```json
{
  "criteria": {
    "five_runs": {
      "trigger": "architectstrials:run_completed",
      "conditions": { "theme": "mypack:crypt", "tier": 1, "runs": { "min": 5 } }
    }
  },
  "rewards": { "function": "mypack:crypt_unlock_tier_2" }
}
```

`data/mypack/function/crypt_unlock_tier_2.mcfunction`:

```mcfunction
at rank @s mypack:crypt set 2
```

Use `architectstrials rank …` instead of `at rank …` if another mod in your pack also registers `/at`.

## For mod developers: `RunCompletedEvent`

Other mods can react to completed runs via `com.gilfort.architectstrials.run.RunCompletedEvent` on the NeoForge
event bus. It provides the player (`getPlayer()`) and the completed instance (`getInstance()`: theme, tier,
structure, options, …).

```java
@SubscribeEvent
static void onRunCompleted(RunCompletedEvent event) {
    ServerPlayer player = event.getPlayer();
    int tier = event.getInstance().tier();
    // …
}
```
