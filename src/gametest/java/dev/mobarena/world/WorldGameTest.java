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
            GameLevels.remove(handle.asLevel());
            handle.delete();
        }
        helper.succeed();
    }
}
