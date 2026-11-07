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
}
