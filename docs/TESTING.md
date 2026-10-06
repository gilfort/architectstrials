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
- [ ] Mobs effects of scroll upgrades (target `mobs`) still apply to mobs in the instance (uses the new slot
      lookup).
