package dev.mobarena.world;

import dev.mobarena.team.TeamId;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.gamerules.GameRules;

public class WorldGameTest {
    @GameTest
    public void w2IndependentGameRules(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        for (var kind : WorldKind.values()) {
            if (kind == WorldKind.ARENA) helper.assertTrue(kind.vanillaKey() == null, "Arena has no vanilla key");
            else helper.assertValueEqual(PortalLink.kindFor(kind.vanillaKey()), kind, "Vanilla keys map to their prep kinds");
        }
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

    @GameTest(maxTicks = 1600, skyAccess = true)
    public void w1PrepAndW11Cleanup(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        // Other module tests are synchronous. Start this lifecycle test after their initial tick.
        helper.runAfterDelay(2, () -> {
            var settings = new WorldTestSettings(server);
            helper.runBeforeTestEnd(settings::close);
            StructureWorldChecks.verify(helper);
            var game = dev.mobarena.core.GameManager.get();
            var redPlayer = WorldTestPlayers.create(helper);
            var bluePlayer = WorldTestPlayers.create(helper);
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
            var players = PortalWorldChecks.verify(helper, ctx, redPlayer, bluePlayer);
            rejoin(helper, ctx, players[0]);
            mappedBlockDrop(helper, ctx);
            ArenaWorldChecks.verify(helper, ctx, players[0], () -> EndWorldChecks.verify(helper, ctx, players[0], players[1], () -> {
            var keys = worlds.values().stream().flatMap(TeamWorlds::handles).map(h -> h.getRegistryKey()).toList();
            game.stop();
            helper.assertTrue(players[0].getRespawnConfig() == null && players[1].getRespawnConfig() == null,
                    "LOBBY clears beds in deleted levels");
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
            }));
        });
    }
    private static void mappedBlockDrop(GameTestHelper helper, dev.mobarena.core.GameContext ctx) {
        var level = ctx.prepLevel(TeamId.RED);
        var pos = ctx.get(TeamWorlds.KEY).get(TeamId.RED).prepSpawn().offset(4, 0, 4);
        level.setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.OAK_LOG.defaultBlockState());
        var expected = dev.mobarena.randomizer.RandomizerModule.hook().mapFor("RED").get(net.minecraft.world.item.Items.OAK_LOG);
        helper.assertTrue(expected != null, "PREP starts the RED randomizer map");
        level.destroyBlock(pos, true);
        helper.assertTrue(!level.getEntities(net.minecraft.world.entity.EntityTypes.ITEM,
                new net.minecraft.world.phys.AABB(pos).inflate(3), item -> item.getItem().is(expected)).isEmpty(),
                "RED prep block drops the RED-mapped item");
    }
    private static void rejoin(GameTestHelper h, dev.mobarena.core.GameContext ctx,
                               net.minecraft.server.level.ServerPlayer player) {
        java.nio.file.Path file = null;
        try {
            file = java.nio.file.Files.createTempFile("mobarena-rejoin-", ".json");
            java.nio.file.Files.delete(file);
            var lobby = new dev.mobarena.core.LobbyManager(file);
            lobby.prepare(ctx.server());
            WorldModule.registerRejoinRouting(lobby);
            var world = ctx.get(TeamWorlds.KEY).get(ctx.team(player.getUUID()));
            lobby.join(player);
            h.assertTrue(player.level() == world.overworld().asLevel(), "PREP rejoin chooses own world");
            h.assertTrue(player.position().distanceTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(world.prepSpawn())) < 1,
                    "PREP rejoin without return uses team spawn");
            var saved = net.minecraft.world.phys.Vec3.atBottomCenterOf(world.prepSpawn().offset(30, 5, 30));
            player.teleportTo(saved.x, saved.y, saved.z);
            lobby.leave(player);
            WorldModule.rememberPrepReturn(ctx, player);
            lobby.sendToLobby(player, false);
            lobby.join(player);
            h.assertTrue(player.position().distanceTo(saved) < 1, "PREP rejoin preserves a saved return position");
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
        finally { if (file != null) try { java.nio.file.Files.deleteIfExists(file); }
            catch (java.io.IOException e) { throw new IllegalStateException(e); } }
    }
}
