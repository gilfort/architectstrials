# Instances & Lifecycle

Every opened scroll creates an **instance**: one placed copy of a structure, drawn from the pool of the scroll's
theme and tier, that a player or group plays through. Instances never share space — parallel groups never see
each other.

## Slots

Every instance gets its own **slot** in its theme dimension. Slots lie on a square spiral around the origin,
`slotSpacing` blocks apart (default 2048). A slot owns a 160 × 160 area: the maximum structure footprint of
128 × 128 plus a margin.

When an instance ends, its slot is **cleared** over several ticks — blocks, block entities and entities
(including those in chunks that were no longer loaded), without drops — before it is reused. Clearing that was
interrupted by a server stop resumes on the next start.

`maxConcurrentInstances` limits the number of simultaneous instances per theme dimension (default 4, `0` =
unlimited); at the limit, scrolls fail without being consumed.

## Placement

Opening an instance never freezes the server: its structure is **placed over several ticks** with a fixed budget
per tick (about 16,000 blocks), bottom to top, after its chunks have been loaded in the background. Markers are
resolved last. Until then the entry portal stays in its forming state (rune particles) — nobody can enter a
half-built instance. A 128 × 128 structure with 60 layers takes a few seconds; small rooms are ready within the
portal's normal forming time. While a structure is being placed, slot clearing pauses, so both never add up.

If the server stops during placement, the unfinished instance is discarded on the next start and its slot is
cleared; its portal disappears.

Structures whose template has no Player Spawn Marker are detected when they are drawn: they are skipped with a
warning in the log and removed from the pool until the next `/reload`.

## Entering and leaving

- Players arrive at a random [Player Spawn Marker](Markers#player-spawn-marker), facing its direction, in
  **Adventure mode** (or Survival, if the structure is a [mining room](Building-Challenges#mining-rooms-survival-challenges)).
- Their **entry point** (dimension, position, rotation, game mode) is stored. Every way out returns them there —
  exit, time limit, Dimension Ward, `/at exit` — with their previous game mode, turned around (they step back out
  of the portal they walked into) and with a short rune echo of the portal behind them.
- If the entry position is obstructed, the nearest safe position is used; if the entry dimension no longer
  exists, the player goes to the world spawn.
- Players who log in inside a challenge whose instance is over are returned automatically.

## Time limit

Every challenge has a time limit, shown to all participants as a boss bar ("Time left: mm:ss", red in the last
minute):

- the scroll's `architectstrials:time_limit` (minutes), otherwise `defaultTimeLimitMinutes` (default 60),
- never shorter than the portal's open time.

Participants get chat warnings at 5 and 1 minute(s) and an action-bar countdown in the last 10 seconds. When time
is up, everyone still inside is sent back — this does **not** count as a completed run.

Time only runs while the server runs (it is counted in server ticks and saved with the world). Players who log
out inside a challenge keep it alive until the time limit; if they log back in in time, they continue, otherwise
they are returned on login.

## End of an instance

An instance is removed and its slot cleared once its portal is closed **and** nobody (online or offline)
belongs to it anymore — e.g. after the last player finished, was saved by the Dimension Ward or ran out of time.
Operators can end one manually with `/at instance close <id>` (participants are returned).

## Death protection (Dimension Ward)

Nobody ever truly dies inside a challenge. When a player with a stored entry point would die in a challenge
dimension — from any cause, including the void and `/kill` — they are returned to their entry point with their
full inventory, full health and hunger, **all effects removed** and a short damage immunity. The vanilla totem
animation plays and a message names the cause. Real totems (and other mods' death protection) always take
precedence.

The protection is a state, not a status effect: it cannot be removed by milk or commands and has no icon.
Communicate it to players via your modpack's questbook. Being saved never counts as a completed run; with
`allow_reentry` the player may go back in while the portal is open.

## Completing a run

Walking through an open [exit](Markers#exit-marker) completes the run: the player is returned home, receives the
[completion bonus](Loot#completion-bonus), the advancement trigger fires (see
[Advancements & Events](Advancements-and-Events)) and the player can never re-enter this instance.
