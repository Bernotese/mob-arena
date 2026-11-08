package dev.mobarena.arena;

import dev.mobarena.core.Phase;
import dev.mobarena.gametest.TestEnd;
import dev.mobarena.team.TeamId;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

public class TeleportCollisionGameTest {
    @GameTest(environment = "mobarena:teleport_collision", maxTicks = 400)
    public void arenaSpawnRestoresCollisionBelowFeet(GameTestHelper h) {
        var f = new ArenaFixture(h, true, false);
        f.game.endPhaseNow();
        h.assertTrue(f.game.phase() == Phase.WAVE_CONFIG, "Arena chunks load during setup");
        var arena = f.arena(TeamId.RED);
        var bounds = arena.layout().bounds();
        for (int x = ((int) Math.floor(bounds.minX)) >> 4; x <= ((int) Math.floor(bounds.maxX - 1)) >> 4; x++) {
            for (int z = ((int) Math.floor(bounds.minZ)) >> 4; z <= ((int) Math.floor(bounds.maxZ - 1)) >> 4; z++) {
                h.assertTrue(arena.level().getForceLoadedChunks().contains(net.minecraft.world.level.ChunkPos.pack(x, z)),
                        "Arena chunk is forced before ARENA entry");
            }
        }
        f.game.endPhaseNow();
        h.startSequence().thenWaitUntil(() -> h.assertTrue(f.group().battle(TeamId.RED).running(), "Arena is ready"))
            .thenExecute(() -> {
                var battle = f.group().battle(TeamId.RED);
                f.red.verticalCollisionBelow = false;
                battle.placeAtSpawn(f.red);
                h.assertTrue(f.red.verticalCollisionBelow, "Living spawn restores collision below feet");
                f.red.setGameMode(GameType.SPECTATOR);
                f.red.verticalCollisionBelow = false;
                battle.placeAtSpawn(f.red);
                h.assertTrue(f.red.verticalCollisionBelow, "Spectator respawn restores collision after survival mode");
                var spawn = Vec3.atBottomCenterOf(arena.layout().teamSpawn());
                f.group().respawns().down(f.red, battle, false);
                h.assertTrue(f.red.position().distanceTo(spawn) < 0.01, "Timer uses the safe spawn floor");
                h.assertTrue(!f.red.verticalCollisionBelow, "Spectator has no collision below feet");
            }).thenExecute(f::close).thenSucceed();
    }
}
