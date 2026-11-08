# Second real-client test → fix tasks + new feature (from the user, 2026-11-08)

Source: second real-client run on main e4c953e (3 real 26.3 clients, VM `mobtest`). Full report and screenshots in `rerun/` next to this file. All T12 fixes passed in the real client (death screen across phases, PREP respawn, death messages to all, Go title, backpack Q, result delay, lobby spread, sounds measured OK, 2 viewers in wave menu, server stop in ARENA).

Note on old bug 2: the End-platform respawn in run 1 was very likely my test setup (I placed the End portal block on the PREP spawn). No action needed beyond the T12a fix.

## New feature (user request, binding)
**Short preparation phase in the arena.** When the arena starts, the teams spawn in their arena and get **30 seconds** before the first wave comes. Then the normal countdown and wave 1 start.
- Suggestion (orchestrator decides details): config key, e.g. `arenaPrepSeconds` default 30; boss bar / title shows the time left ("Get ready – 0:25"); sudden death keeps its current start unless you see a reason.
- This also fixes the improvement below (first "3" of the countdown is lost while the client loads the arena).
- Update SPEC.md, MANUAL-TEST.md and GameTests.

## Bugs
1. **[High] Player offline across phase changes is stuck in the deleted arena after the game.**
   Repro (2×): a team player disconnects in PREP (alive or on the death screen), phases advance to ARENA, the player rejoins in ARENA.
   Server log at rejoin (or at sudden death start): `Force-added player with duplicate UUID …`, `UUID of added entity already exists: ServerPlayer['Tester3'/…, l='ServerLevel[world]', x=0.50, y=64.00, z=0.50, removed=DISCARDED]`, `Fetching packet for removed entity`.
   During the game it looks fine. After the game all others are in the lobby; this player stays in `red_arena_…` (already deleted), adventure mode, no boss bar; the client still shows the arena and the backpack while the server inventory is empty (`rerun/7-NEW-BUG-rejoin-stuck-in-deleted-arena.png`). Only a reconnect fixes it.
   Suspected: new join path from T12b ("Reset dead joiners before player registration") or the rejoin placement into the arena, which adds a second player entity. Add a GameTest (disconnect in PREP, rejoin in ARENA, game end → player in lobby, one entity).
2. **[Low] Selected wave tab only marked in the tooltip** ("▶ Wave 2"); the icon looks the same. Mark the icon too (glint or other item).
3. **[Low] "standing on air" still appears** (31 times in ~15 min, was 83), mostly in ARENA about every 10 s for players standing still. Check whether the arena floor/support is really sent.

## Improvements
- First "3" of the wave 1 countdown is lost while the client loads the arena → solved by the new arena prep phase.
- Optional for tests: debug switch to keep potion effects at arena / sudden death start.

## Process
Normal team flow (AGENTS.md). Use all free agents in parallel where tasks are independent. Update docs/STATUS.md and tell the user when merged; the VM can rerun the real-client test then.
