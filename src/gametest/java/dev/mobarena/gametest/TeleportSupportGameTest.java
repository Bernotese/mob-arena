package dev.mobarena.gametest;

import dev.mobarena.core.LobbyManager;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import java.util.Set;

/** A teleport to another level must not keep the support block of the old level. */
public class TeleportSupportGameTest {
    @GameTest(environment = "mobarena:teleport_support")
    public void teleportClearsOldSupportBlock(GameTestHelper h) throws Exception {
        var server = h.getLevel().getServer();
        var nether = server.getLevel(Level.NETHER);
        var player = TestPlayers.create(h);
        var dir = java.nio.file.Files.createTempDirectory("mobarena-lobby-test");
        var path = dir.resolve("mob-arena-lobby.json");
        TestEnd.run(h, () -> {
            server.getPlayerList().remove(player);
            try { java.nio.file.Files.deleteIfExists(path); java.nio.file.Files.deleteIfExists(dir); } catch (java.io.IOException ignored) {}
        });
        var stone = new BlockPos(0, 120, 0);
        nether.setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());
        Runnable standOnStone = () -> {
            player.teleportTo(nether, 0.5, 121, 0.5, Set.of(), 0, 0, true);
            player.setOnGroundWithMovement(true, Vec3.ZERO);
            h.assertValueEqual(player.mainSupportingBlockPos.orElse(null), stone, "Player stands on the Nether stone");
        };
        // Vanilla keeps the old support block after a teleport to another level.
        standOnStone.run();
        player.teleportTo(server.overworld(), 0.5, 200, 0.5, Set.of(), 0, 0, true);
        h.assertTrue(player.level() == server.overworld(), "Vanilla teleport changes the level");
        h.assertValueEqual(player.mainSupportingBlockPos.orElse(null), stone, "Vanilla keeps the Nether support block");
        // The mod's lobby return clears it.
        standOnStone.run();
        new LobbyManager(path).sendToLobby(player, false);
        h.assertTrue(player.level() == server.overworld(), "Lobby return changes the level");
        h.assertTrue(player.mainSupportingBlockPos.isEmpty(), "Lobby return clears the old support block");
        h.succeed();
    }
}
