# Status (2026-11-08)

## Version 1 plus real-client fixes (T12) on main
All tasks T0–T12 are merged. `main` build: 108 unit tests, 144 GameTests. CI on GitHub runs the same build.

T12 (from the real-client test, docs/findings/2026-11-08-real-client.md, plan docs/plans/T12.md):
- Bug 1: a player on the death screen is respawned at each phase change, at game end and at join. No false ALL_DOWN.
- Bug 2: a PREP death always respawns in the team overworld (user decision).
- Death messages go to all online players and the server log (user decision); /ma debug state shows elimination reasons.
- Go title at wave spawn, backpack Q resync, selected wave tab marked.
- Result title 2 s after the team title, lobby spread, debug wave time min 1 s.
- "standing on air" fix after teleports; missing lobby platform is rebuilt.
- Sounds (vanilla, Master source): countdown, Go, wave clear, finish, elimination, sudden death, win/lose/draw.

## Open
- User: second real-client test (VM). Check: death screen across phase change and rejoin while dead, Go title, backpack Q, wave tab mark, sounds (loudness), "standing on air" log, lobby spread.
- Bug 2 cause of the End platform respawn is not proven (old main already sent normal End deaths to the overworld). Watch for it in the rerun.
- Not tested yet: 2 viewers in the wave menu at the same time, backpack into a chest, S15 load, server stop during ARENA.
- Possible later: T11 features (mob variants, stats screen).

## Rules and defaults
- Ender Dragon in each team End spawns but does not count (default).
- maxAliveMobs 150 with a queue; eliminated players spectate the other arena.
- Player-built golems and withers are allowed and are not wave mobs. Golems summoned by a wave villager are wave mobs.
- Waves have no random jockeys.
- A charged respawn anchor in the team Nether loses its charge on a PREP death, but the player respawns in the team overworld.
- Lobby setSpawn: stand on the block center (the floor check reads the block under the center).

## Notes
- Run Gradle with tools/gradle.sh (Java 25, max 3 builds at a time).
- Delete build/run/gameTest/world before a GameTest build if old drops can affect tests.
