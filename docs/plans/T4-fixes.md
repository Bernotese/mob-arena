# T4 fixes + T2/T3/T4 integration (from claude-2 review of e90cb9d)

## Required
1. Bundle swallows the backpack (proven). In `blocksClick`, block when one stack is guarded and the other has `BUNDLE_CONTENTS`. `isGuarded` also checks inside `BUNDLE_CONTENTS` and `CONTAINER`.
2. Death drop is not guarded (proven). Skip guarded stacks in `Inventory.dropAll` and `EntityEquipment.dropAll` (offhand). The test must check the slot before `restore()`.
3. World interactions: in `UseEntityCallback` and `UseBlockCallback`, a guarded stack in the hand opens the backpack and returns SUCCESS (item frame, armor stand, decorated pot, shelf, allay).
4. Lifetime: memory only, no SavedData (SPEC 5). Clear at `onEnter(LOBBY)` and at PREP start. On JOIN, remove backpack items from players that are not in a game.
5. Wiring: `BackpackAccess` -> T2 `GameManager`/`TeamId`. `/ma backpack` through `CommandRegistry`. Randomizer: `RandomizerModule.hook().startGame(...)` at PREP start, `clear()` at LOBBY.

## Optional (do them)
- `restore()` with a full inventory: choose a slot that is not guarded.
- `restore()` cost: check `CUSTOM_DATA` without a copy, or run every 20 ticks.
- `closeContainer()` in AFTER_DEATH.
- Use `TeamId` in place of String team keys. Replace the deprecated `makeMockServerPlayerInLevel`.
