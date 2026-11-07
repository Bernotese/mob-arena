# T3 fixes (from claude-1 review of ce5590b)

## Required
- R1: `MobEquipmentMixin` maps items that mobs picked up. Exploit: drop X, a zombie picks it up, kill it -> map(X); repeat to get any item. Fix: remove the `dropPreservedEquipment` and `equipItemIfPossible` targets. In `dropCustomDeathLoot`, map only slots where `!dropChances.isPreserved(slot)` (WrapOperation + `@Local EquipmentSlot`). GameTest: a zombie picks up a player-dropped item, dies -> item unchanged.
- R2: remove the `shearItem` target. It maps saddles, horse/wolf armor, carpets and harnesses that players put on mobs. Wool shearing already uses loot tables.
- R3: exclude `Items.COMMAND_BLOCK_MINECART` (op-only). Assert it in `registryPolicyAndStackCap`.

## Optional (do them)
- O1: clear the global hook after the T3 tests (or a hook per test), so other modules' GameTests get normal drops.
- O3: GameTests for shearing and fishing.
- O4: each mixin target comment says why it is needed.
- O2 (TeamId in place of String) comes with the integration task.
