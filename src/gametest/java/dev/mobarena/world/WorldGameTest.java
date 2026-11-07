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
        try {
            for (WorldKind kind : WorldKind.values()) {
                var level = LevelFactory.open(server, 42, TeamId.RED, kind).asLevel();
                helper.assertTrue(level.getGameRules() != server.getGlobalGameRules(), "Rules have separate instances");
                helper.assertTrue(level.getGameRules().get(GameRules.KEEP_INVENTORY), "Game keeps inventory");
                helper.assertValueEqual(server.getGlobalGameRules().get(GameRules.KEEP_INVENTORY), original, "Global rule unchanged");
                server.getGlobalGameRules().set(GameRules.KEEP_INVENTORY, false, server);
                helper.assertTrue(level.getGameRules().get(GameRules.KEEP_INVENTORY), "Global edit leaves game rule unchanged");
                helper.assertFalse(server.overworld().getGameRules().get(GameRules.KEEP_INVENTORY), "Lobby rule stays global");
                level.getGameRules().set(GameRules.KEEP_INVENTORY, false, server);
                server.getGlobalGameRules().set(GameRules.KEEP_INVENTORY, true, server);
                helper.assertFalse(level.getGameRules().get(GameRules.KEEP_INVENTORY), "Game edit stays independent");
                server.getGlobalGameRules().set(GameRules.KEEP_INVENTORY, original, server);
            }
        } finally {
            server.getGlobalGameRules().set(GameRules.KEEP_INVENTORY, original, server);
            WorldCleanup.clean(server);
        }
        helper.succeed();
    }

    @GameTest(maxTicks = 1200)
    public void w1PrepAndW11Cleanup(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        // Wait until Fantasy removes the first test level before using the same names again.
        helper.runAfterDelay(2, () -> {
            var game = dev.mobarena.core.GameManager.get();
            var redPlayer = helper.makeMockServerPlayerInLevel();
            var bluePlayer = helper.makeMockServerPlayerInLevel();
            var teams = java.util.Map.of(redPlayer.getUUID(), TeamId.RED, bluePlayer.getUUID(), TeamId.BLUE);
            helper.assertTrue(game.start(server, teams, teams.keySet()), "First game starts");
            var ctx = game.context();
            helper.runBeforeTestEnd(() -> WorldCleanup.clean(server));
            var worlds = ctx.get(TeamWorlds.KEY);
            helper.assertValueEqual(worlds.size(), 2, "Two teams");
            var red = worlds.get(TeamId.RED);
            var blue = worlds.get(TeamId.BLUE);
            helper.assertValueEqual(red.prepSpawn(), blue.prepSpawn(), "Same seed gives same spawn");
            for (var team : worlds.values()) team.handles().forEach(h -> {
                helper.assertTrue(h.asLevel().getGameRules().get(GameRules.KEEP_INVENTORY), "Every prep level keeps inventory");
                helper.assertValueEqual(h.asLevel().getSeed(), ctx.seed(), "Game seed");
            });
            for (int x : new int[]{-16, 0, 16}) {
                int z = 8;
                for (int y : new int[]{0, 63, 80}) helper.assertValueEqual(
                        red.overworld().asLevel().getBlockState(new net.minecraft.core.BlockPos(x, y, z)),
                        blue.overworld().asLevel().getBlockState(new net.minecraft.core.BlockPos(x, y, z)), "Equal terrain");
            }
            var keys = worlds.values().stream().flatMap(TeamWorlds::handles).map(h -> h.getRegistryKey()).toList();
            game.stop();
            helper.assertTrue(game.start(server, teams, teams.keySet()), "New game starts in same tick as stop");
            var second = game.context().get(TeamWorlds.KEY);
            var secondKeys = second.values().stream().flatMap(TeamWorlds::handles).map(h -> h.getRegistryKey()).toList();
            helper.assertTrue(java.util.Collections.disjoint(keys, secondKeys), "Immediate restart uses new level keys");
            helper.runAfterDelay(5, () -> {
                for (var key : secondKeys) helper.assertTrue(server.getLevel(key) != null, "Old deletion keeps new game intact");
                game.stop();
                for (var key : keys) {
                    helper.assertTrue(server.getLevel(key) == null, "Deleted level is absent");
                    var path = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                            .resolve("dimensions").resolve(key.identifier().getNamespace()).resolve(key.identifier().getPath());
                    helper.assertFalse(java.nio.file.Files.exists(path), "Deleted level folder is absent");
                }
                helper.succeed();
            });
        });
    }
}
