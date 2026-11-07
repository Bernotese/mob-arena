# Mob Arena – Implementation Plan

Read `AGENTS.md`, `docs/SPEC.md` and `docs/TECH.md` (versions, 26.3 API names) first. This file tells how we build the mod and who does which task.

## 1. Tech stack
| Item | Choice | Note |
|---|---|---|
| Minecraft | Java Edition 26.3 | Code is not obfuscated since 26.1. Use Mojang names. |
| Java | 25 (Eclipse Temurin) | Not installed yet. Install it (see task T1). |
| Loader | Fabric Loader 0.20.1 | Stable for 26.3. NeoForge 26.3 is still beta. |
| Build | Fabric Loom 1.19.4, Gradle 9.9.0 (wrapper) | Do not use Loom 1.20 (alpha). |
| API | Fabric API 0.165.2+26.3 | Events, commands, loot, GameTest. |
| Runtime worlds | `xyz.nucleoid:fantasy` 0.8.4+26.3 | Makes and deletes worlds at runtime. |
| Chest menus | `eu.pb4:sgui` 2.2.1+26.3 | Server-side GUIs. |
| Tests | JUnit 5 + Fabric GameTest | See section 5. |

- Mod id: `mobarena`. Base package: `dev.mobarena`.
- `fabric.mod.json`: `"environment": "*"`, only a `main` entrypoint. No client code, no client mixins. Vanilla clients must be able to join.
- If a library has no 26.3 build: tell `orchestrator`. Do not copy library code into the project without approval.

## 2. Architecture
One package per module. One module has one owner task at a time. This prevents merge conflicts.

```
dev.mobarena
├── MobArena.java              entrypoint, wires all modules (owner: T1, later only additions)
├── core/                      T2: GameManager (state machine), Phase enum, GameClock, GameContext
├── config/                    T2: MobArenaConfig (JSON, Gson), config commands, config GUI (T9)
├── team/                      T2: TeamId (RED, BLUE), TeamManager, vanilla scoreboard teams
├── command/                   T2: /ma root + sub-commands. Modules register sub-commands via CommandRegistry
├── world/                     T5: PrepWorldManager, ArenaWorldManager, ArenaTemplate, DefaultArenaBuilder
├── randomizer/                T3: ItemRandomizer (pure logic), RandomizerLootHook
├── backpack/                  T4: TeamBackpack, BackpackItem, BackpackGuard
├── collect/                   T6: MobPool, KillAttribution, CollectionListener
├── wave/                      T6: WaveSetup (3 waves per team), WaveConfigGui
├── arena/                     T7: ArenaBattle, WaveSpawner, WaveMobAi, ArenaBounds, RespawnManager
├── result/                    T8: ProgressTracker, WinResolver (pure logic), SuddenDeath, EndSequence
├── hud/                       T9: BossBarHud, ActionBar, Titles
└── mixin/                     Only if no Fabric event exists. Each mixin file names its owner task in a comment.
```

### 2.1 Core contracts (made in T2, then frozen)
Other modules use only these types from `core`. A change to them needs a review by a Claude agent and an OK from `orchestrator`.
- `enum Phase { LOBBY, PREP, WAVE_CONFIG, ARENA, SUDDEN_DEATH, END }`
- `interface PhaseListener { void onEnter(Phase p, GameContext ctx); void onExit(Phase p, GameContext ctx); void onTick(Phase p, GameContext ctx); }`
- `GameManager.register(PhaseListener)`, `GameManager.endPhaseNow()`, `GameManager.get()`.
- `GameContext`: game seed, `TeamId` per player, `ServerLevel` per team (prep and arena), phase timer, config snapshot.
- `GameContext` has a typed attachment map: `ctx.get(Key<T>)` / `ctx.put(Key<T>, T)`. Modules keep their game state there (for example `MobPool`). All state is gone at game end.
- `ArenaBattle` (T7) calls `ResultHooks.teamFinished(team, timeTicks)` and `ResultHooks.teamEliminated(team, progress)`. `result` (T8) implements these hooks. T2 makes the empty interface.

