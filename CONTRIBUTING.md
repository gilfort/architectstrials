# Contributing to Architect's Trials

These rules apply to everyone working on the repository — people, local AI sessions and **cloud / remote AI
sessions** alike. Cloud sessions start from a fresh clone and only see committed files, so this file is the
binding reference for them.

## Target

- Minecraft **26.3**, NeoForge 26.3.x (`gradle.properties` → `neo_version`), Java **25** (auto-provisioned toolchain)
- Mod id `architectstrials`, base package `com.gilfort.architectstrials`, Mojang official mappings
  (e.g. `Identifier`, not `ResourceLocation`)
- New registries get their own `Mod*` class in `registry/`, wired in the `ArchitectsTrials` constructor.

## Code conventions

1. **English only:** all identifiers (classes, methods, variables, registry names, config keys) and all comments.
2. **Javadoc** on every public / protected class, method and field. Short `//` line comments inside method bodies
   (explaining a non-obvious step) are fine.
3. **Every player-facing text goes into the lang file** `src/main/resources/assets/architectstrials/lang/en_us.json`
   — never hardcode display strings, use `Component.translatable(...)`. Key scheme:
   `item.architectstrials.<name>`, `block.architectstrials.<name>`, `itemGroup.architectstrials.<name>`,
   `message.architectstrials.<name>`, `tooltip.architectstrials.<name>`, `architectstrials.configuration.<key>`.
   New registered content without a lang entry is incomplete.
4. Prefer vanilla / own-code solutions over mixins; write a mixin only when nothing else works.
5. Content (themes, structures, tiers) is **datapack only**; the TOML config holds global options only.

## Workflow

- Work is sliced into GitHub issues labeled `user story` (`US-XX`). **One story at a time.**
- **One branch and one PR per story:** `feature/us-XX-<short-name>`, created from up-to-date `main`.
  - PR body contains `Implements #N`, the story's final commit contains `Closes #N`.
  - Fix-up commits reference the story, e.g. `fix: … (US-XX)`.
  - Merge **only after the maintainer's OK**.
- Never bundle several stories in one branch or PR.
- No `wip:` commits on branches that get merged — squash them or commit only working states.
- Commit messages follow Conventional Commits (`feat:`, `fix:`, `docs:`, `test:`, `ci:` …).

### Cloud / remote sessions

Cloud sessions (claude.ai/code, auto-created `ccr-*` branches) follow the **same workflow, no exceptions**:

- If the session starts on a `ccr-*` branch, create `feature/us-XX-…` from `main` for each story instead of
  committing everything to the session branch.
- Finish one story completely (branch → PR → OK → merge) before starting the next from the updated `main`.
- **Do not discover APIs by pushing temporary CI debug steps** (`ci: temporarily print …`). Look up signatures
  locally: `javap` on the jars under `build/moddev/artifacts` / the Gradle cache, or extract the sources jar. If
  CI really is the only way, do it on a throwaway branch — never on the story branch, never merged.

## Documentation

- **The wiki is mandatory:** `docs/wiki/*.md` (English) is the single source of truth, mirrored to the GitHub wiki
  by `.github/workflows/wiki.yml` on push to `main`. Every story PR updates the affected wiki pages (and
  `_Sidebar.md` for new pages). Never edit the GitHub wiki directly. Docs write commands as `/at …`.
- The README stays short (features, install, development, links) and never duplicates wiki content.
- Every story adds its manual in-game checks to `docs/TESTING.md`:
  `## US-XX (#N): <title>`, the covering GameTests first, then a checkbox list.

## Tests

- GameTests live in the `src/gametest` source set (never part of the jar). Test functions are registered via
  `RegisterEvent` on `Registries.TEST_FUNCTION`; instances are JSON files in
  `src/gametest/resources/data/architectstrials/test_instance/`.
- Run them with `./gradlew runGameTestServer`; CI runs them on every push and PR. A story PR needs a green build.
- Gotchas:
  - The GameTest server never loads datapack dimensions — the test datapack declares `minecraft:the_nether` as a
    theme; `architectstrials:gametest_theme` only exists in `runClient`.
  - Test terrain is unknown: clear / prepare blocks before placing mock players.
  - Mock players always report Creative, the test server disables mob spawning, and mock players cannot receive
    custom payloads.
  - Static `ItemStack`s / components in test classes crash mod loading ("Components not bound yet") — create them
    lazily.
  - Parallel GameTests placing into the same dimension area need distinct centers.

## 26.3 API notes

- Entity type constants in `EntityTypes`, block entity types in `BlockEntityTypes`; `ResourceKey.identifier()`.
- Permissions: `Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)`.
- `ContainerInput` replaces `ClickType`; screens use `extractBackground(GuiGraphicsExtractor, …)`.
- Structure NBT block states use `id` / `properties` (not `Name` / `Properties`).
- Loot table entry functions: `"modifier": {"type": "minecraft:set_count", …}` — the old `"functions": [...]` is
  silently ignored.
- Dimension type visuals / gameplay are set via `attributes` (environment attributes).

## Commands

```bash
./gradlew build              # jar -> build/libs/
./gradlew runClient          # dev client (includes JEI)
./gradlew runServer
./gradlew runGameTestServer  # all GameTests
```
