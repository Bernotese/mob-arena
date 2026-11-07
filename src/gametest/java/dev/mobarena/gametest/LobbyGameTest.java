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
            helper.succeed();
        } finally { server.getPlayerList().deop(profile); cleanup(player); Files.deleteIfExists(path); Files.deleteIfExists(path.getParent()); }
    }
}
