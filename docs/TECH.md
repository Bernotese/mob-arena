# Mob Arena – Tech Reference (T0)

Checked on 2026-11-07. **[V]** = verified (source given). **[U]** = unverified, test it before you rely on it.
Method for API names: download the 26.3 jars and read the class files (method names + descriptors).
Sources: Mojang `server.jar` 26.3 (unobfuscated), `fabric-api-0.165.2+26.3.jar`, `fantasy-0.8.4+26.3.jar`, `sgui-2.2.1+26.3.jar`.

## 1. Versions
| Item | Version | Status / source |
|---|---|---|
| Minecraft | `26.3` (release 2026-09-15). Needs Java 25. | [V] piston-meta `26.3.json` (`javaVersion.majorVersion = 25`) |
| Fabric Loader | `0.20.1` (stable) | [V] https://meta.fabricmc.net/v2/versions/loader |
| Fabric Loom | `1.19.4` (latest release; 1.20 is alpha) | [V] https://maven.fabricmc.net/net/fabricmc/fabric-loom/maven-metadata.xml |
| Gradle | `9.9.0` | [V] https://services.gradle.org/versions/current |
| Fabric API | `0.165.2+26.3` (needs loader >= 0.19.3) | [V] https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml |
| Fantasy | `0.8.4+26.3` (`minecraft >=26.3-`, `java >=25`) | [V] https://maven.nucleoid.xyz/xyz/nucleoid/fantasy/ |
| sgui | `2.2.1+26.3` | [V] https://maven.nucleoid.xyz/eu/pb4/sgui/ |

Note: PLAN.md says Loom 1.17 / Gradle 9.6 / Loader 0.19.x. Use the newer versions above.

