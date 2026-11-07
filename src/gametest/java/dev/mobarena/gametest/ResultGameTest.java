package dev.mobarena.gametest;

import dev.mobarena.core.*;
import dev.mobarena.hud.HudOverrides;
import dev.mobarena.result.ResultKeys;
import dev.mobarena.result.ResultModule;
import dev.mobarena.result.ResultTracker;
import dev.mobarena.result.SuddenDeathStarter;
import dev.mobarena.result.Outcome;
import dev.mobarena.team.TeamId;
import dev.mobarena.team.TeamManager;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/**
 * T8 result flow. Each test waits for server ticks on the GameManager singleton, so each test has its
 * own test environment (= own batch, see data/mobarena/test_environment).
 */
public class ResultGameTest {
    /** Two online test players in a running game, moved to ARENA. */
    private static final class Fixture {
        final GameTestHelper helper;
        final ServerPlayer red, blue;
        final GameContext ctx;
        private SuddenDeathStarter previousStarter;
        Fixture(GameTestHelper helper) {
            this.helper = helper;
            GameManager game = GameManager.get();
            game.stop();
            red = TestPlayers.create(helper);
            blue = TestPlayers.create(helper);
            TeamManager.get().assign(red, TeamId.RED);
            TeamManager.get().assign(blue, TeamId.BLUE);
            helper.assertTrue(game.start(helper.getLevel().getServer(),
                    Map.of(red.getUUID(), TeamId.RED, blue.getUUID(), TeamId.BLUE),
                    Set.of(red.getUUID(), blue.getUUID())), "Game starts");
            game.endPhaseNow();
            game.endPhaseNow();
            helper.assertTrue(game.phase() == Phase.ARENA, "Fixture reaches ARENA");
            ctx = game.context();
        }
        ResultHooks hooks() { return ctx.get(ResultHooks.KEY); }
        ResultTracker tracker() { return (ResultTracker) ctx.get(ResultHooks.KEY); }
        Phase phase() { return GameManager.get().phase(); }
        /** Replaces the static sudden death hook until {@link #close()}. */
        void useStarter(SuddenDeathStarter starter) {
            if (previousStarter == null) previousStarter = ResultModule.suddenDeathStarter();
            ResultModule.setSuddenDeathStarter(starter);
        }
        /** Moves a player into the loaded test area (relative position). */
        void place(ServerPlayer player, double x, double y, double z) {
            Vec3 pos = helper.absoluteVec(new Vec3(x, y, z));
            player.teleportTo(helper.getLevel(), pos.x, pos.y, pos.z, Set.of(), 0, 0, true);
        }
        /** Runs a check; on failure the game is stopped first, so later batches start clean. */
        void at(long delay, Runnable check) {
            helper.runAfterDelay(delay, () -> {
                try { check.run(); } catch (RuntimeException | Error e) { close(); throw e; }
            });
        }
        void close() {
            if (previousStarter != null) { ResultModule.setSuddenDeathStarter(previousStarter); previousStarter = null; }
            GameManager.get().stop();
            TeamManager.get().remove(red);
            TeamManager.get().remove(blue);
            var players = helper.getLevel().getServer().getPlayerList();
            players.remove(red);
            players.remove(blue);
        }
        int fireworksNear(ServerPlayer player) {
            return player.level().getEntitiesOfClass(FireworkRocketEntity.class, player.getBoundingBox().inflate(6)).size();
        }
    }

    private static TeamProgress progress(int waves, int kills) { return new TeamProgress(waves, kills, 200); }

    @GameTest(environment = "mobarena:result_same_tick", maxTicks = 40)
    public void sameTickEventsGiveOneVerdictAtEndServerTick(GameTestHelper helper) {
        var f = new Fixture(helper);
        f.hooks().teamFinished(TeamId.RED, 100);
        f.hooks().teamEliminated(TeamId.BLUE, progress(0, 0));
        helper.assertTrue(f.phase() == Phase.ARENA, "Hooks only record; the resolver runs at END_SERVER_TICK");
        helper.assertTrue(f.tracker().dirty(), "Events are recorded");
        f.at(1, () -> {
            helper.assertTrue(f.phase() == Phase.END, "One verdict in the same tick: END");
            var result = f.ctx.get(ResultKeys.RESULT);
            helper.assertTrue(result != null && result.winner().equals(Optional.of(TeamId.RED)), "RED wins");
            helper.assertTrue(result.reason().equals("All waves cleared"), "Reason: " + result.reason());
            helper.assertTrue(HudOverrides.of(f.ctx).result().text().getString().equals("Team RED wins"), "HUD result");
            f.hooks().teamFinished(TeamId.BLUE, 300);
            helper.assertTrue(!f.tracker().dirty(), "Events after the verdict are ignored");
            f.close();
            helper.succeed();
        });
    }

