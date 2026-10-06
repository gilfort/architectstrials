# Test plan

Two kinds of tests exist:

- **GameTests** (automated) run on every push and pull request in GitHub Actions (`./gradlew runGameTestServer`,
  workflow *Build*). They use mock players and cannot check rendering, real network clients or feel.
- **Manual tests** (this file) cover what GameTests cannot: real clients, multiplayer, visuals, performance on a
  real server. Tick them off after each implemented issue.

How to run manually: `./gradlew runClient` (single player) or `./gradlew runServer` plus one or two clients. The test
datapack in `src/gametest` is active in dev runs, so `architectstrials:gametest_theme` (tier 1: small platform) and
`minecraft:the_nether` (tier 1–3, tier 4: 48 × 48 large platform for placement tests) can be used directly.

Useful commands: `/at instance create <theme> <tier> join`, `/at instance list`, `/at instance close <id>`,
`/at scroll give …`, `/tick query` (tick times), `/debug start` / `/debug stop` (profiling).

---

## #54 CI: build and GameTests on every PR

Automated: the workflow itself is the test.

- [ ] Open a pull request — the *Build* check appears and runs build + GameTests.
- [ ] Actions → a run → *Artifacts*: `architectstrials-<commit>` contains the mod jar; it loads in a normal
      NeoForge 26.3 installation.
- [ ] README shows the build badge (green).
- [ ] (Optional) Break a GameTest on a test branch — the run turns red.

## #55 Config: instance limit, scroll drop chance, command Y

GameTests: `portal_expired_scroll_drop_chance`.

- [ ] New world: `serverconfig/architectstrials-server.toml` contains `maxConcurrentInstances = 4` and
      `unusedPortalScrollDropChance = 0.5`; both appear with name and tooltip in the Mods → Config screen.
- [ ] With `maxConcurrentInstances = 1`: a second scroll of the same theme fails with "no slot" and is **not**
      consumed while the first instance runs.
- [ ] `unusedPortalScrollDropChance = 1.0`: open a portal, do not enter, wait for the timeout → scroll drops.
      `0.0` → scroll never drops.
- [ ] `structurePlacementY = 100`: `/at theme tp <theme>` and `/at enter <theme>` put you at Y 100.

## US-28 (#53): Spread structure placement over ticks

GameTests: `placement_spreads_over_ticks`, `placement_interrupted_is_discarded`, `slot_lookup_by_position`,
`spawn_marker_structure_validation` (lazy check), plus all existing instance tests (now with immediate placement).

- [ ] **Lag check (most important):** on a dedicated server, open a challenge with a large structure (ideally
      close to 128 × 128) via scroll while watching `/tick query` or the F3 tick graph. No freeze; at most small,
      even bumps for a few seconds.
- [ ] Portal stays in its forming state (rune particles) until the structure is complete; walking into it earlier
      does nothing. Afterwards you enter normally.
- [ ] Placed structure is complete and identical to the editor version: no holes at chunk borders, fences / walls /
      glass panes connected across chunk borders, sand / gravel did not fall, water and lava as built.
- [ ] Rotated structures (`rotation: true`): open several times — structure intact in all orientations, markers
      (spawns, exits, mobs, loot) work.
- [ ] Paintings, item frames and armor stands of the structure are at the right place.
- [ ] Containers with pool loot / fixed loot, spawner and trial spawner markers work as before.
- [ ] `/at instance create <theme> <tier> join` with a large structure: message "Placing the structure …", then you
      are teleported in automatically.
- [ ] Two scrolls opened right after each other (two players): both instances are built one after the other,
      both portals become active.
- [ ] Restart during placement: open a large instance and stop the server immediately (`/stop`). After the
      restart the portal is gone, `/at instance list` does not show the instance, the slot is cleared (teleport to
      it with `/at theme tp` + coordinates, or check that the next instance reuses the slot).
- [ ] A structure without Player Spawn Marker in a pool (e.g. a hand-written JSON): drawing it logs
      "has no player spawn marker and is skipped" once; scrolls then use the other structures of the pool.
- [ ] `/reload` with many stored structures: no noticeable hitch anymore.
- [ ] Mob effects of scroll upgrades (target `mobs`) still apply to mobs in the instance (uses the new slot
      lookup) — also to mobs of a large structure that are far from the spawn point when you enter.

## US-38 (#67): Collision-based exit and entry portal detection

GameTests: `exit_redstone_lock_and_completion` (portal blocks 2 × 3, locked / open / above the portal space /
no double completion), `bonus_exit_override`, all scroll portal and scroll option tests (entry via `playerTouch`).

- [ ] Walk through an open exit (1, 2 and 3 bases wide): run completes once, you are returned, bonus granted.
- [ ] Locked exit (redstone): walking into the portal does nothing; unlock → walking in completes.
- [ ] Standing **next to** the exit or on top of the portal space does not complete.
- [ ] The portal space looks unchanged (animated surface, no visible block, no outline when looking at it); you
      cannot break or place blocks in it, also not in Survival; light passes through.
- [ ] Entry portal: walking in enters the challenge; standing next to it does not; solo / group / re-entry rules
      as before; the "not the owner / full / no re-entry" message still appears while standing in it.
- [ ] Portal timing unchanged (forming → active → closes after the open time).
- [ ] Existing world from before this update with a running instance: log in inside it (or enter it through a
      still open portal) → its exits get portal blocks and work.
- [ ] Spark / profiler: no `RunCompletion` tick handler anymore; with players inside a challenge the mod's own tick
      time is near zero.

## US-27 (#52): Robust data for removed mods + structure validation

GameTests: `missing_ids_are_skipped` (equipment lists, fixed equipment, loot setups, scroll effects, scroll item,
parked effects), `structure_validate_reports_missing_content`.

Best tested with a small content mod (e.g. any mod adding items, blocks and a mob) that you remove afterwards:

- [ ] Build a structure using a block, an item in a chest, an item in a marker slot, a random-equipment entry,
      a loot setup entry and a spawn egg of the mod; give a scroll an effect upgrade of the mod (if it has effects).
- [ ] Remove the mod, start the world (accept the vanilla "missing registry entries" warning).
- [ ] `/at structure validate` lists the structure with the missing block, items, entity type (and effect) — and
      lists nothing for clean structures; summary line counts are right.
- [ ] Open the structure in the editor (`/at editor load`): markers keep their other settings and entries;
      random equipment / loot setup lose only the removed entries.
- [ ] The scroll still exists in the inventory, without the removed effect.
- [ ] Log out inside a running challenge, remove the theme's dimension (datapack) and log in again: you are
      returned to your entry point with a message.
- [ ] The log contains one "Skipped invalid … entry (missing mod?)" warning per skipped entry, no stack traces.
