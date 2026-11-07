package dev.mobarena.gametest;
import dev.mobarena.core.*;
import dev.mobarena.team.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.item.*;
import net.minecraft.server.permissions.PermissionSet;
public class CoreGameTest {
    @GameTest(environment = "mobarena:core_start", maxTicks = 12000)
    public void commandsStartAndHoldingAreaProtectsPlayers(GameTestHelper helper) throws Exception {
        var server = helper.getLevel().getServer();
        GameManager game = GameManager.get(); game.stop();
        ServerPlayer red = TestPlayers.create(helper);
        ServerPlayer blue = TestPlayers.create(helper);
        Runnable cleanup = () -> {
            game.preparer().cancel(server); game.stop(); TeamManager.get().remove(red); TeamManager.get().remove(blue);
            server.getPlayerList().remove(red); server.getPlayerList().remove(blue);
        };
        TestEnd.run(helper, cleanup);
        {
            TeamManager.get().assign(red, TeamId.RED); TeamManager.get().assign(blue, TeamId.BLUE);
            var dispatcher = server.getCommands().getDispatcher();
            var source = server.createCommandSourceStack().withPermission(PermissionSet.ALL_PERMISSIONS);
            helper.assertTrue(dispatcher.execute("ma start", source) == 1, "Start succeeds with two teams");
            helper.assertTrue(game.phase() == Phase.LOBBY && game.preparer().busy(), "Start prepares the worlds first");
            helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(game.phase() == Phase.PREP, "Start enters PREP when the worlds are ready"))
                .thenExecute(() -> { try {
                    helper.assertTrue(red.gameMode.getGameModeForPlayer() == GameType.SURVIVAL, "Prep uses survival");
                    helper.assertTrue(game.context().get(ResultHooks.KEY) != null, "Result hooks exist before modules run");
                    red.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 3));
                    dispatcher.execute("ma skip", source);
                    helper.assertTrue(game.phase() == Phase.WAVE_CONFIG, "Skip enters wave config");
                    helper.assertTrue(red.level() == server.overworld() && blue.level() == server.overworld(), "Both teams use lobby world");
                    float health = red.getHealth();
                    helper.assertTrue(red.getAbilities().invulnerable && blue.getAbilities().invulnerable, "Holding area is invulnerable");
                    helper.assertTrue(red.gameMode.getGameModeForPlayer() == GameType.ADVENTURE, "Holding area uses adventure");
                    helper.assertTrue(red.getInventory().getItem(0).getCount() == 3, "Holding area keeps inventory");
                    helper.assertTrue(!red.hurtServer(red.level(), red.damageSources().generic(), 5), "Holding area blocks damage");
                    helper.assertTrue(red.getHealth() == health, "Holding area keeps health");
                    helper.assertTrue(dispatcher.execute("ma status", red.createCommandSourceStack()) == 1, "Player can read status");
                    helper.assertTrue(dispatcher.execute("ma stop", source) == 1, "Stop succeeds");
                    helper.assertTrue(game.phase() == Phase.LOBBY && game.context() == null, "Stop clears game state");
                    helper.assertTrue(red.getInventory().isEmpty(), "Stop clears inventory");
                    helper.assertTrue(red.experienceLevel == 0 && red.totalExperience == 0 && red.experienceProgress == 0, "Stop clears experience");
                    helper.assertTrue(red.gameMode.getGameModeForPlayer() == GameType.ADVENTURE, "Lobby uses adventure");
                } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) { throw new AssertionError(e); } })
                .thenSucceed();
        }
    }
}
