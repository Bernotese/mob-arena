package dev.mobarena.gametest;

import dev.mobarena.arena.ArenaBattle;
import dev.mobarena.arena.ArenaBattles;
import dev.mobarena.arena.BattleEvent;
import dev.mobarena.arena.BattleFlow;
import dev.mobarena.arena.WaveTags;
import dev.mobarena.collect.CollectKeys;
import dev.mobarena.core.Phase;
import dev.mobarena.core.ResultHooks;
import dev.mobarena.debug.DebugDriver;
import dev.mobarena.hud.HudModule;
import dev.mobarena.hud.HudOverrides;
import dev.mobarena.result.Outcome;
import dev.mobarena.result.ResultKeys;
import dev.mobarena.result.ResultTracker;
import dev.mobarena.team.TeamId;
import dev.mobarena.world.TeamWorlds;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import java.nio.file.Files;
import java.util.*;

/** T10 scenarios that need T8 results (S1, S4, S6). Each test has its own environment. */
public class FullResultTest {
    /** S1: 1v1, real kills in PREP, BLUE down in wave 2, RED clears all waves and wins. */
    @GameTest(environment = "mobarena:full_happy_path", maxTicks = 1500)
    public void happyPathRedClearsAllAndWins(GameTestHelper h) {
        var f = new FullGameFixture(h, true, 1);
        try {
            List<Phase> phases = new ArrayList<>(List.of(f.game.phase()));
            h.onEachTick(() -> { if (phases.getLast() != f.game.phase()) phases.add(f.game.phase()); });
            DebugDriver.kill(f.red, "cow", 3);
            DebugDriver.kill(f.blue, "cow", 3);
            var pools = f.ctx.get(CollectKeys.POOLS);
            h.assertTrue(pools.get(TeamId.RED).count("minecraft:cow") == 3 && pools.get(TeamId.BLUE).count("minecraft:cow") == 3,
                    "PREP kills fill the killer's team pool");
            f.waveConfig();
            phases.add(f.game.phase());
            for (var team : TeamId.values()) DebugDriver.waves(team, "cow:1", "cow:1", "cow:1");
            f.admin("ma skip");
            phases.add(f.game.phase());
            List<ResourceKey<Level>> keys = new ArrayList<>();
            h.startSequence().thenWaitUntil(f::arenaReady)
                .thenExecute(f.check(() -> {
                    keys.addAll(levelKeys(f));
                    h.assertTrue(keys.size() >= 4, "Both teams have game levels: " + keys.size());
                    f.red.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 3));
                }))
                .thenWaitUntil(() -> { waveUp(h, f, TeamId.RED, 1, 1); waveUp(h, f, TeamId.BLUE, 1, 1); })
                .thenExecute(f.check(() -> { f.admin("ma debug clearwave red"); f.admin("ma debug clearwave blue"); }))
                .thenWaitUntil(() -> { waveUp(h, f, TeamId.RED, 2, 1); waveUp(h, f, TeamId.BLUE, 2, 1); })
                .thenExecute(f.check(() -> { f.down(f.blue); f.admin("ma debug clearwave red"); }))
                .thenWaitUntil(() -> h.assertTrue(battle(h, f, TeamId.BLUE).state() == BattleFlow.State.ELIMINATED, "BLUE is eliminated"))
                .thenExecute(f.check(() -> {
                    h.assertTrue(f.game.phase() == Phase.ARENA, "RED still runs, so no verdict yet");
                    var blue = (Outcome.Eliminated) tracker(f).arenaOutcome(TeamId.BLUE);
                    h.assertTrue(blue.progress().clearedWaves() == 1 && blue.progress().killsInCurrentWave() == 0,
                            "BLUE progress: one wave cleared, " + blue.progress());
                    h.assertTrue(f.battle(TeamId.BLUE).eliminationReason().orElseThrow() == BattleEvent.Reason.ALL_DOWN, "BLUE reason ALL_DOWN");
                }))
                .thenWaitUntil(() -> waveUp(h, f, TeamId.RED, 3, 1))
                .thenExecute(f.check(() -> f.admin("ma debug clearwave red")))
                .thenWaitUntil(() -> h.assertTrue(f.game.phase() == Phase.END, "RED finishes, the game ends"))
                .thenExecute(f.check(() -> {
                    var result = f.ctx.get(ResultKeys.RESULT);
                    h.assertTrue(result != null && result.winner().equals(Optional.of(TeamId.RED))
                            && result.reason().equals("All waves cleared"), "RED wins: " + result);
                    h.assertTrue(HudOverrides.of(f.ctx).result().text().getString().equals("Team RED wins"), "HUD shows the winner");
                    h.assertTrue(tracker(f).arenaOutcome(TeamId.RED) instanceof Outcome.Finished, "RED outcome FINISHED");
                    var verdict = DebugDriver.state(f.server).getAsJsonObject("verdict");
                    h.assertTrue(verdict.get("type").getAsString().equals("WIN") && verdict.get("winner").getAsString().equals("RED"),
                            "State verdict: " + verdict);
                }))
                .thenWaitUntil(() -> h.assertTrue(f.game.phase() == Phase.LOBBY, "END returns to LOBBY"))
                .thenExecute(f.check(() -> {
                    h.assertTrue(phases.equals(List.of(Phase.PREP, Phase.WAVE_CONFIG, Phase.ARENA, Phase.END, Phase.LOBBY)),
                            "Phases in order: " + phases);
                    for (var p : f.teamPlayers()) {
                        h.assertTrue(p.getInventory().isEmpty(), "Inventory is empty in the lobby: " + p.getName().getString());
                        h.assertTrue(p.level() == f.server.overworld(), "Player is back in the lobby");
                    }
                    var bars = HudModule.bossBars();
                    h.assertTrue(bars.bar(null).getPlayers().isEmpty() && bars.bar(TeamId.RED).getPlayers().isEmpty()
                            && bars.bar(TeamId.BLUE).getPlayers().isEmpty(), "No boss bar is left");
                    f.state(Phase.LOBBY);
                    for (var key : keys) h.assertTrue(f.server.getLevel(key) == null, "Game level is closed: " + key.identifier());
                }))
                .thenWaitUntil(() -> {
                    for (var key : keys) {
                        var path = f.server.getWorldPath(LevelResource.ROOT).resolve("dimensions")
                                .resolve(key.identifier().getNamespace()).resolve(key.identifier().getPath());
                        h.assertTrue(!Files.exists(path), "Game level directory is deleted: " + path);
                    }
                })
                .thenExecute(f::close).thenSucceed();
        } catch (RuntimeException | Error e) { f.close(); throw e; }
    }

    /** S4: the wave max time ends the battle with TIME; the progress counts cleared waves and kills. */
    @GameTest(environment = "mobarena:full_wave_time", maxTicks = 2200)
    public void waveMaxTimeEliminatesWithTimeAndKeepsProgress(GameTestHelper h) {
        var f = new FullGameFixture(h, true, 1);
        try {
            DebugDriver.poolAdd(TeamId.RED, "cow", 3);
            DebugDriver.poolAdd(TeamId.BLUE, "cow", 3);
            f.waveConfig();
            DebugDriver.waves(TeamId.RED, "cow:1", "cow:1", "cow:1");
            // RED fights BLUE's waves.
            DebugDriver.waves(TeamId.BLUE, "cow:1", "cow:2", "-");
            f.admin("ma skip");
            long maxWave = f.ctx.config().waveMaxTimeSeconds() * 20L;
            // RED's elimination ends ARENA in the same tick and removes the battles from the context.
            ArenaBattle[] red = new ArenaBattle[1];
            h.startSequence().thenWaitUntil(f::arenaReady)
                .thenExecute(f.check(() -> { red[0] = f.battle(TeamId.RED); f.down(f.blue); }))
                .thenWaitUntil(() -> waveUp(h, f, TeamId.RED, 1, 1))
                .thenExecute(f.check(() -> f.admin("ma debug clearwave red")))
                .thenWaitUntil(() -> waveUp(h, f, TeamId.RED, 2, 2))
                .thenExecute(f.check(() -> {
                    h.assertTrue(f.battle(TeamId.BLUE).eliminationReason().orElseThrow() == BattleEvent.Reason.ALL_DOWN, "BLUE is down");
                    var blueState = DebugDriver.state(f.server).getAsJsonObject("battles").getAsJsonObject("BLUE");
                    h.assertTrue(blueState.get("reason").getAsString().equals("ALL_DOWN"), "State reason: " + blueState);
                    var cow = f.battle(TeamId.RED).waveMobs().getFirst();
                    cow.hurtServer((ServerLevel) cow.level(), cow.damageSources().genericKill(), Float.MAX_VALUE);
                }))
                .thenWaitUntil(() -> h.assertTrue(red[0].kills() == 1, "One kill in wave 2"))
                .thenWaitUntil(() -> h.assertTrue(red[0].state() == BattleFlow.State.ELIMINATED, "Wave max time ends RED"))
                .thenExecute(f.check(() -> {
                    h.assertTrue(red[0].eliminationReason().orElseThrow() == BattleEvent.Reason.TIME, "RED reason TIME");
                    var outcome = (Outcome.Eliminated) tracker(f).arenaOutcome(TeamId.RED);
                    h.assertTrue(outcome.progress().clearedWaves() == 1 && outcome.progress().killsInCurrentWave() == 1,
                            "RED progress: 1 wave cleared, 1 kill in wave 2: " + outcome.progress());
                    h.assertTrue(outcome.progress().ticks() >= maxWave, "Progress time includes the full wave 2: " + outcome.progress().ticks());
                }))
                .thenWaitUntil(() -> h.assertTrue(f.game.phase() == Phase.END, "Both eliminated: verdict"))
                .thenExecute(f.check(() -> {
                    var result = f.ctx.get(ResultKeys.RESULT);
                    h.assertTrue(result.winner().equals(Optional.of(TeamId.RED)) && result.reason().equals("More progress"),
                            "RED wins on progress: " + result);
                }))
                .thenExecute(f::close).thenSucceed();
        } catch (RuntimeException | Error e) { f.close(); throw e; }
    }

    /** S6: BLUE has 0 kills, so RED's waves are empty: RED clears each wave at once and finishes. */
    @GameTest(environment = "mobarena:full_empty_waves", maxTicks = 1000)
    public void emptyEnemyWavesClearAtOnceAndFinish(GameTestHelper h) {
        var f = new FullGameFixture(h, true, 1);
        try {
            DebugDriver.kill(f.red, "cow", 3);
            h.assertTrue(f.ctx.get(CollectKeys.POOLS).get(TeamId.BLUE).total() == 0, "BLUE has no kills");
            f.waveConfig();
            DebugDriver.waves(TeamId.RED, "cow:1", "cow:1", "cow:1");
            f.admin("ma skip");
            int[] maxRedMobs = {0};
            h.onEachTick(() -> {
                var battles = ArenaBattles.get(f.ctx);
                if (battles != null && battles.battle(TeamId.RED) != null)
                    maxRedMobs[0] = Math.max(maxRedMobs[0], battles.battle(TeamId.RED).waveMobs().size());
            });
            long countdown = f.ctx.config().waveCountdownSeconds() * 20L, cooldown = f.ctx.config().waveCooldownSeconds() * 20L;
            h.startSequence().thenWaitUntil(f::arenaReady)
                .thenWaitUntil(() -> h.assertTrue(battle(h, f, TeamId.RED).state() == BattleFlow.State.FINISHED, "RED finishes"))
                .thenExecute(f.check(() -> {
                    h.assertTrue(maxRedMobs[0] == 0, "No mob spawns in RED's arena");
                    var red = tracker(f).arenaOutcome(TeamId.RED);
                    h.assertTrue(red instanceof Outcome.Finished fin && fin.ticks() >= 3 * countdown + 2 * cooldown - 1
                            && fin.ticks() <= 3 * countdown + 2 * cooldown + 5, "Only countdowns and cooldowns take time: " + red);
                    h.assertTrue(f.battle(TeamId.BLUE).running() && WaveTags.tag(TeamId.BLUE, 1).equals(f.battle(TeamId.BLUE).currentTag()),
                            "BLUE still fights its first wave");
                    h.assertTrue(f.game.phase() == Phase.ARENA, "No verdict while BLUE runs");
                    f.down(f.blue);
                }))
                .thenWaitUntil(() -> h.assertTrue(f.game.phase() == Phase.END, "BLUE elimination ends the game"))
                .thenExecute(f.check(() -> {
                    var result = f.ctx.get(ResultKeys.RESULT);
                    h.assertTrue(result.winner().equals(Optional.of(TeamId.RED)) && result.reason().equals("All waves cleared"),
                            "RED wins: " + result);
                }))
                .thenExecute(f::close).thenSucceed();
        } catch (RuntimeException | Error e) { f.close(); throw e; }
    }

    /** Fails (no crash) when the battles are gone, for example after the phase ended. */
    private static ArenaBattle battle(GameTestHelper h, FullGameFixture f, TeamId team) {
        var group = ArenaBattles.get(f.ctx);
        h.assertTrue(group != null && group.battle(team) != null, team + " battle exists, phase " + f.game.phase());
        return group.battle(team);
    }

    private static ResultTracker tracker(FullGameFixture f) { return (ResultTracker) f.ctx.get(ResultHooks.KEY); }

    /** The team fights the given wave and all of its mobs are alive. */
    private static void waveUp(GameTestHelper h, FullGameFixture f, TeamId team, int wave, int mobs) {
        var battle = battle(h, f, team);
        h.assertTrue(battle.state() == BattleFlow.State.FIGHT && WaveTags.tag(team, wave).equals(battle.currentTag())
                && battle.waveMobs().size() == mobs, team + " fights wave " + wave + " with " + mobs + " mobs");
    }

    private static List<ResourceKey<Level>> levelKeys(FullGameFixture f) {
        return f.ctx.get(TeamWorlds.KEY).values().stream().flatMap(TeamWorlds::handles)
                .map(handle -> handle.asLevel().dimension()).toList();
    }
}
