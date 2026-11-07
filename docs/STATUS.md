# Status (2026-11-07, paused for a machine restart)

## On main (merged, build green: 63 GameTests)
T0–T6b (incl. T6b fix), T8b part 1 (results, end sequence), T9 HUD, T10b smoke tools, T5 W1/W9 stability + spawn fallback, CI workflow.
Plans: T8b part 2 (docs/plans/T8.md section 7 + decisions), T7 decisions after the review.

## Open branches (pushed to origin)
| Branch | Agent | State | Next step |
|---|---|---|---|
| claude-3/t7b-battle | claude-3 | T7b review done (claude-2). Fix committed f980f0e; 1 test left: the infight test sees the forced golem target before the end-of-tick reset (wait 1 tick or reset in setTarget). Fixes: slime split on discard, no infighting between wave mobs, block player-built golems/withers, no placeAtSpawn at shutdown. | Finish fix, build, merge first. |
| codex-1/t10a-debug | codex-1 | Reviewed (claude-1). Base: T7b. | Merged together with t10c. |
| codex-1/t10c-fullgame | codex-1 | Test commit b684e8e reviewed OK (claude-2). WIP commit e4cdadb has the T10a R1/O fixes. | Rebuild; claude-1 reviews the fix commit; merge after T7b. |
| codex-2/t5-shutdown | codex-2 | WIP 969a106. Cause: Fantasy delete unregisters a level without close(); its chunk IO worker reschedules into the stopped IO pool at server stop. | Case 5, fix (close removed levels), tools fixes, case 3 three times. |
| codex-3/t5-w1-flaky | codex-3 | Merged. | One more runGameTest repeat. |

## Merge order
1. claude-3/t7b-battle (after its fix + green build)
2. codex-1/t10c-fullgame (contains t10a; merge main first, fill outcomes/verdict in state from ResultTracker)
3. T8b part 2: sudden death (plan in T8.md section 7; needs 1 and T8b part 1)
4. codex-2/t5-shutdown
5. T10 full-game GameTests for S1–S4, S6 (need results), smoke run, then the user test with a real client.

## Open user questions
- Ender Dragon in each team End spawns but does not count (default).
- Defaults: maxAliveMobs 150 with a queue; eliminated players spectate the other arena.
- New rule: player-built golems and withers are blocked in arenas.

## Notes
- build/run/gameTest/world is kept between runs; old item drops there can break drop checks. Delete it before a build (or filter drops to test items).
- Only one Gradle build at a time: `tools/gradle.sh (3 build slots, AGENTS.md).
- Machine after restart: 8 cores, 30 GB RAM.
