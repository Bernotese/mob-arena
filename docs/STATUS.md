# Status (2026-11-08, after T13)

## Version 1 plus real-client fixes (T12, T13) on main
All tasks T0–T13 are merged. `main` build: 112 unit tests, 155 GameTests. CI on GitHub runs the same build.

T13 (second real-client test, docs/findings/2026-11-08-rerun-tasks.md, plan docs/plans/T13.md):
- New arena prep phase: teams get arenaPrepSeconds (default 30) in their arena before the wave 1 countdown. No elimination in prep.
- Bug fix (High): a player who rejoins in ARENA is no longer registered in two levels (no stuck player after the game).
- Selected wave tab icon is an emerald block with glint.
- "standing on air": not reproducible on the server; with -Dmobarena.debug=true the server logs MOBARENA_STANDING_ON_AIR lines.
- Debug switch -Dmobarena.debug.keepEffects=true keeps effects at sudden death start.

T12 (from the real-client test, docs/findings/2026-11-08-real-client.md, plan docs/plans/T12.md):
- Bug 1: a player on the death screen is respawned at each phase change, at game end and at join. No false ALL_DOWN.
- Bug 2: a PREP death always respawns in the team overworld (user decision).
- Death messages go to all online players and the server log (user decision); /ma debug state shows elimination reasons.
- Go title at wave spawn, backpack Q resync, selected wave tab marked.
- Result title 2 s after the team title, lobby spread, debug wave time min 1 s.
- "standing on air" fix after teleports; missing lobby platform is rebuilt.
- Sounds (vanilla, Master source): countdown, Go, wave clear, finish, elimination, sudden death, win/lose/draw.

## Open
- User: third real-client test (VM). Check: rejoin in ARENA after a PREP disconnect (one entity, lobby after the game), arena prep phase (30 s, "Get ready", first "3" heard), wave tab icon. Run with -Dmobarena.debug=true and send the MOBARENA_STANDING_ON_AIR lines.
- Not tested yet: backpack into a chest, S15 load.
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