### 2.2 Rules for all code
- Keep logic that does not need Minecraft in plain Java classes (randomizer map, wave split, win rules, team shuffle). Unit-test these classes.
- Use Fabric API events first. Use a mixin only when no event exists. Explain each mixin in one comment line.
- All timers count server ticks. Do not use wall-clock time for game logic.
- No client-side code. All text uses `Component` with plain colors (no resource pack).
- Log with SLF4J, logger name `mobarena`.

## 3. Tasks
Status is tracked by `orchestrator`. Each task: owner type, dependencies, done criteria.

### Phase 0 – Setup
**T0 – Library research** (Claude). No dependencies.
- Find the latest versions for 26.3: Fabric Loader, Loom, Fabric API, Fantasy, sgui. Check that each has a 26.3 build.
- Find the 26.3 API for: game rules (keep inventory, daylight cycle, mob griefing), block loot modify events, living entity death events, entity load/split events, sun burn check, spawn egg item lookup, `generic_9x3` menus.
- Write the result to `docs/TECH.md` (versions, links, API names, code snippets). Max 2 pages.

**T1 – Project scaffold** (Codex). No dependencies (T0 can run at the same time).
- Install Temurin JDK 25 (user-local install is OK). Report the version.
- Use the versions in `docs/TECH.md`. T0 says: `fabric-gametest-api-v1` is not in the fabric-api jar, and the Loom DSL for unobfuscated MC is not verified. Test both.
- Make a Fabric mod project: Gradle wrapper, Loom, `fabric.mod.json`, `MobArena` entrypoint, the package tree from section 2 (empty `package-info.java` files), JUnit 5, Fabric GameTest setup.
- Add `./gradlew runServer` config with `eula=true` and `online-mode=false` in `run/` (local only, in `.gitignore`).
- Done: `./gradlew build` passes, `./gradlew runServer` starts and logs `Mob Arena loaded`, one example JUnit test and one example GameTest pass.

### Phase 1 – Foundations (start after T1 is merged)
**T2 – Core, config, teams, commands** (Codex). Depends on T1.
- Section 2.1 contracts. Phase state machine with tick timer and `/ma start|stop|skip|status`.
- `MobArenaConfig` with all keys from SPEC section 11, JSON load/save, `/ma config get|set|reset`.
- `TeamManager`: assign, remove, random shuffle (pure logic, unit test), vanilla scoreboard teams with colors, friendly fire off.
- Lobby: `/ma setlobby`, lobby spawn, adventure mode, no damage in `LOBBY`.
- Done: unit tests for state machine (fake tick source), config round trip, shuffle. GameTest: `/ma start` with 2 fake players goes to `PREP`.

**T3 – Item randomizer** (Codex). Depends on T1. Can run with T2.
- `ItemRandomizer`: builds `Map<Item,Item>` from a seed and an item list. Pure logic for the shuffle.
- Hook: all loot in a world that belongs to a team uses the team map (SPEC section 4): all loot tables (`LootTableEvents.MODIFY_DROPS`) and mob equipment drops (mixin if no event).
- Do not touch player drops and villager/wandering trader trades. Add a test for each exception.
- Until T5 exists: use a test hook that maps a given `ServerLevel` to a team.
- Done: unit tests (same seed -> same map, different team -> different map, blacklist excluded, stack cap). GameTests: block drop, mob drop and chest loot are mapped; player drop is not mapped.

**T4 – Team backpack** (Codex). Depends on T1. Can run with T2.
- SPEC section 5. Shared `SimpleContainer` per team, live sync to all viewers, guards against drop/move/storage.
- Done: GameTest: 2 players of one team see the same items. Drop of the backpack is blocked. Item survives death.

### Phase 2 – Game content (start after T2 is merged)
**T5 – Worlds** (Claude plans, Codex implements). Depends on T2.
- Prep worlds with Fantasy: per team one overworld and one Nether, same seed for both teams, keep inventory on. Delete at game end.
- Nether portals link only inside the team (overworld <-> team Nether). End portals do nothing.
- Risk from T0: game rules can be global only in 26.3. Test per-level rules first.
- Arena worlds: void world per team, paste the template, fixed time, no weather, no sun burn for undead.
- `DefaultArenaBuilder`: round arena, radius ~30, stone floor, some cover, 1 water basin, barrier wall and roof, spawn ring markers.
- Done: GameTest or `runServer` test: `/ma start` makes 4 prep worlds (2 overworlds, 2 Nethers) with the same seed. A portal in team RED goes to the RED Nether. Arena paste works. `/ma stop` deletes all game worlds.