    @GameTest(environment = "mobarena:result_sudden_death", maxTicks = 40)
    public void equalEliminationsStartSuddenDeath(GameTestHelper helper) {
        var f = new Fixture(helper);
        int[] started = {0};
        f.useStarter((ctx, hooks) -> started[0]++);
        f.hooks().teamEliminated(TeamId.RED, progress(1, 5));
        f.hooks().teamEliminated(TeamId.BLUE, progress(1, 5));
        f.at(1, () -> {
            helper.assertTrue(f.phase() == Phase.SUDDEN_DEATH, "Equal progress -> SUDDEN_DEATH, phase " + f.phase());
            helper.assertTrue(started[0] == 1, "Sudden death hook runs once");
            helper.assertTrue(f.tracker().suddenDeathOutcome(TeamId.RED).orElseThrow() instanceof Outcome.Running,
                    "Both teams run again");
            helper.assertTrue(f.ctx.get(ResultKeys.RESULT) == null, "No result yet");
            f.hooks().teamFinished(TeamId.RED, 250);
        });
        f.at(2, () -> {
            helper.assertTrue(f.phase() == Phase.END, "First clear in sudden death ends the game");
            var result = f.ctx.get(ResultKeys.RESULT);
            helper.assertTrue(result.winner().equals(Optional.of(TeamId.RED)), "RED wins sudden death");
            helper.assertTrue(result.reason().equals("Sudden death: All waves cleared"), "Reason: " + result.reason());
            f.close();
            helper.succeed();
        });
    }

    @GameTest(environment = "mobarena:result_end_timing", maxTicks = 400)
    public void endSequenceFireworksAndLobbyAfterEndScreenSeconds(GameTestHelper helper) {
        var f = new Fixture(helper);
        long endTicks = f.ctx.config().endScreenSeconds() * 20L;
        helper.assertTrue(endTicks + 2 < 400, "Test needs endScreenSeconds below 19");
        f.red.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 2));
        f.hooks().teamFinished(TeamId.RED, 100);
        f.hooks().teamEliminated(TeamId.BLUE, progress(2, 4));
        f.at(1, () -> {
            helper.assertTrue(f.phase() == Phase.END, "Verdict enters END");
            // The lobby moved both players to the lobby spawn; its chunk is not loaded in tests.
            // This tick runs before the first END tick, so the first fireworks start here.
            f.place(f.red, 1.5, 2, 1.5);
            f.place(f.blue, 1.5, 22, 1.5);
            helper.assertTrue(f.blue.distanceTo(f.red) > 12, "Loser is far from the winner");
        });
        f.at(3, () -> {
            helper.assertTrue(f.fireworksNear(f.red) > 0, "Fireworks over the winner");
            helper.assertTrue(f.fireworksNear(f.blue) == 0, "No fireworks over the loser");
            helper.assertTrue(f.red.getInventory().getItem(0).is(Items.DIAMOND), "T8 does not clear inventories");
        });
        // END starts at the END_SERVER_TICK of tick 0; GameManager.tick of tick endTicks ends it.
        f.at(endTicks, () -> helper.assertTrue(f.phase() == Phase.END, "Still END before endScreenSeconds end"));
        f.at(endTicks + 1, () -> {
            helper.assertTrue(f.phase() == Phase.LOBBY, "LOBBY after endScreenSeconds, phase " + f.phase());
            f.close();
            helper.succeed();
        });
    }

    @GameTest(environment = "mobarena:result_admin_skip", maxTicks = 40)
    public void adminSkipToEndShowsGameOver(GameTestHelper helper) {
        var f = new Fixture(helper);
        try {
            GameManager.get().endPhaseNow();
            helper.assertTrue(f.phase() == Phase.END, "Skip from ARENA enters END");
            helper.assertTrue(f.ctx.get(ResultKeys.RESULT) == null, "No verdict, no result");
            helper.assertTrue(HudOverrides.of(f.ctx).result().text().getString().equals("Game over"),
                    "Boss bar shows the same text as the title");
        } finally {
            f.close();
        }
        helper.succeed();
    }

    @GameTest(environment = "mobarena:result_draw", maxTicks = 40)
    public void equalSuddenDeathIsDrawWithoutFireworks(GameTestHelper helper) {
        var f = new Fixture(helper);
        f.useStarter((ctx, hooks) -> { });
        f.hooks().teamFinished(TeamId.RED, 100);
        f.hooks().teamFinished(TeamId.BLUE, 900);
        f.at(1, () -> {
            helper.assertTrue(f.phase() == Phase.SUDDEN_DEATH, "Two finishes -> SUDDEN_DEATH (time ignored)");
            f.hooks().teamEliminated(TeamId.RED, progress(0, 8));
            f.hooks().teamEliminated(TeamId.BLUE, progress(0, 8));
        });
        f.at(4, () -> {
            helper.assertTrue(f.phase() == Phase.END, "Equal kills end the game");
            var result = f.ctx.get(ResultKeys.RESULT);
            helper.assertTrue(result.winner().isEmpty(), "Draw has no winner");
            helper.assertTrue(result.reason().equals("Sudden death: equal result"), "Reason: " + result.reason());
            helper.assertTrue(HudOverrides.of(f.ctx).result().text().getString().equals("Draw"), "HUD shows Draw");
            helper.assertTrue(f.fireworksNear(f.red) == 0 && f.fireworksNear(f.blue) == 0, "No fireworks for a draw");
            f.close();
            helper.succeed();
        });
    }
}
