package dev.mobarena.world;

import dev.mobarena.core.GameManager;
import dev.mobarena.core.Phase;
import dev.mobarena.gametest.TestPlayers;
import dev.mobarena.team.TeamId;
import dev.mobarena.team.TeamManager;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;

/** /ma start prepares the prep worlds over several ticks before PREP. */
public class StartPrepGameTest {
    private record Setup(GameManager game, ServerPlayer red, ServerPlayer blue, Runnable cleanup) {
        void command(GameTestHelper h, String command) {
            var server = h.getLevel().getServer();
            try {
                h.assertTrue(server.getCommands().getDispatcher().execute(command,
                        server.createCommandSourceStack().withPermission(PermissionSet.ALL_PERMISSIONS)) == 1, command + " succeeds");
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) { throw new AssertionError(e); }
        }
    }

    private static Setup setup(GameTestHelper h) {
        var server = h.getLevel().getServer();
        var game = GameManager.get();
        game.preparer().cancel(server); game.stop();
        var red = TestPlayers.create(h); var blue = TestPlayers.create(h);
        TeamManager.get().assign(red, TeamId.RED); TeamManager.get().assign(blue, TeamId.BLUE);
        return new Setup(game, red, blue, () -> {
            game.preparer().cancel(server); game.stop();
            TeamManager.get().remove(red); TeamManager.get().remove(blue);
            server.getPlayerList().remove(red); server.getPlayerList().remove(blue);
        });
    }

    private static boolean noGameLevels(GameTestHelper h) {
        for (var level : h.getLevel().getServer().getAllLevels())
            if ("mobarena".equals(level.dimension().identifier().getNamespace())) return false;
        return true;
    }

    @GameTest(environment = "mobarena:start_prep", maxTicks = 12000)
    public void asyncStartSharesSpawnColumn(GameTestHelper h) {
        var s = setup(h);
        try {
            s.command(h, "ma start");
            h.assertTrue(s.game().phase() == Phase.LOBBY && s.game().preparer().busy(), "The command returns before PREP");
            h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(s.game().phase() == Phase.PREP, "PREP starts when the worlds are ready"))
                .thenExecute(() -> {
                    h.assertFalse(s.game().preparer().busy(), "Preparation is done");
                    var worlds = s.game().context().get(TeamWorlds.KEY);
                    var red = worlds.get(TeamId.RED); var blue = worlds.get(TeamId.BLUE);
                    h.assertTrue(red.prepSpawn().getX() == blue.prepSpawn().getX() && red.prepSpawn().getZ() == blue.prepSpawn().getZ(),
                            "RED and BLUE share the spawn X/Z: " + red.prepSpawn() + " " + blue.prepSpawn());
                    // Same ring search as the blocking path.
                    h.assertValueEqual(TeamSpawnFinder.find(red.overworld().asLevel()), red.prepSpawn(), "Async spawn equals the blocking search");
                    h.assertTrue(s.red().level() == red.overworld().asLevel() && s.blue().level() == blue.overworld().asLevel(),
                            "Players are in their own prep overworld");
                    h.assertTrue(s.red().blockPosition().getX() == red.prepSpawn().getX() && s.red().blockPosition().getZ() == red.prepSpawn().getZ(),
                            "RED stands at the spawn");
                    s.command(h, "ma stop");
                })
                .thenWaitUntil(() -> h.assertTrue(noGameLevels(h), "Stop deletes the game levels"))
                .thenExecute(s.cleanup()).thenSucceed();
        } catch (RuntimeException | Error e) { s.cleanup().run(); throw e; }
    }

    @GameTest(environment = "mobarena:start_prep_cancel", maxTicks = 12000)
    public void stopCancelsPreparation(GameTestHelper h) {
        var s = setup(h);
        try {
            s.command(h, "ma start");
            h.assertTrue(s.game().preparer().busy(), "Preparation runs");
            s.command(h, "ma stop");
            h.assertFalse(s.game().preparer().busy(), "Stop cancels the preparation");
            h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(noGameLevels(h), "Cancel deletes the prepared levels"))
                .thenIdle(100)
                .thenExecute(() -> h.assertTrue(s.game().phase() == Phase.LOBBY && s.game().context() == null
                        && noGameLevels(h), "A canceled preparation does not start a game later"))
                .thenExecute(s.cleanup()).thenSucceed();
        } catch (RuntimeException | Error e) { s.cleanup().run(); throw e; }
    }
}
