package dev.mobarena.world;

import dev.mobarena.team.TeamId;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.gamerules.GameRules;

public class WorldGameTest {
    @GameTest
    public void w2IndependentGameRules(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        boolean original = server.getGlobalGameRules().get(GameRules.KEEP_INVENTORY);
        var handle = LevelFactory.open(server, 42, TeamId.RED, WorldKind.ARENA);
        try {
            helper.assertTrue(handle.asLevel().getGameRules().get(GameRules.KEEP_INVENTORY), "Game keeps inventory");
            helper.assertValueEqual(server.getGlobalGameRules().get(GameRules.KEEP_INVENTORY), original, "Global rule unchanged");
            server.getGlobalGameRules().set(GameRules.KEEP_INVENTORY, false, server);
            helper.assertTrue(handle.asLevel().getGameRules().get(GameRules.KEEP_INVENTORY), "Global edit leaves game rule unchanged");
            helper.assertFalse(server.overworld().getGameRules().get(GameRules.KEEP_INVENTORY), "Lobby rule stays global");
        } finally {
            server.getGlobalGameRules().set(GameRules.KEEP_INVENTORY, original, server);
            WorldCleanup.clean(server);
        }
        w1PrepAndW11Cleanup(helper);
    }

    private void w1PrepAndW11Cleanup(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        // Wait until Fantasy removes the first test level before using the same names again.
        helper.runAfterDelay(2, () -> {
            var ctx = new dev.mobarena.core.GameContext(server, 725L, java.util.Map.of(), new dev.mobarena.config.MobArenaConfig());
            helper.runBeforeTestEnd(() -> WorldCleanup.clean(server));
            var worlds = PrepWorldManager.create(ctx);
            helper.assertValueEqual(worlds.size(), 2, "Two teams");
            var red = worlds.get(TeamId.RED);
            var blue = worlds.get(TeamId.BLUE);
            helper.assertValueEqual(red.prepSpawn(), blue.prepSpawn(), "Same seed gives same spawn");
            for (var team : worlds.values()) team.handles().forEach(h -> {
                helper.assertTrue(h.asLevel().getGameRules().get(GameRules.KEEP_INVENTORY), "Every prep level keeps inventory");
                helper.assertValueEqual(h.asLevel().getSeed(), 725L, "Game seed");
            });
            for (int x : new int[]{-16, 0, 16}) {
                int z = 8;
                for (int y : new int[]{0, 63, 80}) helper.assertValueEqual(
                        red.overworld().asLevel().getBlockState(new net.minecraft.core.BlockPos(x, y, z)),
                        blue.overworld().asLevel().getBlockState(new net.minecraft.core.BlockPos(x, y, z)), "Equal terrain");
            }
            var keys = worlds.values().stream().flatMap(TeamWorlds::handles).map(h -> h.getRegistryKey()).toList();
            WorldCleanup.clean(server);
            helper.runAfterDelay(5, () -> {
                for (var key : keys) helper.assertTrue(server.getLevel(key) == null, "Deleted level is absent");
                helper.succeed();
            });
        });
    }
}
