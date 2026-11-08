package dev.mobarena.gametest;

import dev.mobarena.core.*;
import dev.mobarena.team.TeamId;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.world.level.GameType;
import net.minecraft.world.item.*;
import java.nio.file.*;
import java.util.*;

public class LobbyGameTest {
    private static void start(ServerPlayer red, ServerPlayer blue) {
        var game = GameManager.get(); game.stop();
        game.start(red.level().getServer(), Map.of(red.getUUID(), TeamId.RED, blue.getUUID(), TeamId.BLUE), Set.of(red.getUUID(), blue.getUUID()));
    }
    private static void cleanup(ServerPlayer... players) {
        GameManager.get().stop();
        for (ServerPlayer player : players) player.level().getServer().getPlayerList().remove(player);
    }
    @GameTest
    public void savedLobbySpawnRebuildsMissingPlatformAndSupportsPlayer(GameTestHelper helper) throws Exception {
        var player = TestPlayers.create(helper);
        var server = helper.getLevel().getServer();
        var level = server.overworld();
        Path path = Files.createTempDirectory("mobarena-platform-test").resolve("lobby.json");
        try {
            var spawn = helper.absolutePos(new net.minecraft.core.BlockPos(2, 100, 2));
            var floor = spawn.below();
            player.teleportTo(level, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, Set.of(), 35, 12, true);
            var original = new LobbyManager(path);
            original.setSpawn(player);
            original.prepare(server);
            helper.assertTrue(level.getBlockState(floor).is(net.minecraft.world.level.block.Blocks.STONE),
                    "Initial lobby platform exists at the saved spawn");
            for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++) for (int y = 0; y <= 3; y++)
                level.setBlockAndUpdate(floor.offset(x, y, z), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            String saved = Files.readString(path);
            var loaded = new LobbyManager(path);
            loaded.prepare(server);
            for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++)
                helper.assertTrue(level.getBlockState(floor.offset(x, 0, z)).is(net.minecraft.world.level.block.Blocks.STONE),
                        "Preparing a saved spawn rebuilds the entire platform floor");
            helper.assertTrue(level.getBlockState(floor.offset(5, 1, 0)).is(net.minecraft.world.level.block.Blocks.BARRIER),
                    "Preparing a saved spawn restores the platform barrier");
            helper.assertValueEqual(Files.readString(path), saved, "Platform repair keeps the saved lobby data");
            loaded.sendToLobby(player, false);
            player.hasChangedDimension();
            player.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
            // Embedded clients do not simulate gravity. Use the real entity collision movement to land.
            player.teleportTo(player.getX(), player.getY() + 2, player.getZ());
            player.move(net.minecraft.world.entity.MoverType.PLAYER, new net.minecraft.world.phys.Vec3(0, -4, 0));
            helper.assertTrue(player.onGround() && Math.abs(player.getY() - spawn.getY()) < 0.001,
                    "Player lands on the rebuilt platform at the saved spawn height");
            helper.assertTrue(player.isAlive() && player.getHealth() == player.getMaxHealth(), "Player lands safely");
            helper.succeed();
        } finally {
            server.getPlayerList().remove(player); player.discard();
            Files.deleteIfExists(path); Files.deleteIfExists(path.getParent());
        }
    }

    @GameTest
    public void savedCustomLobbyOnSolidGroundStaysUnchanged(GameTestHelper helper) throws Exception {
        var player = TestPlayers.create(helper);
        var server = helper.getLevel().getServer();
        var level = server.overworld();
        Path path = Files.createTempDirectory("mobarena-custom-lobby-test").resolve("lobby.json");
        try {
            var spawn = helper.absolutePos(new net.minecraft.core.BlockPos(2, 100, 2));
            var floor = spawn.below();
            var marker = floor.offset(4, 1, 0);
            level.setBlockAndUpdate(floor, net.minecraft.world.level.block.Blocks.GLASS.defaultBlockState());
            level.setBlockAndUpdate(floor.east(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(marker, net.minecraft.world.level.block.Blocks.GOLD_BLOCK.defaultBlockState());
            player.teleportTo(level, spawn.getX() + 0.25, spawn.getY(), spawn.getZ() + 0.75, Set.of(), 35, 12, true);
            new LobbyManager(path).setSpawn(player);
            String saved = Files.readString(path);
            var loaded = new LobbyManager(path);
            loaded.prepare(server);
            helper.assertTrue(level.getBlockState(floor).is(net.minecraft.world.level.block.Blocks.GLASS),
                    "Preparing a valid custom spawn preserves its solid ground");
            helper.assertTrue(level.getBlockState(marker).is(net.minecraft.world.level.block.Blocks.GOLD_BLOCK)
                    && level.getBlockState(floor.east()).isAir(), "Preparing a valid custom spawn preserves its surroundings");
            helper.assertValueEqual(Files.readString(path), saved, "Preparing a valid custom spawn keeps its saved data");
            loaded.sendToLobby(player, false);
            helper.assertTrue(Math.abs(player.getX() - spawn.getX() - 0.25) < 0.001
                    && Math.abs(player.getY() - spawn.getY()) < 0.001
                    && Math.abs(player.getZ() - spawn.getZ() - 0.75) < 0.001
                    && player.getYRot() == 35 && player.getXRot() == 12, "Custom lobby coordinates and rotation stay unchanged");
            helper.succeed();
        } finally {
            server.getPlayerList().remove(player); player.discard();
            Files.deleteIfExists(path); Files.deleteIfExists(path.getParent());
        }
    }

    @GameTest
    public void savedCustomLobbyOnBottomSlabOverAirStaysUnchanged(GameTestHelper helper) throws Exception {
        var player = TestPlayers.create(helper);
        var second = TestPlayers.create(helper);
        var server = helper.getLevel().getServer();
        var level = server.overworld();
        Path path = Files.createTempDirectory("mobarena-slab-lobby-test").resolve("lobby.json");
        try {
            var floor = helper.absolutePos(new net.minecraft.core.BlockPos(2, 100, 2));
            var slab = net.minecraft.world.level.block.Blocks.STONE_SLAB.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.SLAB_TYPE,
                            net.minecraft.world.level.block.state.properties.SlabType.BOTTOM);
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
                var tile = floor.offset(x, 0, z);
                level.setBlockAndUpdate(tile.below(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                level.setBlockAndUpdate(tile, slab);
                level.setBlockAndUpdate(tile.above(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                level.setBlockAndUpdate(tile.above(2), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            }
            player.teleportTo(level, floor.getX() + 0.5, floor.getY() + 0.5, floor.getZ() + 0.5, Set.of(), 35, 12, true);
            new LobbyManager(path).setSpawn(player);
            String saved = Files.readString(path);
            var loaded = new LobbyManager(path);
            loaded.prepare(server);
            helper.assertValueEqual(level.getBlockState(floor), slab, "Preparing a custom slab spawn preserves its bottom slab");
            helper.assertTrue(level.getBlockState(floor.below()).isAir(), "Preparing a custom slab spawn preserves the air below");
            helper.assertValueEqual(Files.readString(path), saved, "Preparing a custom slab spawn keeps its saved data");
            loaded.sendToLobby(player, false);
            loaded.sendToLobby(second, false);
            helper.assertTrue(player.position().distanceToSqr(second.position()) >= 0.81,
                    "Players spread to separate positions on the bottom-slab floor");
            helper.assertTrue(second.verticalCollisionBelow && Math.abs(second.getY() - floor.getY() - 0.5) < 0.001,
                    "The spread position has slab support at the half-block height");
            helper.assertTrue(Math.abs(player.getY() - floor.getY() - 0.5) < 0.001, "Lobby return keeps the half-block spawn height");
            player.hasChangedDimension();
            player.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
            player.teleportTo(player.getX(), player.getY() + 1, player.getZ());
            player.move(net.minecraft.world.entity.MoverType.PLAYER, new net.minecraft.world.phys.Vec3(0, -2, 0));
            helper.assertTrue(player.onGround() && Math.abs(player.getY() - floor.getY() - 0.5) < 0.001,
                    "Player lands safely on the preserved bottom slab");
            helper.succeed();
        } finally {
            server.getPlayerList().remove(player); player.discard();
            server.getPlayerList().remove(second); second.discard();
            Files.deleteIfExists(path); Files.deleteIfExists(path.getParent());
        }
    }

    @GameTest
    public void shutdownClearsOnlinePlayersAndSavesOfflineCleanup(GameTestHelper helper) throws Exception {
        ServerPlayer red = TestPlayers.create(helper), blue = TestPlayers.create(helper);
        var server = helper.getLevel().getServer();
        Path path = Files.createTempDirectory("mobarena-lobby-test").resolve("mob-arena-lobby.json");
        LobbyManager lobby = new LobbyManager(path);
        try {
            start(red, blue); lobby.prepare(server);
            red.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 5));
            red.setExperienceLevels(12); red.setExperiencePoints(3);
            blue.getInventory().setItem(0, new ItemStack(Items.EMERALD, 2));
            server.getPlayerList().remove(blue);
            int[] reset = {0}; lobby.registerResetHook(s -> reset[0]++);
            lobby.shutdown(server);
            helper.assertTrue(red.getInventory().isEmpty() && red.experienceLevel == 0 && red.totalExperience == 0, "Shutdown clears online game inventory and XP");
            helper.assertTrue(red.level() == server.overworld() && red.gameMode.getGameModeForPlayer() == GameType.ADVENTURE, "Shutdown returns players to the lobby");
            helper.assertTrue(GameManager.get().context() == null && reset[0] == 1, "Shutdown clears state and runs hooks");
            helper.assertTrue(Files.readString(path).contains(blue.getUUID().toString()), "Offline cleanup UUID is saved");
            LobbyManager loaded = new LobbyManager(path); loaded.join(blue);
            helper.assertTrue(blue.getInventory().isEmpty(), "Offline player inventory clears on lobby join");
            helper.assertTrue(!Files.readString(path).contains(blue.getUUID().toString()), "Completed cleanup UUID is removed");
            helper.succeed();
        } finally { cleanup(red, blue); Files.deleteIfExists(path); Files.deleteIfExists(path.getParent()); }
    }
    @GameTest
    public void holdingDisconnectDoesNotRestoreHoldingPositionInArena(GameTestHelper helper) throws Exception {
        ServerPlayer red = TestPlayers.create(helper), blue = TestPlayers.create(helper);
        var server = helper.getLevel().getServer();
        Path path = Files.createTempDirectory("mobarena-rejoin-test").resolve("lobby.json");
        LobbyManager lobby = new LobbyManager(path);
        try {
            start(red, blue); var ctx = GameManager.get().context();
            red.teleportTo(server.overworld(), red.getX() + 40, 140, red.getZ() + 40,
                    Set.of(), red.getYRot(), red.getXRot(), true); lobby.setSpawn(red);
            double holdingX = red.getX(); lobby.leave(red); // PREP return must also be discarded.
            GameManager.get().endPhaseNow(); lobby.onEnter(Phase.WAVE_CONFIG, ctx); lobby.leave(red);
            GameManager.get().endPhaseNow(); lobby.onEnter(Phase.ARENA, ctx);
            ctx.setArenaLevel(TeamId.RED, server.overworld());
            int[] rejoins = {0}; lobby.registerRejoinHook(Phase.ARENA, (c, player) -> rejoins[0]++);
            lobby.join(red);
            helper.assertTrue(Math.abs(red.getX() - holdingX) > 1, "Arena rejoin does not restore the holding area");
            helper.assertTrue(red.gameMode.getGameModeForPlayer() == GameType.SPECTATOR && rejoins[0] == 1, "Arena rejoin starts as spectator and calls the timer hook");
            helper.succeed();
        } finally { cleanup(red, blue); Files.deleteIfExists(path); Files.deleteIfExists(path.getParent()); }
    }
    @GameTest
    public void lobbyRescueStaysNearPlatformAndSkipsAdminFlight(GameTestHelper helper) throws Exception {
        ServerPlayer player = TestPlayers.create(helper);
        Path path = Files.createTempDirectory("mobarena-rescue-test").resolve("lobby.json");
        LobbyManager lobby = new LobbyManager(path); var server = helper.getLevel().getServer();
        NameAndId profile = new NameAndId(player.getGameProfile());
        try {
            player.teleportTo(player.getX(), 150, player.getZ()); lobby.setSpawn(player);
            double x = player.getX(), z = player.getZ();
            player.setGameMode(GameType.ADVENTURE); player.teleportTo(x, 120, z);
            helper.assertTrue(lobby.needsRescue(player), "Players below the nearby platform need rescue");
            player.teleportTo(x + 30, 120, z);
            helper.assertTrue(!lobby.needsRescue(player), "Players away from the platform are not teleported");
            player.teleportTo(x, 120, z);
            server.getPlayerList().op(profile, Optional.of(LevelBasedPermissionSet.GAMEMASTER), Optional.empty());
            player.setGameMode(GameType.CREATIVE);
            helper.assertTrue(!lobby.needsRescue(player), "Creative admins can move below the platform");
            player.setGameMode(GameType.SPECTATOR);
            helper.assertTrue(!lobby.needsRescue(player), "Spectator admins can move below the platform");
            server.getPlayerList().deop(profile); player.setGameMode(GameType.ADVENTURE);
            var nether = server.getLevel(net.minecraft.world.level.Level.NETHER);
            player.teleportTo(nether, x + 100, nether.getMinY() - 1, z, Set.of(), 0, 0, true);
            helper.assertTrue(lobby.needsRescue(player), "Protected players below another dimension need rescue");
            lobby.tick(server);
            helper.assertTrue(player.level() == server.overworld(), "Void rescue returns protected players from other dimensions");
            helper.succeed();
        } finally { server.getPlayerList().deop(profile); cleanup(player); Files.deleteIfExists(path); Files.deleteIfExists(path.getParent()); }
    }
}
