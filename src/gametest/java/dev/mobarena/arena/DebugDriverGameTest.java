package dev.mobarena.arena;

import dev.mobarena.collect.CollectKeys;
import dev.mobarena.config.ConfigStore;
import dev.mobarena.config.MobArenaConfig;
import dev.mobarena.core.GameManager;
import dev.mobarena.core.Phase;
import dev.mobarena.debug.DebugDriver;
import dev.mobarena.team.TeamId;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import java.io.IOException;
import java.util.*;

/** Tests real debug commands, including the alive-cap queue and delayed body removal. */
public class DebugDriverGameTest {
    private static final class Fixture implements AutoCloseable {
        final GameTestHelper h;
        final ConfigStore original;
        final ArenaFixture arena;
        private boolean closed;
        Fixture(GameTestHelper h) {
            this.h = h;
            var game = GameManager.get(); game.stop();
            try { original = new ConfigStore(FabricLoader.getInstance().getConfigDir().resolve("mob-arena.json")); }
            catch (IOException e) { throw new IllegalStateException(e); }
            var config = new MobArenaConfig();
            config.prepTimeSeconds = 60; config.waveConfigTimeSeconds = 30;
            config.waveCountdownSeconds = 3; config.waveCooldownSeconds = 3;
            config.waveMaxTimeSeconds = 60; config.suddenDeathMaxTimeSeconds = 60;
            config.arenaRespawnSeconds = 3; config.mobGlowAfterSeconds = 10;
            config.endScreenSeconds = 5; config.maxAliveMobs = 20;
            game.setConfigSource(config::copy);
            try { arena = new ArenaFixture(h, true, false); }
            catch (RuntimeException | Error e) { game.stop(); game.setConfigSource(original::snapshot); throw e; }
            h.runBeforeTestEnd(this::close);
        }
        int command(String text) {
            try { return arena.ctx.server().getCommands().getDispatcher().execute(text, arena.ctx.server().createCommandSourceStack()); }
            catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) { throw new IllegalStateException(e); }
        }
        Runnable check(Runnable action) {
            return () -> { try { action.run(); } catch (RuntimeException | Error e) { close(); throw e; } };
        }
        void timerRejected() {
            long before = arena.ctx.timer().remainingTicks();
            try { DebugDriver.timer(1); throw new AssertionError("Arena timer must be rejected"); }
            catch (IllegalStateException expected) {
                h.assertTrue(expected.getMessage().contains("cannot control arena battles"), "Timer error explains the separate battle timer");
            }
            h.assertTrue(command("ma debug timer 1") == 0 && arena.ctx.timer().remainingTicks() == before,
                    "Rejected timer command preserves the phase timer");
        }
        @Override public void close() {
            if (closed) return;
            closed = true;
            arena.close();
            GameManager.get().setConfigSource(original::snapshot);
        }
    }

    @GameTest(environment = "mobarena:debug_actions", maxTicks = 700)
    public void killDownAndClearwaveUseRealEventsAndDrainTheQueue(GameTestHelper h) {
        var f = new Fixture(h);
        try {
            var a = f.arena;
            try { DebugDriver.kill(a.blue, "cow", DebugDriver.MAX_COUNT + 1); throw new AssertionError("Reject oversized kill"); }
            catch (IllegalArgumentException expected) { }
            try { DebugDriver.poolAdd(TeamId.BLUE, "cow", DebugDriver.MAX_COUNT + 1); throw new AssertionError("Reject oversized pool addition"); }
            catch (IllegalArgumentException expected) { }
            h.assertTrue(a.ctx.get(CollectKeys.POOLS).get(TeamId.BLUE).total() == 0, "Oversized requests do not change the pool");
            var dispatcher = a.ctx.server().getCommands().getDispatcher();
            var source = a.ctx.server().createCommandSourceStack();
            for (String command : List.of("ma debug kill " + a.blue.getName().getString() + " cow 10001", "ma debug pool add blue cow 10001")) {
                try { dispatcher.execute(command, source); throw new AssertionError("Reject oversized command count"); }
                catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) { }
            }
            h.assertTrue(f.command("ma debug kill " + a.blue.getName().getString() + " cow 25") == 1, "Kill command succeeds");
            h.assertTrue(a.ctx.get(CollectKeys.POOLS).get(TeamId.BLUE).count("minecraft:cow") == 25,
                    "Kill n uses real collection attribution and adds n pool entries");
            DebugDriver.poolAdd(TeamId.RED, "cow", 1);
            a.game.endPhaseNow();
            DebugDriver.waves(TeamId.BLUE, "cow:25", "-", "-");
            DebugDriver.waves(TeamId.RED, "cow:1", "-", "-");
            a.game.endPhaseNow();
            var battle = a.group().battle(TeamId.RED);
            List<LivingEntity> bodies = new ArrayList<>();
            String tag = WaveTags.tag(TeamId.RED, 1);
            h.startSequence().thenWaitUntil(() -> h.assertTrue(battle.state() == BattleFlow.State.FIGHT
                    && battle.waveMobs().size() == 20, "Wave reaches its configured alive cap"))
                .thenExecute(f.check(() -> {
                    h.assertTrue(battle.remainingInQueue() == 5, "The cap leaves five unspawned orders in the queue");
                    f.timerRejected();
                    ArenaGameTest.damageable(a.red);
                    h.assertTrue(f.command("ma debug down " + a.red.getName().getString()) == 1, "Down command succeeds");
                    h.assertTrue(a.red.isAlive() && a.red.isSpectator() && !a.mate.isSpectator(), "Down uses ALLOW_DEATH and the teammate stays alive");
                    bodies.addAll(battle.waveMobs());
                    h.assertTrue(f.command("ma debug clearwave red") == 1, "Clearwave command succeeds");
                    h.assertTrue(battle.remainingInQueue() == 0 && battle.waveMobs().isEmpty(), "Clearwave kills all living mobs and drains pending orders");
                    h.assertTrue(battle.kills() == 20 && battle.countAlive() == 20 && battle.state() == BattleFlow.State.FIGHT,
                            "Real death events count kills, while dying bodies prevent an immediate clear");
                }))
                .thenWaitUntil(() -> {
                    h.assertTrue(battle.waveMobs().isEmpty(), "The cleared queue never spawns a new living mob");
                    h.assertTrue(battle.state() == BattleFlow.State.COOLDOWN, "WaveCleared follows body removal");
                })
                .thenExecute(f.check(() -> {
                    h.assertTrue(bodies.size() == 20 && bodies.stream().allMatch(LivingEntity::isRemoved), "All original bodies are removed before cooldown");
                    h.assertTrue(battle.flow.progress().clearedWaves() == 1, "WaveCleared advances progress exactly once");
                }))
                .thenIdle(25)
                .thenExecute(f.check(() -> {
                    h.assertTrue(battle.state() == BattleFlow.State.COOLDOWN && battle.remainingInQueue() == 0, "No queued orders return during cooldown");
                    h.assertTrue(a.arena(TeamId.RED).level().getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(LivingEntity.class),
                            e -> e.entityTags().contains(tag)).isEmpty(), "No old wave entities remain");
                    a.game.enterSuddenDeath();
                    h.assertTrue(a.game.phase() == Phase.SUDDEN_DEATH, "Timer check reaches sudden death");
                    f.timerRejected();
                })).thenExecute(f::close).thenSucceed();
        } catch (RuntimeException | Error e) { f.close(); throw e; }
    }
}
