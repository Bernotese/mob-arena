# Real-client test findings → fix tasks (from the user, 2026-11-08)

Source: manual test with 3 real vanilla 26.3 clients on a Fabric server (jar = main a8bebc5) in LXD VM `mobtest`. Screenshots: `screenshots/` next to this file. Most of the MANUAL-TEST.md checklist passed; the items below are what failed or should improve.

## User decisions (binding)
- **Death messages are always shown to everyone** (all online players, also in the arena and the server log). Today arena deaths go only to team mates.
- **A player who dies in PREP always respawns in the team overworld**, never in the team Nether or End.

## Bugs
1. **[High] Player on the death screen across a phase change is never moved.**
   Repro: die in PREP (e.g. fall into the void in the team End), do not click Respawn until the phase changes to ARENA.
   Result: team counts as ALL_DOWN at arena tick 1 → eliminated (`BLUE arena ELIMINATED ticks 1`), again in sudden death. After the game the player stays in the deleted End level, in the void, adventure mode, until reconnect (screenshot 5).
   Cause: phase teleports skip dead players; `ArenaBattles.alive()` counts them as down. Fix idea: on each phase change and at game end, force-respawn dead team players (`PlayerList.respawn`) or queue the teleport for their respawn; consider auto/immediate respawn in game levels. Add a GameTest.
2. **[Medium] PREP death respawns at the team End/Nether.**
   Repro: as RED visit the team End (or Nether), die in PREP → respawn at the End platform (100.5, 49, 0.5) in `red_end`, or in the End after a Nether death.
   Cause: `RespawnRouter.accepts()` uses `GameLevels.isPrepLevel()`, true for the team Nether and End, so the vanilla result is accepted. Fix: accept only the team overworld (user decision above). Add a GameTest.
3. **[Low] "Go" title never shown.** `BattleFlow.tick` emits `Countdown` only while `stateTicks < countdownTicks`, so `secondsLeft` is never 0 and the "Go" branch in `ArenaBattle.handle` is dead; then `spawnWave` shows "Wave N / x mobs". Seen: 3, 2, 1, "Wave 1" (screenshot 4). Show "Go" (e.g. as title with the wave name as subtitle) when the wave spawns.
4. **[Low] Q on the backpack makes it vanish on the client.** Server cancels the drop (bundle still in slot 0), client hotbar shows an empty slot until the next inventory sync (screenshot 6). Resend the slot / full inventory after cancelling.
5. **[Low/UX] Wave menu does not show the selected wave tab.** All tab items look the same; only the tooltip tells. Mark the selected tab (glint, other item, or "▶ Wave 2" name).

## Improvements
- **Death messages to everyone** (user decision above): send the arena death message to all players and log it on the server. Also add the elimination `reason` to sudden-death outcomes in `/ma debug state`.
- **Sounds** (user request, was the unsent draft in your input box): add sounds for countdown, wave start and win.
- **Result title hides the team title:** "All waves cleared" / the last "Eliminated" title is replaced in the same tick by the game result title. Delay the result title ~2 s or merge them.
- **Lobby teleport puts all players on the same block** (800.5, 83, 256.5). Spread them a little.
- **"Player X standing on air – force-sending blocks below"** appears often after arena teleports (support block arrives late). Check the teleport/chunk order.
- **Config minimum for tests:** `waveMaxTimeSeconds` min is 60 s; allow lower values when `-Dmobarena.debug=true`.

## Not tested yet (for a later real-client run)
Sound, 2 team viewers in the wave menu at the same time, backpack into a chest, S15 load, server stop during ARENA.

## Process
Use the normal team flow (AGENTS.md). Tell the user when the fixes are merged; the VM can rerun the real-client test then.
