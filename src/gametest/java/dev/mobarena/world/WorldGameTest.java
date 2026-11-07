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

    @GameTest(environment = "mobarena:world_lifecycle", maxTicks = 1600, skyAccess = true)
    public void w1PrepAndW11Cleanup(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        // This environment keeps global world generation settings separate from other module tests.
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
            helper.assertValueEqual(red.prepSpawn().getX(), blue.prepSpawn().getX(), "Same spawn X");
            helper.assertValueEqual(red.prepSpawn().getZ(), blue.prepSpawn().getZ(), "Same spawn Z");
            NoiseWorldChecks.verify(helper, red.overworld().asLevel(), blue.overworld().asLevel());
            for (var world : worlds.values()) NoiseWorldChecks.safeSpawn(helper, world.overworld().asLevel(), world.prepSpawn());
            for (var team : worlds.values()) team.handles().forEach(h -> {
                helper.assertTrue(h.asLevel().getGameRules().get(GameRules.KEEP_INVENTORY), "Every prep level keeps inventory");
                helper.assertValueEqual(h.asLevel().getSeed(), ctx.seed(), "Game seed");
            });
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
    @GameTest(environment = "mobarena:world_cleanup", maxTicks = 400)
    public void removedNetherClosesChunkWorker(GameTestHelper helper) throws Exception {
        var server = helper.getLevel().getServer();
        var level = LevelFactory.open(server, 42, TeamId.RED, WorldKind.PREP_NETHER).asLevel();
        var worker = (net.minecraft.world.level.chunk.storage.IOWorker) level.getChunkSource().chunkMap.chunkScanner();
        // Observe the real worker lifecycle; deletion alone previously left this flag false.
        var field = worker.getClass().getDeclaredField("shutdownRequested");
        field.setAccessible(true);
        var closed = (java.util.concurrent.atomic.AtomicBoolean) field.get(worker);
        helper.assertFalse(closed.get(), "New chunk worker is open");
        level.getChunk(0, 0);
        var folder = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve("dimensions/mobarena").resolve(level.dimension().identifier().getPath());
        WorldCleanup.clean(server);
        helper.succeedWhen(() -> {
            helper.assertTrue(server.getLevel(level.dimension()) == null, "Nether leaves the server level map");
            helper.assertTrue(closed.get(), "Removed Nether closes its chunk I/O worker");
            helper.assertFalse(java.nio.file.Files.exists(folder), "Closed Nether folder is deleted");
        });
    }

    @GameTest(environment = "mobarena:world_cleanup", maxTicks = 400)
    public void removedLevelFolderStaysDeleted(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var level = LevelFactory.open(server, 43, TeamId.RED, WorldKind.PREP_OVERWORLD).asLevel();
        // Dirty several chunks so their saves are queued while Fantasy deletes the level.
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            level.setBlock(new net.minecraft.core.BlockPos(x * 16 + 8, 200, z * 16 + 8),
                    net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
        }
        var folder = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve("dimensions/mobarena").resolve(level.dimension().identifier().getPath());
        WorldCleanup.clean(server);
        // A late chunk write must not recreate the folder after deletion.
        helper.onEachTick(() -> {
            if (server.getLevel(level.dimension()) == null) {
                helper.assertFalse(java.nio.file.Files.exists(folder), "Deleted level folder is not recreated");
            }
        });
        helper.runAfterDelay(250, () -> {
            helper.assertTrue(server.getLevel(level.dimension()) == null, "Level leaves the server level map");
            helper.assertFalse(java.nio.file.Files.exists(folder), "Level folder stays deleted");
            helper.succeed();
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
    @GameTest(environment = "mobarena:world_generation")
    public void w1SeedZeroAndSpawnClearance(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        // This environment can also clean levels left by a failed lifecycle test.
        WorldCleanup.clean(server);
        try (var settings = new WorldTestSettings(server)) {
            var red = LevelFactory.open(server, 0, TeamId.RED, WorldKind.PREP_OVERWORLD);
            var blue = LevelFactory.open(server, 0, TeamId.BLUE, WorldKind.PREP_OVERWORLD);
            try {
                NoiseWorldChecks.verify(helper, red.asLevel(), blue.asLevel());
                var column = TeamSpawnFinder.find(red.asLevel());
                var spawn = TeamSpawnFinder.atColumn(blue.asLevel(), column);
                helper.assertValueEqual(spawn.getX(), column.getX(), "Seed zero shares spawn X");
                helper.assertValueEqual(spawn.getZ(), column.getZ(), "Seed zero shares spawn Z");
                NoiseWorldChecks.safeSpawn(helper, red.asLevel(), column);
                NoiseWorldChecks.safeSpawn(helper, blue.asLevel(), spawn);
                // Decorations can differ. Force a trunk and a leaf above the shared column.
                var trunk = spawn.above(2);
                blue.asLevel().setBlockAndUpdate(trunk, net.minecraft.world.level.block.Blocks.OAK_LOG.defaultBlockState());
                blue.asLevel().setBlockAndUpdate(trunk.above(2), net.minecraft.world.level.block.Blocks.OAK_LEAVES.defaultBlockState());
                var raised = TeamSpawnFinder.atColumn(blue.asLevel(), column);
                helper.assertValueEqual(raised.getX(), column.getX(), "Trunk keeps shared X");
                helper.assertValueEqual(raised.getZ(), column.getZ(), "Trunk keeps shared Z");
                helper.assertTrue(raised.getY() > trunk.getY() + 2, "Spawn clears trunk and head obstruction");
                NoiseWorldChecks.safeSpawn(helper, blue.asLevel(), raised);
            } finally {
                // This test owns its environment, so no other test uses the tracked levels.
                WorldCleanup.clean(server);
            }
        }
        helper.succeed();
    }
    @GameTest(environment = "mobarena:world_fallback")
    public void w1WaterSpawnFallback(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var handles = new java.util.ArrayList<xyz.nucleoid.fantasy.RuntimeLevelHandle>();
        try {
            for (var team : TeamId.values()) {
                var config = LevelFactory.config(server, 0, WorldKind.PREP_OVERWORLD)
                        .setGenerator(new xyz.nucleoid.fantasy.util.VoidChunkGenerator(server));
                handles.add(xyz.nucleoid.fantasy.Fantasy.get(server).openTemporaryLevel(
                        net.minecraft.resources.Identifier.fromNamespaceAndPath("mobarena", "water_fallback_" + team.name().toLowerCase(java.util.Locale.ROOT)), config));
            }
            var red = handles.get(0).asLevel();
            var blue = handles.get(1).asLevel();
            // The empty region has no safe column. Water requires a support block at the origin.
            red.setBlockAndUpdate(new net.minecraft.core.BlockPos(0, 63, 0), net.minecraft.world.level.block.Blocks.WATER.defaultBlockState());
            blue.setBlockAndUpdate(new net.minecraft.core.BlockPos(0, 68, 0), net.minecraft.world.level.block.Blocks.WATER.defaultBlockState());
            var redSpawn = TeamSpawnFinder.find(red);
            var blueSpawn = TeamSpawnFinder.atColumn(blue, redSpawn);
            helper.assertValueEqual(redSpawn, new net.minecraft.core.BlockPos(0, 64, 0), "Water fallback uses origin surface");
            helper.assertValueEqual(blueSpawn, new net.minecraft.core.BlockPos(0, 69, 0), "BLUE fallback keeps column and uses own surface");
            for (int index = 0; index < handles.size(); index++) {
                var level = handles.get(index).asLevel();
                var spawn = index == 0 ? redSpawn : blueSpawn;
                helper.assertTrue(level.getBlockState(spawn.below()).is(net.minecraft.world.level.block.Blocks.STONE), "Water fallback has stone support");
                NoiseWorldChecks.safeSpawn(helper, level, spawn);
            }
        } finally { handles.forEach(xyz.nucleoid.fantasy.RuntimeLevelHandle::delete); }
        helper.succeed();
    }
}
