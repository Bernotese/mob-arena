package dev.mobarena.world;

import dev.mobarena.core.GameManager;
import dev.mobarena.core.Phase;
import dev.mobarena.gametest.TestEnd;
import dev.mobarena.gametest.TestPlayers;
import dev.mobarena.team.TeamId;
import dev.mobarena.team.TeamManager;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.TicketStorage;

/** /ma start prepares the prep worlds over several ticks before PREP. */
public class StartPrepGameTest {
    private record Setup(GameManager game, ServerPlayer red, ServerPlayer blue) {
        void command(GameTestHelper h, String command) {
            var server = h.getLevel().getServer();
            try {
                h.assertTrue(server.getCommands().getDispatcher().execute(command,
                        server.createCommandSourceStack().withPermission(PermissionSet.ALL_PERMISSIONS)) == 1, command + " succeeds");
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) { throw new AssertionError(e); }
        }
    }

    /** Two team players; at the end (pass or fail) no preparation, game, or player is left. */
    private static Setup setup(GameTestHelper h) {
        var server = h.getLevel().getServer();
        var game = GameManager.get();
        game.preparer().cancel(server);
        TestEnd.stopStaleGame();
        var red = TestPlayers.create(h); var blue = TestPlayers.create(h);
        TeamManager.get().assign(red, TeamId.RED); TeamManager.get().assign(blue, TeamId.BLUE);
        TestEnd.run(h, () -> {
            game.preparer().cancel(server);
            TestEnd.stopStaleGame();
            TeamManager.get().remove(red); TeamManager.get().remove(blue);
            server.getPlayerList().remove(red); server.getPlayerList().remove(blue);
        });
        return new Setup(game, red, blue);
    }

    private static boolean noGameLevels(GameTestHelper h) {
        for (var level : h.getLevel().getServer().getAllLevels())
            if ("mobarena".equals(level.dimension().identifier().getNamespace())) return false;
        return true;
    }

    /** Any start-preparation ticket in the searched area (rings up to 5 plus the spawn radius). */
    private static boolean hasPrepTickets(ServerLevel level) {
        var storage = level.getDataStorage().get(TicketStorage.TYPE);
        if (storage == null) return false;
        var origin = TeamSpawnFinder.origin(level);
        int r = TeamSpawnFinder.MAX_RADIUS + 3;
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            for (var ticket : storage.getTickets(ChunkPos.pack(origin.x() + dx, origin.z() + dz)))
                if (ticket.getType() == WorldPreparer.ticketType()) return true;
        }
        return false;
    }

    @GameTest(environment = "mobarena:start_prep", maxTicks = 12000)
    public void asyncStartSharesSpawnColumn(GameTestHelper h) {
        var s = setup(h);
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
            .thenSucceed();
    }

    /** R3: the start keeps its tickets for 200 ticks, then removes them. */
    @GameTest(environment = "mobarena:start_prep_tickets", maxTicks = 12000)
    public void startRemovesTicketsAfterDelay(GameTestHelper h) {
        var s = setup(h);
        s.command(h, "ma start");
        ServerLevel[] levels = new ServerLevel[2];
        long[] started = {0};
        h.startSequence()
            .thenWaitUntil(() -> h.assertTrue(s.game().phase() == Phase.PREP, "PREP starts"))
            .thenExecute(() -> {
                var worlds = s.game().context().get(TeamWorlds.KEY);
                levels[0] = worlds.get(TeamId.RED).overworld().asLevel();
                levels[1] = worlds.get(TeamId.BLUE).overworld().asLevel();
                started[0] = h.getTick();
                for (var level : levels) h.assertTrue(hasPrepTickets(level), "Tickets stay after the teleport");
            })
            .thenIdle(150)
            .thenExecute(() -> { for (var level : levels) h.assertTrue(hasPrepTickets(level), "Tickets stay for 150 ticks"); })
            .thenWaitUntil(() -> { for (var level : levels) h.assertFalse(hasPrepTickets(level), "Tickets are removed"); })
            .thenExecute(() -> h.assertTrue(h.getTick() - started[0] <= 230, "Tickets are removed about 200 ticks after the start: "
                    + (h.getTick() - started[0])))
            .thenSucceed();
    }

    /** R3: /ma stop during the preparation removes the tickets and the levels, and no game starts later. */
    @GameTest(environment = "mobarena:start_prep_cancel", maxTicks = 12000)
    public void stopCancelsPreparation(GameTestHelper h) {
        var s = setup(h);
        var server = h.getLevel().getServer();
        s.command(h, "ma start");
        h.assertTrue(s.game().preparer().busy(), "Preparation runs");
        var other = otherLevel(h);
        var red = GameLevels.level(TeamId.RED, WorldKind.PREP_OVERWORLD);
        h.assertTrue(red != null && hasPrepTickets(red), "The preparation holds tickets");
        s.command(h, "ma stop");
        h.assertFalse(s.game().preparer().busy(), "Stop cancels the preparation");
        h.assertFalse(hasPrepTickets(red), "Cancel removes the tickets at once");
        h.startSequence()
            .thenWaitUntil(() -> h.assertTrue(server.getLevel(red.dimension()) == null, "Cancel deletes the prepared levels"))
            .thenIdle(100)
            .thenExecute(() -> {
                h.assertTrue(s.game().phase() == Phase.LOBBY && s.game().context() == null, "A canceled preparation does not start a game later");
                h.assertTrue(server.getLevel(other.asLevel().dimension()) == other.asLevel(), "Cancel keeps the other game level");
                for (var level : server.getAllLevels())
                    if ("mobarena".equals(level.dimension().identifier().getNamespace()))
                        h.assertTrue(level == other.asLevel(), "Only the other game level is left: " + level.dimension().identifier());
            })
            .thenSucceed();
    }

    /** A tracked game level that is not part of the preparation; deleted at the test end. */
    private static xyz.nucleoid.fantasy.RuntimeLevelHandle otherLevel(GameTestHelper h) {
        var server = h.getLevel().getServer();
        var other = LevelFactory.open(server, 7, TeamId.RED, WorldKind.ARENA);
        TestEnd.run(h, () -> WorldCleanup.delete(server, java.util.List.of(other)));
        return other;
    }

    /** R1: a refused start deletes only the levels of its own preparation. */
    @GameTest(environment = "mobarena:start_prep_refused", maxTicks = 12000)
    public void refusedStartDeletesOnlyOwnLevels(GameTestHelper h) {
        var s = setup(h);
        var server = h.getLevel().getServer();
        s.command(h, "ma start");
        // Another tracked game level, opened after the start: the preparation must not delete it.
        var other = otherLevel(h);
        var prepRed = GameLevels.level(TeamId.RED, WorldKind.PREP_OVERWORLD);
        // BLUE has no player when the worlds are ready: the start is refused.
        TeamManager.get().remove(s.blue());
        h.startSequence()
            .thenWaitUntil(() -> h.assertFalse(s.game().preparer().busy(), "Preparation ends"))
            .thenExecute(() -> h.assertTrue(s.game().phase() == Phase.LOBBY && s.game().context() == null, "The start is refused"))
            .thenWaitUntil(() -> h.assertTrue(server.getLevel(prepRed.dimension()) == null, "The prepared levels are deleted"))
            .thenIdle(20)
            .thenExecute(() -> h.assertTrue(server.getLevel(other.asLevel().dimension()) == other.asLevel(), "The other game level stays"))
            .thenSucceed();
    }
}
