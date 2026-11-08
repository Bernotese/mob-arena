package dev.mobarena.gametest;

import dev.mobarena.core.*;
import dev.mobarena.hud.Titles;
import dev.mobarena.result.ResultKeys;
import dev.mobarena.team.TeamId;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.world.phys.Vec3;
import java.util.*;

public class PolishGameTest {
    @GameTest(environment = "mobarena:polish_title", maxTicks = 400)
    public void resultTitleWaitsTwoSeconds(GameTestHelper h) {
        var f = new FullGameFixture(h, true, 1);
        var packets = new ArrayList<Object>();
        var viewer = TestPlayers.recordingPackets(h, packets);
        f.players.add(viewer);
        packets.clear();
        Titles.show(viewer, Component.literal("All waves cleared"), Component.empty(), 0, 60, 10);
        f.ctx.put(ResultKeys.RESULT, new ResultKeys.GameResult(Optional.of(TeamId.RED), "All waves cleared"));
        f.game.finish();
        h.assertTrue(titles(packets).equals(List.of("All waves cleared")), "Team title remains on END entry");
        h.startSequence().thenIdle(39).thenExecute(f.check(() ->
            h.assertTrue(titles(packets).equals(List.of("All waves cleared")), "Result title waits at least 39 ticks")))
            .thenIdle(2).thenExecute(f.check(() ->
                h.assertTrue(titles(packets).equals(List.of("All waves cleared", "Team RED wins")),
                        "Result title appears once after two seconds")))
            .thenIdle(10).thenExecute(f.check(() ->
                h.assertTrue(titles(packets).size() == 2, "Result title is not repeated")))
            .thenExecute(f::close).thenSucceed();
    }

    @GameTest(environment = "mobarena:polish_cancel_title", maxTicks = 400)
    public void stoppedEndDoesNotSendDelayedTitle(GameTestHelper h) {
        var f = new FullGameFixture(h, true, 1);
        var packets = new ArrayList<Object>();
        var viewer = TestPlayers.recordingPackets(h, packets);
        f.players.add(viewer);
        f.game.finish();
        f.game.stop();
        packets.clear();
        h.startSequence().thenIdle(45).thenExecute(f.check(() ->
            h.assertTrue(titles(packets).isEmpty(), "Stopped END sends no delayed title")))
            .thenExecute(f::close).thenSucceed();
    }

    @GameTest(environment = "mobarena:polish_lobby", maxTicks = 400)
    public void lobbyPlayersUseSeparateSafeBlocks(GameTestHelper h) {
        var f = new FullGameFixture(h, true);
        f.game.stop();
        try {
            var directory = java.nio.file.Files.createTempDirectory("mobarena-spread");
            var path = directory.resolve("lobby.json");
            TestEnd.run(h, () -> {
                try { java.nio.file.Files.deleteIfExists(path); java.nio.file.Files.deleteIfExists(directory); }
                catch (java.io.IOException e) { throw new IllegalStateException(e); }
            });
            var lobby = new LobbyManager(path);
            // A new config builds a known platform, even when a previous run's world was deleted.
            lobby.prepare(f.server);
            var saved = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(path))
                    .getAsJsonObject().getAsJsonObject("spawn");
            var center = new Vec3(saved.get("x").getAsDouble(), saved.get("y").getAsDouble(), saved.get("z").getAsDouble());
            var positions = new HashSet<Vec3>();
            for (var player : f.players) {
                lobby.sendToLobby(player, false);
                h.assertTrue(player.level() == f.server.overworld(), "Player returns to the lobby");
                for (var position : positions)
                    h.assertTrue(player.position().distanceToSqr(position) >= 4.0, "Lobby players stand at least two blocks apart");
                h.assertTrue(positions.add(player.position()), "Each player has a separate lobby position");
                var offset = player.position().subtract(center);
                h.assertTrue(Math.abs(offset.x) <= 4 && Math.abs(offset.z) <= 4 && Math.abs(offset.y) < 0.001,
                        "Lobby candidates stay inside the platform interior");
                h.assertTrue(offset.x % 2 == 0 && offset.z % 2 == 0, "Lobby candidates use a two-block grid");
                h.assertTrue(player.verticalCollisionBelow, "Lobby position has support");
                h.assertTrue(player.level().noCollision(player, player.getBoundingBox()), "Lobby position has enough space");
            }
            f.red.teleportTo(f.server.overworld(), center.x, center.y, center.z, Set.of(), 0, 0, true);
            lobby.setSpawn(f.red);
            // Keep the saved spawn supported so prepare does not rebuild the platform.
            // Occupy the center and remove the first spread candidate instead.
            f.redMate.teleportTo(f.red.getX() + 20, f.red.getY(), f.red.getZ());
            f.blueMate.teleportTo(f.red.getX() + 22, f.red.getY(), f.red.getZ());
            f.blue.teleportTo(f.red.getX(), f.red.getY(), f.red.getZ());
            var hole = f.red.blockPosition().below().offset(-2, 0, -2);
            var holePosition = Vec3.atBottomCenterOf(hole.above());
            h.assertTrue(f.server.getPlayerList().getPlayers().stream().noneMatch(player ->
                    player.level() == f.server.overworld() && player.position().distanceToSqr(holePosition) < 4.0),
                    "The first spread candidate is unoccupied before removing its floor");
            var old = f.server.overworld().getBlockState(hole);
            h.assertTrue(!old.getCollisionShape(f.server.overworld(), hole).isEmpty(),
                    "The first spread candidate has floor support before removing it");
            f.server.overworld().setBlockAndUpdate(hole, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            try {
                lobby.sendToLobby(f.red, false);
                h.assertTrue(f.server.overworld().getBlockState(hole).isAir(), "Prepare leaves the test hole open");
                h.assertTrue(!f.red.blockPosition().below().equals(hole), "Lobby spread avoids the floor hole");
                h.assertTrue(f.red.verticalCollisionBelow, "Alternate lobby block has support");
                h.assertTrue(f.red.position().distanceToSqr(f.blue.position()) >= 4.0,
                        "The alternate lobby block keeps two blocks from the occupied center");
            } finally { f.server.overworld().setBlockAndUpdate(hole, old); }
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
        f.close(); h.succeed();
    }

    private static List<String> titles(List<Object> packets) {
        return packets.stream().filter(ClientboundSetTitleTextPacket.class::isInstance)
                .map(ClientboundSetTitleTextPacket.class::cast).map(packet -> packet.text().getString()).toList();
    }
}
