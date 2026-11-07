package dev.mobarena.arena;

import dev.mobarena.team.TeamId;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.entity.EntityTypeTest;
import java.util.Map;

/** T7 decision: a wave spawns exactly the collected mobs, without random jockeys from finalizeSpawn. */
public class NoJockeyGameTest {
    // Vanilla: 1% of spiders get a skeleton rider, about 0.25% of zombies a new chicken. Without the
    // rule, 300 + 300 spawns add an extra mob in about 98% of runs.
    private static final int EACH = 300;

    @GameTest(environment = "mobarena:arena_no_jockeys", maxTicks = 400, skyAccess = true)
    public void waveSpawnsExactlyTheCollectedMobs(GameTestHelper h) {
        var f = new ArenaFixture(h, false, true);
        var battle = f.run(TeamId.RED, 0, 0, 6000, 6000, Map.of("minecraft:cow", 1));
        h.startSequence()
            .thenWaitUntil(() -> h.assertTrue(battle.state() == BattleFlow.State.FIGHT && battle.currentTag() != null, "Wave 1 runs"))
            .thenExecute(() -> {
                var level = f.arena(TeamId.RED).level();
                int before = level.getEntities(EntityTypeTest.forClass(Mob.class), m -> !m.isRemoved()).size();
                int spawned = 0;
                for (int i = 0; i < EACH; i++) {
                    if (WaveSpawner.spawn(battle, new SpawnPlanner.SpawnOrder("minecraft:spider", i, false))) spawned++;
                    if (WaveSpawner.spawn(battle, new SpawnPlanner.SpawnOrder("minecraft:zombie", i, false))) spawned++;
                }
                h.assertValueEqual(spawned, 2 * EACH, "All spawn orders succeed");
                var mobs = level.getEntities(EntityTypeTest.forClass(Mob.class), m -> !m.isRemoved());
                h.assertValueEqual(mobs.size() - before, 2 * EACH, "Exactly the ordered mobs are in the arena");
                h.assertValueEqual(ArenaGameTest.mobs(f.arena(TeamId.RED), battle.currentTag()).size(), before + 2 * EACH,
                        "Exactly the ordered mobs carry the wave tag");
                for (var mob : mobs) {
                    h.assertTrue(!mob.isPassenger() && !mob.isVehicle(), "No jockey: " + mob.getType() + " " + mob.getVehicle());
                    var id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString();
                    h.assertTrue(id.equals("minecraft:spider") || id.equals("minecraft:zombie") || id.equals("minecraft:cow"),
                            "Only collected types: " + id);
                }
            })
            .thenExecute(f::close).thenSucceed();
    }

    /** A chicken that was in the arena before stays after the unmount and loses the jockey flag. */
    @GameTest(environment = "mobarena:arena_jockey_flag", maxTicks = 400, skyAccess = true)
    public void existingChickenStaysWithoutJockeyFlag(GameTestHelper h) {
        var f = new ArenaFixture(h, false, true);
        var battle = f.run(TeamId.RED, 0, 0, 6000, 6000, Map.of("minecraft:cow", 1));
        h.startSequence()
            .thenWaitUntil(() -> h.assertTrue(battle.state() == BattleFlow.State.FIGHT, "Wave 1 runs"))
            .thenExecute(() -> {
                var level = f.arena(TeamId.RED).level();
                var pos = net.minecraft.world.phys.Vec3.atBottomCenterOf(f.arena(TeamId.RED).layout().teamSpawn());
                var chicken = net.minecraft.world.entity.EntityTypes.CHICKEN.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                chicken.snapTo(pos.x, pos.y, pos.z, 0, 0);
                h.assertTrue(level.addFreshEntity(chicken), "Placed chicken is in the arena");
                // The same state as after Zombie.finalizeSpawn with a chicken nearby.
                var zombie = net.minecraft.world.entity.EntityTypes.ZOMBIE.create(level, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
                zombie.snapTo(pos.x, pos.y, pos.z, 0, 0);
                chicken.setChickenJockey(true);
                h.assertTrue(zombie.startRiding(chicken, true, false), "Zombie rides the placed chicken");
                var rider = net.minecraft.world.entity.EntityTypes.SKELETON.create(level, net.minecraft.world.entity.EntitySpawnReason.JOCKEY);
                h.assertTrue(rider.startRiding(zombie, true, false), "Skeleton rides the zombie");
                WaveSpawner.removeJockeys(level, zombie);
                h.assertTrue(!zombie.isPassenger() && !chicken.isVehicle(), "The wave mob dismounts");
                h.assertTrue(!chicken.isRemoved() && level.getEntity(chicken.getUUID()) == chicken, "The placed chicken stays");
                h.assertFalse(chicken.isChickenJockey(), "The placed chicken is no jockey");
                h.assertTrue(rider.isRemoved() && !zombie.isVehicle(), "The rider is discarded");
                // A new chicken that never entered the level is discarded.
                var fresh = net.minecraft.world.entity.EntityTypes.CHICKEN.create(level, net.minecraft.world.entity.EntitySpawnReason.JOCKEY);
                fresh.snapTo(pos.x, pos.y, pos.z, 0, 0);
                fresh.setChickenJockey(true);
                h.assertTrue(zombie.startRiding(fresh, true, false), "Zombie rides the new chicken");
                WaveSpawner.removeJockeys(level, zombie);
                h.assertTrue(fresh.isRemoved() && !zombie.isPassenger(), "A new jockey chicken is discarded");
                zombie.discard();
                chicken.discard();
            })
            .thenExecute(f::close).thenSucceed();
    }
}
