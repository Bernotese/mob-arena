package dev.mobarena.arena;

import dev.mobarena.team.TeamId;
import dev.mobarena.wave.WaveKeys;
import dev.mobarena.collect.CollectKeys;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import dev.mobarena.world.ArenaLayout;
import java.util.*;

public class ArenaGameTest {
    private static final String ZOMBIE = "minecraft:zombie";
    private static final int LONG = 20 * 600;

    static void kill(LivingEntity mob, ServerPlayer by) {
        mob.hurtServer((net.minecraft.server.level.ServerLevel) mob.level(), mob.damageSources().playerAttack(by), Float.MAX_VALUE);
    }
    /** Mock players never send "loaded" and never finish a dimension change, so vanilla makes them invulnerable. */
    static void damageable(ServerPlayer player) {
        player.hasChangedDimension();
        player.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
    }
    static void killPlayer(ServerPlayer player) {
        damageable(player);
        player.hurtServer(player.level(), player.damageSources().genericKill(), Float.MAX_VALUE);
    }
    static List<? extends Mob> mobs(Arena arena, String tag) {
        if (arena == null) return List.of();
        return arena.level().getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(Mob.class),
                e -> e.isAlive() && e.entityTags().contains(tag));
    }

    /** PLAN T7 done criterion 1, through the real WAVE_CONFIG -> ARENA path (enemy plan, config timers). */
    @GameTest(environment = "mobarena:arena_flow", maxTicks = 2400, skyAccess = true)
    public void planFromEnemyWavesSpawnsClearsAndCoolsDown(GameTestHelper h) {
        var f = new ArenaFixture(h, false, false);
        // BLUE collects 3 zombies; RED fights BLUE's plan.
        for (int i = 0; i < 3; i++) f.ctx.get(CollectKeys.POOLS).get(TeamId.BLUE).add(ZOMBIE);
        f.game.endPhaseNow();
        f.ctx.get(WaveKeys.SETUPS).get(TeamId.BLUE).toWave(ZOMBIE, 1, 3);
        f.game.endPhaseNow();
        String tag = WaveTags.tag(TeamId.RED, 1);
        var battle = f.group().battle(TeamId.RED);
        h.startSequence()
            .thenWaitUntil(() -> h.assertTrue(f.red.level() == f.arena(TeamId.RED).level(), "RED player is in the RED arena"))
            .thenWaitUntil(() -> h.assertTrue(mobs(f.arena(TeamId.RED), tag).size() == 3, "3 zombies spawn"))
            .thenExecute(() -> {
                for (var mob : mobs(f.arena(TeamId.RED), tag)) {
                    h.assertTrue(mob.getType() == EntityTypes.ZOMBIE, "Wave mob is a zombie");
                    h.assertTrue(mob.isPersistenceRequired(), "Wave mob is persistent");
                    h.assertTrue(mob.getAttributeBaseValue(Attributes.FOLLOW_RANGE) >= 128, "Large follow range");
                    h.assertTrue(mob.getTarget() == f.red, "Zombie targets the RED player");
                }
                h.assertTrue(f.arena(TeamId.BLUE).level().getEntities(net.minecraft.world.level.entity.EntityTypeTest
                        .forClass(Mob.class), e -> true).isEmpty(), "BLUE fights the empty RED plan");
                for (var mob : mobs(f.arena(TeamId.RED), tag)) kill(mob, f.red);
            })
            .thenWaitUntil(() -> h.assertTrue(battle.state() == BattleFlow.State.COOLDOWN, "Wave 1 is cleared"))
            .thenExecute(() -> h.assertTrue(battle.flow.progress().killsInCurrentWave() == 0
                    && battle.flow.progress().clearedWaves() == 1, "Progress after clear"))
            .thenWaitUntil(() -> h.assertTrue(battle.state() == BattleFlow.State.COUNTDOWN && battle.flow.wave() == 2,
                    "Wave 2 countdown starts after the cooldown"))
            .thenSucceed();
    }

    /** PLAN T7 done criterion 2: all players dead -> teamEliminated is called once. */
    @GameTest(environment = "mobarena:arena_down", maxTicks = 400, skyAccess = true)
    public void allPlayersDownEliminatesOnce(GameTestHelper h) {
        var f = new ArenaFixture(h, false, true);
        h.startSequence()
            .thenWaitUntil(() -> h.assertTrue(f.red.level() == f.arena(TeamId.RED).level(), "RED player is in the arena"))
            .thenExecute(() -> {
                killPlayer(f.red);
                h.assertTrue(f.red.isAlive() && f.red.isSpectator(), "Arena death makes a spectator, not a vanilla death");
            })
            .thenWaitUntil(() -> h.assertTrue(f.results.contains("eliminated RED 0 0"), "RED is eliminated"))
            .thenIdle(5)
            .thenExecute(() -> {
                h.assertTrue(f.results.stream().filter(r -> r.startsWith("eliminated RED")).count() == 1, "Eliminated once");
                h.assertTrue(f.red.level() == f.arena(TeamId.BLUE).level() && f.red.isSpectator(),
                        "Eliminated player watches the BLUE arena");
            })
            .thenSucceed();
    }

    @GameTest(environment = "mobarena:arena_respawn", maxTicks = 600, skyAccess = true)
    public void secondPlayerKeepsTeamAliveAndRespawns(GameTestHelper h) {
        var f = new ArenaFixture(h, true, true);
        f.group().setRespawnTicks(40);
        var battle = f.run(TeamId.RED, 20, 20, LONG, LONG, Map.of(ZOMBIE, 1), Map.of(), Map.of());
        h.startSequence()
            .thenWaitUntil(() -> h.assertTrue(battle.state() == BattleFlow.State.FIGHT && f.mate.level() == f.arena(TeamId.RED).level(),
                    "Fight starts"))
            .thenExecute(() -> {
                f.red.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 2));
                killPlayer(f.red);
                h.assertTrue(f.red.isSpectator(), "Dead player is a spectator");
            })
            .thenIdle(10)
            .thenExecute(() -> h.assertTrue(battle.state() == BattleFlow.State.FIGHT && f.results.isEmpty(),
                    "One alive player keeps the team in the game"))
            .thenWaitUntil(() -> h.assertTrue(f.red.gameMode() == GameType.SURVIVAL, "Player respawns after the timer"))
            .thenExecute(() -> {
                var spawn = Vec3.atBottomCenterOf(f.arena(TeamId.RED).layout().teamSpawn());
                h.assertTrue(f.red.position().distanceTo(spawn) < 1.5, "Respawn at the team spawn");
                h.assertTrue(f.red.getHealth() == f.red.getMaxHealth(), "Full health");
                h.assertTrue(f.red.getInventory().getItem(0).is(Items.DIAMOND), "Items stay");
                killPlayer(f.red); killPlayer(f.mate);
            })
            .thenWaitUntil(() -> h.assertTrue(f.results.contains("eliminated RED 0 0"), "Both down -> eliminated"))
            .thenSucceed();
    }

    @GameTest(environment = "mobarena:arena_totem", maxTicks = 300, skyAccess = true)
    public void totemSavesTheVanillaWay(GameTestHelper h) {
        var f = new ArenaFixture(h, false, true);
        h.startSequence()
            .thenWaitUntil(() -> h.assertTrue(f.red.level() == f.arena(TeamId.RED).level(), "RED in arena"))
            .thenExecute(() -> {
                f.red.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
                damageable(f.red);
                f.red.hurtServer(f.red.level(), f.red.damageSources().generic(), 1000);
                h.assertTrue(f.red.isAlive() && !f.red.isSpectator(), "Totem keeps the player alive and playing");
                h.assertTrue(f.red.getOffhandItem().isEmpty(), "Totem is used");
            })
            .thenIdle(3)
            .thenExecute(() -> h.assertTrue(f.results.isEmpty(), "No elimination"))
            .thenSucceed();
    }

    @GameTest(environment = "mobarena:arena_slime", maxTicks = 600, skyAccess = true)
    public void slimeChildrenKeepTagAndBlockTheClear(GameTestHelper h) {
        var f = new ArenaFixture(h, false, true);
        var battle = f.run(TeamId.RED, 1, 20, LONG, LONG, Map.of("minecraft:slime", 1), Map.of(ZOMBIE, 1), Map.of());
        String tag = WaveTags.tag(TeamId.RED, 1);
        h.startSequence()
            .thenWaitUntil(() -> h.assertTrue(mobs(f.arena(TeamId.RED), tag).size() == 1, "Slime spawns"))
            .thenExecute(() -> {
                var slime = (net.minecraft.world.entity.monster.cubemob.Slime) mobs(f.arena(TeamId.RED), tag).getFirst();
                slime.setSize(2, true);
                kill(slime, f.red);
            })
            .thenWaitUntil(() -> h.assertTrue(mobs(f.arena(TeamId.RED), tag).size() >= 2, "Children have the wave tag"))
            .thenIdle(5)
            .thenExecute(() -> {
                h.assertTrue(battle.state() == BattleFlow.State.FIGHT, "Wave is not clear while children live");
                for (var child : mobs(f.arena(TeamId.RED), tag)) {
                    h.assertTrue(child.isPersistenceRequired(), "Child is persistent");
                    kill(child, f.red);
                }
            })
            .thenWaitUntil(() -> {
                var left = mobs(f.arena(TeamId.RED), tag);
                left.forEach(m -> kill(m, f.red));
                h.assertTrue(battle.state() == BattleFlow.State.COOLDOWN, "Clear after all children die, left " + left.size()
                        + " " + left.stream().map(m -> m.getType().toShortString() + "@" + m.blockPosition().toShortString()).toList()
                        + " state " + battle.state() + " alive " + battle.countAlive());
            })
            .thenExecute(() -> h.assertTrue(battle.flow.progress().clearedWaves() == 1, "One wave cleared"))
            .thenSucceed();
    }

    @GameTest(environment = "mobarena:arena_neutral", maxTicks = 600, skyAccess = true)
    public void neutralMobsAreAngryPassiveMobsAreNot(GameTestHelper h) {
        var f = new ArenaFixture(h, false, true);
        var battle = f.run(TeamId.RED, 1, 20, LONG, LONG,
                Map.of("minecraft:iron_golem", 1, "minecraft:zombified_piglin", 1, "minecraft:piglin", 1, "minecraft:cow", 1),
                Map.of(), Map.of());
        String tag = WaveTags.tag(TeamId.RED, 1);
        h.startSequence()
            .thenWaitUntil(() -> h.assertTrue(mobs(f.arena(TeamId.RED), tag).size() == 4, "4 mobs spawn"))
            .thenIdle(25)
            .thenExecute(() -> {
                for (var mob : mobs(f.arena(TeamId.RED), tag)) {
                    if (mob instanceof IronGolem golem) h.assertTrue(golem.getTarget() == f.red, "Iron golem targets RED");
                    else if (mob instanceof NeutralMob angry) h.assertTrue(angry.isAngryAt(f.red, f.arena(TeamId.RED).level()),
                            "Zombified piglin is angry at RED");
                    else if (mob instanceof Piglin piglin) h.assertTrue(piglin.getBrain().getMemory(MemoryModuleType.ANGRY_AT)
                            .filter(f.red.getUUID()::equals).isPresent(), "Piglin brain is angry at RED");
                    else h.assertTrue(mob.getTarget() == null, "Cow has no target");
                }
            })
            .thenExecute(() -> {
                for (var mob : mobs(f.arena(TeamId.RED), tag)) mob.discard();
            })
            .thenWaitUntil(() -> h.assertTrue(battle.state() == BattleFlow.State.COOLDOWN, "Passive mobs still count until gone"))
            .thenSucceed();
    }

    @GameTest(environment = "mobarena:arena_bounds", maxTicks = 600, skyAccess = true)
    public void boundsTeleportBackAndGlowAfterTime(GameTestHelper h) {
        var f = new ArenaFixture(h, false, true);
        var battle = f.run(TeamId.RED, 1, 20, LONG, 60, Map.of("minecraft:cow", 2), Map.of(), Map.of());
        String tag = WaveTags.tag(TeamId.RED, 1);
        h.startSequence()
            .thenWaitUntil(() -> h.assertTrue(mobs(f.arena(TeamId.RED), tag).size() == 2, "Cows spawn"))
            .thenExecute(() -> {
                var cows = mobs(f.arena(TeamId.RED), tag);
                cows.get(0).teleportTo(100, 64, 100);
                cows.get(1).teleportTo(0, 20, 0);
            })
            .thenWaitUntil(() -> {
                var layout = f.arena(TeamId.RED).layout();
                for (var cow : mobs(f.arena(TeamId.RED), tag)) {
                    h.assertTrue(layout.bounds().contains(cow.position()), "Cow is back inside the bounds");
                    h.assertTrue(layout.mobSpawns().stream().anyMatch(p -> Vec3.atBottomCenterOf(p).distanceTo(cow.position()) < 2),
                            "Cow is at a ring point");
                }
            })
            .thenWaitUntil(() -> h.assertTrue(battle.glowing() && mobs(f.arena(TeamId.RED), tag).stream().allMatch(Entity::hasGlowingTag),
                    "Mobs glow after the glow time"))
            .thenSucceed();
    }

    @GameTest(environment = "mobarena:arena_time", maxTicks = 400, skyAccess = true)
    public void waveTimeLimitEliminates(GameTestHelper h) {
        var f = new ArenaFixture(h, false, true);
        f.run(TeamId.RED, 1, 20, 60, LONG, Map.of("minecraft:cow", 2), Map.of(), Map.of());
        String tag = WaveTags.tag(TeamId.RED, 1);
        h.startSequence()
            .thenWaitUntil(() -> h.assertTrue(mobs(f.arena(TeamId.RED), tag).size() == 2, "Cows spawn"))
            .thenExecute(() -> kill(mobs(f.arena(TeamId.RED), tag).getFirst(), f.red))
            .thenWaitUntil(() -> h.assertTrue(f.results.contains("eliminated RED 0 1"), "Time limit eliminates with 1 kill"))
            .thenExecute(() -> h.assertTrue(mobs(f.arena(TeamId.RED), tag).isEmpty(), "Wave mobs are removed"))
            .thenSucceed();
    }

    @GameTest(environment = "mobarena:arena_water", maxTicks = 400, skyAccess = true)
    public void waterMobsUseBasinOrAWaterBlock(GameTestHelper h) {
        var f = new ArenaFixture(h, false, true);
        var real = f.arena(TeamId.RED);
        var noBasin = new Arena(real.level(), new ArenaLayout(real.layout().teamSpawn(), real.layout().mobSpawns().subList(0, 1),
                List.of(), real.layout().bounds()));
        String tag = WaveTags.tag(TeamId.RED, 1);
        var script = new BattleScript(List.of(new TreeMap<>(Map.of("minecraft:squid", 1)), new TreeMap<>(), new TreeMap<>()), 1, 20, LONG, LONG);
        f.group().start(TeamId.RED, () -> Optional.of(noBasin), script, f.recorder, false);
        var point = noBasin.layout().mobSpawns().getFirst();
        h.startSequence()
            .thenWaitUntil(() -> h.assertTrue(mobs(real, tag).size() == 1, "Squid spawns"))
            .thenExecute(() -> {
                h.assertTrue(real.level().getBlockState(point.below()).is(Blocks.WATER), "A water block is placed");
                h.assertTrue(mobs(real, tag).getFirst().isInWater(), "Squid is in water");
                h.assertTrue(WaveSpawner.isWater("minecraft:squid") && !WaveSpawner.isWater(ZOMBIE), "Aquatic check");
            })
            .thenSucceed();
    }

    @GameTest(environment = "mobarena:arena_rules", maxTicks = 400, skyAccess = true)
    public void naturalSpawnsAndEggsBlockedSummonsTagged(GameTestHelper h) {
        var f = new ArenaFixture(h, false, true);
        var battle = f.run(TeamId.RED, 1, 20, LONG, LONG, Map.of("minecraft:cow", 1), Map.of(), Map.of());
        String tag = WaveTags.tag(TeamId.RED, 1);
        h.startSequence()
            .thenWaitUntil(() -> h.assertTrue(mobs(f.arena(TeamId.RED), tag).size() == 1, "Wave runs"))
            .thenExecute(() -> {
                var level = f.arena(TeamId.RED).level();
                var pos = f.arena(TeamId.RED).layout().mobSpawns().get(3);
                var natural = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.NATURAL);
                natural.snapTo(Vec3.atBottomCenterOf(pos));
                h.assertTrue(!level.addFreshEntity(natural), "Natural spawn is blocked");
                var egg = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.SPAWN_ITEM_USE);
                egg.snapTo(Vec3.atBottomCenterOf(pos));
                h.assertTrue(!level.addFreshEntity(egg), "Spawn egg mob is blocked");
                var summon = EntityTypes.VEX.create(level, EntitySpawnReason.MOB_SUMMONED);
                summon.snapTo(Vec3.atBottomCenterOf(pos).add(0, 1, 0));
                h.assertTrue(level.addFreshEntity(summon) && summon.entityTags().contains(tag), "Summoned child gets the wave tag");
                f.red.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.ZOMBIE_SPAWN_EGG));
                var result = net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.invoker()
                        .interact(f.red, level, InteractionHand.MAIN_HAND);
                h.assertTrue(result == net.minecraft.world.InteractionResult.FAIL, "Spawn egg use fails in the arena");
                summon.discard();
            })
            .thenSucceed();
    }

    @GameTest(environment = "mobarena:arena_spectate", maxTicks = 600, skyAccess = true)
    public void rejoinDuringFightStartsRespawnTimer(GameTestHelper h) {
        var f = new ArenaFixture(h, true, true);
        f.group().setRespawnTicks(30);
        var battle = f.run(TeamId.RED, 1, 20, LONG, LONG, Map.of("minecraft:cow", 1), Map.of(), Map.of());
        h.startSequence()
            .thenWaitUntil(() -> h.assertTrue(battle.state() == BattleFlow.State.FIGHT && f.red.level() == f.arena(TeamId.RED).level(), "Fight"))
            .thenExecute(() -> {
                f.group().rejoin(f.red);
                h.assertTrue(f.red.isSpectator() && f.group().respawns().isDown(f.red.getUUID()), "Rejoin gets the respawn timer");
            })
            .thenWaitUntil(() -> h.assertTrue(!f.red.isSpectator(), "Respawn after the timer"))
            .thenExecute(() -> h.assertTrue(f.results.isEmpty(), "Mate kept the team alive"))
            .thenSucceed();
    }

    /** T8b reuse: one wave with all 3 enemy waves, tag wave 4, no cooldown, own listener. */
    @GameTest(environment = "mobarena:arena_sudden", maxTicks = 600, skyAccess = true)
    public void suddenDeathRunnerMergesEnemyWaves(GameTestHelper h) {
        var f = new ArenaFixture(h, false, true);
        var plans = new EnumMap<TeamId, dev.mobarena.wave.WavePlan>(TeamId.class);
        plans.put(TeamId.BLUE, new dev.mobarena.wave.WavePlan(TeamId.BLUE, List.of(new TreeMap<>(Map.of(ZOMBIE, 1)),
                new TreeMap<>(Map.of(ZOMBIE, 1, "minecraft:cow", 1)), new TreeMap<>())));
        plans.put(TeamId.RED, new dev.mobarena.wave.WavePlan(TeamId.RED, List.of(new TreeMap<>(), new TreeMap<>(), new TreeMap<>())));
        f.ctx.put(WaveKeys.PLANS, plans);
        var group = ArenaPhaseListener.startSuddenDeath(f.ctx, f.recorder);
        var battle = group.battle(TeamId.RED);
        String tag = WaveTags.tag(TeamId.RED, WaveTags.SUDDEN_DEATH_WAVE);
        h.assertTrue(battle.script.waves().size() == 1 && battle.script.waves().getFirst().equals(Map.of(ZOMBIE, 2, "minecraft:cow", 1))
                && battle.script.cooldownTicks() == 0, "One merged wave, no cooldown");
        h.startSequence()
            .thenWaitUntil(() -> h.assertTrue(mobs(f.arena(TeamId.RED), tag).size() == 3, "All 3 enemy mobs spawn at once with tag 4"))
            .thenExecute(() -> mobs(f.arena(TeamId.RED), tag).forEach(m -> kill(m, f.red)))
            .thenWaitUntil(() -> h.assertTrue(f.results.contains("finished RED"), "First clear reports finished to the T8 listener"))
            .thenSucceed();
    }

    private static List<? extends net.minecraft.world.entity.monster.cubemob.Slime> slimes(Arena arena) {
        return arena.level().getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(
                net.minecraft.world.entity.monster.cubemob.Slime.class), e -> !e.isRemoved());
    }

    @GameTest(environment = "mobarena:arena_discard", maxTicks = 600, skyAccess = true)
    public void dyingSlimeOnEliminationOrCloseLeavesNoSlimes(GameTestHelper h) {
        var f = new ArenaFixture(h, false, true);
        var red = f.run(TeamId.RED, 1, 20, LONG, LONG, Map.of("minecraft:slime", 1), Map.of(), Map.of());
        f.run(TeamId.BLUE, 1, 20, LONG, LONG, Map.of("minecraft:slime", 1), Map.of(), Map.of());
        String redTag = WaveTags.tag(TeamId.RED, 1), blueTag = WaveTags.tag(TeamId.BLUE, 1);
        h.startSequence()
            .thenWaitUntil(() -> h.assertTrue(mobs(f.arena(TeamId.RED), redTag).size() == 1
                    && mobs(f.arena(TeamId.BLUE), blueTag).size() == 1, "Both slimes spawn"))
            .thenExecute(() -> {
                // Big dying bodies; vanilla splits them on removal, also on discard().
                for (net.minecraft.world.entity.monster.cubemob.Slime slime : List.of(slimes(f.arena(TeamId.RED)).getFirst(), slimes(f.arena(TeamId.BLUE)).getFirst())) {
                    slime.setSize(4, true);
                    kill(slime, f.red);
                    h.assertTrue(slime.isDeadOrDying() && !slime.isRemoved(), "Slime body is dying");
                }
                killPlayer(f.red);
            })
            .thenWaitUntil(() -> h.assertTrue(red.state() == BattleFlow.State.ELIMINATED, "RED is eliminated"))
            .thenExecute(() -> {
                h.assertTrue(slimes(f.arena(TeamId.RED)).isEmpty(), "Elimination leaves no slime");
                f.group().close();
                h.assertTrue(slimes(f.arena(TeamId.BLUE)).isEmpty(), "Close leaves no slime");
            })
            .thenIdle(30)
            .thenExecute(() -> h.assertTrue(slimes(f.arena(TeamId.RED)).isEmpty() && slimes(f.arena(TeamId.BLUE)).isEmpty(),
                    "No late split children"))
            .thenSucceed();
    }

    @GameTest(environment = "mobarena:arena_safety", maxTicks = 600, skyAccess = true)
    public void oldWaveSlimeSplitLeavesNoChildren(GameTestHelper h) {
        var f = new ArenaFixture(h, false, true);
        var battle = f.run(TeamId.RED, 1, 20, LONG, LONG, Map.of(ZOMBIE, 1), Map.of(), Map.of());
        String oldTag = WaveTags.tag(TeamId.RED, 2);
        h.startSequence()
            .thenWaitUntil(() -> h.assertTrue(battle.state() == BattleFlow.State.FIGHT, "Wave 1 runs"))
            .thenExecute(() -> {
                var level = f.arena(TeamId.RED).level();
                var slime = EntityTypes.SLIME.create(level, EntitySpawnReason.COMMAND);
                slime.snapTo(Vec3.atBottomCenterOf(f.arena(TeamId.RED).layout().mobSpawns().get(2)));
                slime.setSize(4, true);
                slime.addTag(oldTag);
                h.assertTrue(level.addFreshEntity(slime), "Slime with a tag of no running wave is added");
                kill(slime, f.red);
            })
            .thenIdle(40)
            .thenExecute(() -> h.assertTrue(slimes(f.arena(TeamId.RED)).isEmpty(),
                    "Split children of an old wave mob are discarded, left " + slimes(f.arena(TeamId.RED)).size()))
            .thenSucceed();
    }

    @GameTest(environment = "mobarena:arena_golem", maxTicks = 600, skyAccess = true)
    public void playerBuiltGolemsAndWithersAreBlocked(GameTestHelper h) {
        var f = new ArenaFixture(h, false, true);
        h.startSequence()
            .thenWaitUntil(() -> h.assertTrue(f.red.level() == f.arena(TeamId.RED).level(), "Arena is ready"))
            .thenExecute(() -> {
                var arena = f.arena(TeamId.RED);
                var level = arena.level();
                var pos = arena.layout().mobSpawns().get(5);
                for (var type : List.<EntityType<? extends Mob>>of(EntityTypes.IRON_GOLEM, EntityTypes.SNOW_GOLEM,
                        EntityTypes.COPPER_GOLEM, EntityTypes.WITHER)) {
                    var mob = type.create(level, EntitySpawnReason.TRIGGERED);
                    mob.snapTo(Vec3.atBottomCenterOf(pos));
                    h.assertTrue(!level.addFreshEntity(mob), "Built " + type.toShortString() + " is blocked");
                }
                // A real snow golem pattern: the pumpkin placement triggers the build.
                level.setBlockAndUpdate(pos, Blocks.SNOW_BLOCK.defaultBlockState());
                level.setBlockAndUpdate(pos.above(), Blocks.SNOW_BLOCK.defaultBlockState());
                level.setBlockAndUpdate(pos.above(2), Blocks.CARVED_PUMPKIN.defaultBlockState());
                h.assertTrue(level.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(
                        net.minecraft.world.entity.animal.golem.SnowGolem.class), e -> true).isEmpty(), "No snow golem in the arena");
                var command = EntityTypes.IRON_GOLEM.create(level, EntitySpawnReason.COMMAND);
                command.snapTo(Vec3.atBottomCenterOf(pos).add(3, 0, 0));
                h.assertTrue(level.addFreshEntity(command), "Admin summon still works");
                command.discard();
            })
            .thenSucceed();
    }

    @GameTest(environment = "mobarena:arena_infight", maxTicks = 600, skyAccess = true)
    public void waveMobsDoNotFightEachOther(GameTestHelper h) {
        var f = new ArenaFixture(h, false, true);
        var battle = f.run(TeamId.RED, 1, 20, LONG, LONG,
                Map.of("minecraft:iron_golem", 1, "minecraft:zombified_piglin", 2), Map.of(), Map.of());
        String tag = WaveTags.tag(TeamId.RED, 1);
        h.startSequence()
            .thenWaitUntil(() -> h.assertTrue(mobs(f.arena(TeamId.RED), tag).size() == 3, "Golem and 2 zombified piglins spawn"))
            .thenExecute(() -> {
                var all = mobs(f.arena(TeamId.RED), tag);
                var golem = all.stream().filter(m -> m instanceof IronGolem).findFirst().orElseThrow();
                var piglin = all.stream().filter(m -> !(m instanceof IronGolem)).findFirst().orElseThrow();
                // An iron golem picks any Enemy as target; setTarget must refuse a wave mob.
                golem.setTarget(piglin);
                h.assertTrue(golem.getTarget() != piglin, "Iron golem accepts a wave mob as target");
                var level = f.arena(TeamId.RED).level();
                float health = piglin.getHealth();
                h.assertTrue(!piglin.hurtServer(level, piglin.damageSources().mobAttack(golem), 5) && piglin.getHealth() == health,
                        "A wave mob cannot hurt a mob of the same wave");
            })
            .thenExecuteFor(200, () -> {
                for (var mob : mobs(f.arena(TeamId.RED), tag)) {
                    h.assertTrue(!WaveMobAi.waveMob(mob.getTarget()), mob.getType().toShortString() + " targets a wave mob");
                    h.assertTrue(!WaveMobAi.waveMob(mob.getLastHurtByMob()), mob.getType().toShortString() + " was hurt by a wave mob");
                    h.assertTrue(mob.getHealth() == mob.getMaxHealth(), mob.getType().toShortString() + " lost health");
                }
            })
            .thenExecute(() -> {
                for (var mob : mobs(f.arena(TeamId.RED), tag))
                    h.assertTrue(mob.getTarget() == f.red, mob.getType().toShortString() + " targets RED");
            })
            .thenSucceed();
    }
}