```groovy
repositories { maven { url = "https://maven.nucleoid.xyz/" } }
dependencies {
    minecraft "com.mojang:minecraft:26.3"
    implementation "net.fabricmc:fabric-loader:0.20.1"          // no mappings line: 26.x is unobfuscated [U: Loom DSL]
    implementation "net.fabricmc.fabric-api:fabric-api:0.165.2+26.3"
    implementation include("xyz.nucleoid:fantasy:0.8.4+26.3")
    implementation include("eu.pb4:sgui:2.2.1+26.3")
}
```
[U] Exact Loom DSL for unobfuscated versions (`implementation` vs `modImplementation`, no `mappings`). T1 must confirm with the Fabric template (https://fabricmc.net/develop/template/).

## 2. Important 26.3 name changes
- `ResourceLocation` is now `net.minecraft.resources.Identifier` (`Identifier.fromNamespaceAndPath("mobarena","x")`). [V] class file
- Game rules: package `net.minecraft.world.level.gamerules`, typed `GameRule<T>`. [V] class file
- Daylight cycle rule is now `GameRules.ADVANCE_TIME`. Weather: `GameRules.ADVANCE_WEATHER`. Time uses "world clocks" (`net.minecraft.world.clock.WorldClocks.OVERWORLD`, `ClockTimeMarkers.NOON`). [V] class file
- Fantasy renamed `World` -> `Level`: `RuntimeLevelConfig`, `RuntimeLevelHandle`, `openTemporaryLevel`. [V] class file
- GameTest: `fabric-gametest-api-v1:4.0.36` is in the Fabric API POM but **not** inside the fabric-api jar (dev/test only). [V] POM. Setup via Loom is [U].

## 3. API reference
**Game rules** [V] `GameRules.get(GameRule)`, `set(GameRule, Object, MinecraftServer)`; `ServerLevel.getGameRules()`, `MinecraftServer.getGlobalGameRules()`.
Fields: `KEEP_INVENTORY`, `ADVANCE_TIME`, `ADVANCE_WEATHER`, `MOB_GRIEFING`.
```java
level.getGameRules().set(GameRules.KEEP_INVENTORY, true, server);
```
[U] Whether `ServerLevel.getGameRules()` is per level or global. Safer: set rules in the Fantasy config (below).

**Runtime worlds (Fantasy)** [V] names; [U] runtime behavior.
```java
RuntimeLevelConfig cfg = new RuntimeLevelConfig()
    .setSeed(seed)
    .setDimensionType(BuiltinDimensionTypes.OVERWORLD)            // [V] ResourceKey
    .setGenerator(server.overworld().getChunkSource().getGenerator()) // [U] uses cfg seed?
    .setGameRule(GameRules.KEEP_INVENTORY, true)
    .setShouldTickTime(true);
RuntimeLevelHandle h = Fantasy.get(server).openTemporaryLevel(cfg);
ServerLevel level = h.asLevel();
// arena: new VoidChunkGenerator(server), setShouldTickTime(false),
//        setClockTime(WorldClocks.OVERWORLD, 6000), setGameRule(ADVANCE_WEATHER, false)
h.delete();   // removes world + files
```
Other: `setMirrorOverworldGameRules(bool)`, `setDifficulty(Difficulty)`, `setFlat(bool)`, `getOrOpenPersistentLevel(Identifier, cfg)`.
Temporary levels are deleted on server stop (`onServerStopping`). [U] Check that the noise generator really uses the new seed (biomes and terrain).

**Block loot (randomizer)** [V] `LootTableEvents.MODIFY_DROPS` (`fabric-loot-api-v3`), called from a `LootTable` mixin, so all block drops pass through it (also explosions).
```java
LootTableEvents.MODIFY_DROPS.register((Holder<LootTable> table, LootContext ctx, List<ItemStack> drops) -> {
    if (!table.unwrapKey().map(k -> k.identifier().getPath().startsWith("blocks/")).orElse(false)) return; // [V] unwrapKey, identifier()
    TeamId team = levelToTeam(ctx.getLevel());   // LootContext.getLevel() -> ServerLevel [V]
    drops.replaceAll(s -> mapStack(team, s));
});
```
Alternative filter: `ctx.hasParameter(LootContextParams.BLOCK_STATE)` [U].

**Living death / kill attribution** [V]
- `ServerLivingEntityEvents.AFTER_DEATH`: `(LivingEntity e, DamageSource src)`. Also `ALLOW_DEATH`, `ALLOW_DAMAGE`, `AFTER_DAMAGE (e, src, baseDmg, dmg, blocked)`.
- `LivingEntity.getKillCredit()`, `getLastHurtByPlayer()`, `getLastHurtByPlayerMemoryTime()`, `getLastHurtByMob()`. `DamageSource.getEntity()` = owner of a projectile [U].
- Players: `ServerPlayerEvents.ALLOW_DEATH`, `AFTER_RESPAWN`, `COPY_FROM`, `JOIN`, `LEAVE`.

**Entity load / split** [V]
- `ServerEntityEvents.ENTITY_LOAD (Entity, ServerLevel)`, `ALLOW_LOAD (Entity, ServerLevel, EntitySpawnReason, boolean)`.
- Slime/magma split: `AbstractCubeMob` uses `Mob.convertTo(..)` with `ConversionType.SPLIT_ON_DEATH`. Fabric fires `ServerLivingEntityEvents.MOB_CONVERSION (Mob from, Mob to, ConversionParams)` from a `Mob` mixin. Use it to copy tags to children.
```java
ServerLivingEntityEvents.MOB_CONVERSION.register((from, to, params) ->
    from.getTags().stream().filter(t -> t.startsWith("mobarena.wave.")).forEach(to::addTag));
```
[U] `MOB_CONVERSION` fires once per split child (expected, test it).

**Sun burn** [V] Only `Mob` calls `isSunBurnTick()` / `burnUndead()`. No Fabric event. Needs one mixin:
```java
@Mixin(Mob.class) // owner T5: no sun burn in arena levels
abstract class MobSunMixin { @Inject(method = "isSunBurnTick", at = @At("HEAD"), cancellable = true)
  void ma$noBurn(CallbackInfoReturnable<Boolean> cir) { if (ArenaLevels.isArena(((Mob)(Object)this).level())) cir.setReturnValue(false); } }
```

**Spawn egg lookup** [V] `SpawnEggItem.byId(EntityType<?>) -> Optional<Holder<Item>>` [V generic signature]; `SpawnEggItem.getType(ItemStack) -> EntityType`.

**Mobs in waves** [V] `Mob.setPersistenceRequired()`, `Mob.setTarget(LivingEntity)`, `Attributes.FOLLOW_RANGE` (a `Holder`), `Entity.addTag(String)`, `Entity.setGlowingTag(boolean)`, `LivingEntity.addEffect(MobEffectInstance)`.

**Chest menu `generic_9x3` (backpack)** [V] `MenuType.GENERIC_9x3`, `ChestMenu.threeRows(int id, Inventory inv, Container c)`, `SimpleMenuProvider(MenuConstructor, Component)`.
```java
player.openMenu(new SimpleMenuProvider((id, inv, p) -> ChestMenu.threeRows(id, inv, teamContainer),
    Component.literal("Team Backpack")));   // teamContainer = one shared SimpleContainer per team
```
Item use: `UseItemCallback.EVENT` `interact(Player, Level, InteractionHand) -> InteractionResult` [V]. Item data: `DataComponents.CUSTOM_DATA`, `CUSTOM_NAME`, `BUNDLE_CONTENTS`; `CustomData.of(CompoundTag)` [V].

**sgui (wave menu)** [V] `new SimpleGui(MenuType.GENERIC_9x6, player, false)`, `setTitle`, `setSlot(int, GuiElement)`, `open()`, `close(bool)`.
```java
gui.setSlot(i, GuiElementBuilder.from(eggStack).setName(name)
    .addLoreLine(Component.literal("Count: " + n))
    .setCallback((idx, type, input, g) -> { if (type.shift) move(10); else if (type.isRight) moveAll(); else move(1); }));
```
`ClickCallback.click(int, ClickType, ContainerInput, SlotBasedGui)`; `ClickType` has fields `isLeft`, `isRight`, `shift` [V].

**Commands** [V] `CommandRegistrationCallback.EVENT` `register(CommandDispatcher, CommandBuildContext, Commands.CommandSelection)`. Tick: `ServerTickEvents`. Lifecycle: `ServerLifecycleEvents`.

## 4. Blockers / risks
- No blocker. Fantasy and sgui both have a 26.3 build.
- Risk: per-level game rules in 26.3 (global game rules exist). Fantasy has `DelegatingGameRules` / `GameRuleStore`, so per-level rules are probably OK. T5 must test.
- Risk: GameTest setup (module not in the main jar). T1 must test.
