# Status (2026-11-07, 16:00 UTC)

## Version 1: code complete on main
All tasks T0–T10 are merged. `main` build: 105 unit tests, 119 GameTests (about 1.5 min). CI on GitHub runs the same build.
Smoke runs on runServer with Carpet (tools/smoke.py s1, s15, s16): PASS.

Merged after the first pause:
- T7b arena battle (slime split, no infighting, no player-built golems/withers), TestEnd test cleanup.
- T8b part 1 and 2: results, end sequence, sudden death.
- T10a debug driver, T10c/T10d full-game GameTests (S1–S7, S9, S10, S12–S14), T10b/T10e smoke tools.
- Fixes found by the tests: shutdown race (close removed levels), fast close (no 30 s freeze at game end), async game start, structure cache crash and same-seed structure lock, teleport support block, no random jockeys in waves.

## Open
- claude-2: chicken jockey flag follow-up (small).
- User: manual test with a real client, see docs/MANUAL-TEST.md.
- Possible later: sounds (the mod plays none now), T11 features (mob variants, stats screen).

## Open user questions
- Ender Dragon in each team End spawns but does not count (default).
- Defaults: maxAliveMobs 150 with a queue; eliminated players spectate the other arena.
- Rules set by the orchestrator: player-built golems and withers are blocked in arenas; waves have no random jockeys.

## Notes
- Run Gradle with tools/gradle.sh (Java 25, max 3 builds at a time).
- Delete build/run/gameTest/world before a GameTest build if old drops can affect tests.
