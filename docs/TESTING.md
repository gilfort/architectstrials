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

## US-31 (#60): Per-challenge game mode (Survival for mining rooms)

GameTests: `game_mode_entry_and_restore`, `game_mode_survival_protection`, `game_mode_metadata_default`.

- [ ] `/at structure set <theme> <tier> <id> game_mode survival`, open a scroll: you arrive in Survival. Another
      structure without the setting: Adventure. `/at structure list` shows the game mode.
- [ ] In the Survival room: mine stone, ores, a chest (contents drop), a spawner → drops as in vanilla; place blocks.
- [ ] The block below a spawn point, the exit bases and the portal space cannot be broken or replaced; a creeper /
      TNT explosion next to them leaves them intact.
- [ ] Leave via exit, death (Dimension Ward), `/at exit`, time limit and log out/in: your original game mode
      (e.g. Creative) is back; mined items are kept.
- [ ] A second player joining later also gets Survival.

## US-30 (#59): Required mobs seal exits

GameTests: `required_mobs_seal_exit` (glow duration, sealed exit, partial / full progress, completion),
`required_mobs_marker_options` (only the Direct Spawn Marker supports it).

- [ ] Direct Spawn Marker GUI: "Required: off/on" button top right; Spawner Marker GUI has no such button.
      Exit Marker: right-click with an empty hand opens a small GUI with "Needs all required mobs defeated".
- [ ] Settings survive `editor save` / `editor load`.
- [ ] In a challenge: required mobs glow (also through walls); the sealed exit has the locked look and no portal;
      walking in shows "Defeat all marked enemies (0/2)" (once per second).
- [ ] Kill the first: still sealed (1/2). Kill the last: exit opens with sound + message, walking in completes.
- [ ] Other ways out of the world count: `/kill @e[type=zombie]`, a required zombie drowning into a drowned, a
      mob pushed into the void. An exit without the setting is never sealed.
- [ ] Milk on a required mob (dispenser) removes the glow; it is not re-applied; the mob still counts.
- [ ] Two instances of the same structure in parallel: killing the mobs in one does not open the other.
- [ ] Server restart with required mobs left: progress is kept, the exit stays sealed until the rest are killed.
- [ ] Combination with redstone: an exit that is powered **and** sealed opens only when both are cleared.

## US-36 (#65): Challenge-defined player effects and attributes (+ new rule for permanent scroll effects)

GameTests: `challenge_effects_entry_and_leave`, `challenge_effects_behind_scroll`, `scroll_effects_applied`
(permanent scroll effects now last the remaining instance time).

- [ ] Add `player_effects` / `player_attributes` to a structure JSON (`/reload`), enter: effects shown with the
      remaining challenge time (not ∞), attribute active (e.g. slower walking).
- [ ] Drink milk inside: effects are gone and stay gone; leaving and re-entering (with re-entry scroll) gives them
      again.
- [ ] Drink a night vision potion before entering a night vision challenge: inside the challenge effect is shown;
      after leaving the potion is back with its old remaining time.
- [ ] Scroll with a permanent effect upgrade: shows the remaining instance time instead of ∞; removed on leaving.
- [ ] Scroll with a short speed effect + challenge speed II: first the scroll's speed, after it runs out speed II.
- [ ] Leave by exit, death, time limit, `/at exit`, log out / in after the instance ended: nothing of it remains.
- [ ] A modded effect / attribute works; a typo in an id only logs a warning.

## US-33 (#62): Craft tier 1 scrolls and raise the tier

GameTests: `scroll_tier_recipes` (blank → tier 1, tier 1 → 2 keeping components, wrong base / theme, cap, default
tier 1).

- [ ] In a dev run (test datapack): smithing table with paper + Blank Challenge Scroll + netherrack → Nether tier 1
      scroll; again with that scroll → tier 2; up to tier 3.
- [ ] Upgraded scrolls (options / effects) keep their upgrades when the tier is raised.
- [ ] The recipe book (smithing tab) and JEI / EMI (if installed) show each step with the right input and result
      scroll (tooltip shows theme and tier).
- [ ] A recipe for a tier without challenges shows no result; adding a structure of that tier + `/reload` makes it
      work.

## US-26 (#49): Vault Marker

GameTests: `vault_marker_resolves` (ominous vault, custom key with count, reward from the loot setup, once per
player), `vault_marker_default_key_and_hint`, `vault_marker_world_import`.

- [ ] Vault Marker in the creative tab; place it (faces you), right-click: Normal/Ominous button, key slot.
- [ ] Loot tool on the marker: compose a reward; `/at marker loot_table <id>` as alternative. Both survive
      `editor save` / `editor load`.
- [ ] In a challenge: the vault looks normal / ominous, shows the floating preview cycling through possible rewards.
- [ ] Click with an empty hand or a wrong item: action bar "Opens with: N× <key>" + vanilla fail sound.
- [ ] Custom key (e.g. 2× renamed item): opens with exactly that (name / components must match), consumes 2.
- [ ] Two players: each can unlock once; a second try of the same player fails. A new instance has a fresh vault.
- [ ] Empty key slot: Trial Key (normal) / Ominous Trial Key (ominous) works.
- [ ] Import a real trial chamber (`/at editor import`): vaults become Vault Markers (message counts them) with
      variant, key and loot table; the resulting challenge works like the original.

## US-34 (#63): Camouflage for exit blocks

GameTests: `exit_camouflage_set_and_remove` (valid / invalid blocks, block state property, save / load, sync data,
GUI remove button), `exit_camouflage_carried_over` (exit gets the camouflage, rotated with the structure).

- [ ] Editor: right-click an Exit Marker with stone bricks → it looks like stone bricks with the green door on the
      front and the arrow on top (pointing to the front); action bar "Exit camouflaged as …".
- [ ] Right-click with a chest, glass, a slab, a torch → red message, nothing changes.
- [ ] Right-click with an oak log looking from the side → log lies sideways as if placed; with stairs / a furnace
      it faces as when placed (furnace is rejected — block entity).
- [ ] Sneak + right-click with a block places it against the marker; camouflage unchanged.
- [ ] Empty hand opens the GUI: block icon + "Camouflage: …", "Remove camouflage" removes it (button grey without
      camouflage). Required-mobs toggle still works.
- [ ] `editor save` / `editor load`: camouflage kept. Rejoin the world / move away and back: still camouflaged.
- [ ] In a challenge: a locked (powered) or sealed exit looks exactly like the block; open exit: block + purple
      glowing lines on all sides; portal above unchanged. Lighting and shading of the block look like the
      surrounding blocks (no dark or too bright faces).
- [ ] Wider exit (2–3 markers, different camouflages): each base shows its own block; one combined portal.
- [ ] Rotated structure: a sideways log camouflage stays aligned with the room.
- [ ] A camouflage from another mod works; remove that mod → the exit shows its normal look, the log mentions the
      missing block.
- [ ] Breaking particles / sounds stay the exit's; grass-like blocks may be untinted (grey), check how it looks.
- [ ] Survival player cannot change the camouflage of a placed exit (right-click with a block does nothing special).