**T6 – Mob collection + wave config** (Claude plans, Codex implements). Depends on T2. Can run with T5.
- `KillAttribution` (SPEC 3.1), `MobPool` per team, action bar message.
- `WaveSetup` (pure logic: move N, move all, leftover to wave 3; unit tests). `WaveConfigGui` with sgui, live update, `/ma waves`, `/ma ready`.
- Done: unit tests for `WaveSetup`. GameTest: a team player kills a zombie, pool has 1 zombie.

**T7 – Arena battle** (Claude plans, Codex implements). Depends on T5, T6.
- Countdown, wave spawn on the spawn ring, AI targets, neutral mobs angry, tags for children, bounds, glow, cooldown, max time, `RespawnManager` (SPEC 7.5).
- Done: GameTest: a wave of 3 zombies spawns, kill all, next wave starts after cooldown. All players dead -> `teamEliminated` is called.

**T8 – Results, sudden death, end** (Codex). Depends on T2. Can run with T5–T7 (uses `ResultHooks`).
- `WinResolver` pure logic for all cases in SPEC section 8 (unit test each case). `SuddenDeath` uses T7 spawner. `EndSequence`.
- Done: unit tests for all win cases. GameTest with fake hooks.

**T9 – HUD and config GUI** (Codex). Depends on T2.
- Boss bar with phase and time, titles, action bar helpers. `/ma config` chest menu (toggle bools, +/- for numbers).
- Done: `runServer` check with screenshots not possible: test by GameTest where possible, else describe the manual check.

### Phase 3 – Integration
**T10 – Full game test** (Claude). Depends on all.
- Play a full game on `runServer` with short config times (prep 60 s, config 30 s). Use 2 offline-mode clients if possible, or GameTest fake players.
- Write a bug list. `orchestrator` sends fixes as small tasks.

**T11 – Later features** (not in version 1): End per team, mob variants, stats screen.

## 4. Order and parallel work
```
T0 (claude-1) ─┐
T1 (codex-1) ──┴─> T2 (codex-1) ──┬─> T5 (plan claude-2 -> codex-2) ─┐
               ├─> T3 (codex-2)   ├─> T6 (plan claude-3 -> codex-3) ─┴─> T7 (plan claude-1 -> codex) ─> T10
               └─> T4 (codex-3)   ├─> T8 (codex)
                                  └─> T9 (codex)
```
- Claude agents plan T5, T6 and T7 while T2–T4 are in progress. A plan is a file `docs/plans/T<n>.md`: classes, events/mixins, test list, open questions.
- Each Codex result gets a review by a free Claude agent. The reviewer checks: SPEC rules, tests, no client code, no edits outside the module.

## 5. Tests
- **Unit tests** (JUnit 5, `src/test/java`): all pure logic classes. Run with `./gradlew test`.
- **GameTests** (Fabric GameTest API, `src/gametest/java` or the Loom default): behavior in a real server world. Run with `./gradlew runGameTest`.
- **Smoke test**: `./gradlew runServer`, run commands in the server console, read the log. Use short config times.
- Each task report lists: tests added, commands run, and the result.

## 6. Task message template (orchestrator -> agent)
```
Task T<n>: <title>
Read: AGENTS.md, docs/SPEC.md (sections ...), docs/PLAN.md (task T<n>), docs/plans/T<n>.md (if it exists).
Worktree: git worktree add ../mob-arena-wt/<agent> -b <agent>/t<n>-<slug>   (from main)
Scope: only package dev.mobarena.<module> (+ listed shared files).
Done when: <done criteria from PLAN>.
Report to orchestrator: branch name, summary, tests run and results, new tools/libraries with versions, open questions.
```
- Report with: `herdr agent prompt orchestrator "<report>"` (or the messaging tool you have). Keep the report short.
