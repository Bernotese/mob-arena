package dev.mobarena.arena;

import static dev.mobarena.arena.ArenaGameTest.*;

import dev.mobarena.core.*;
import dev.mobarena.hud.HudOverrides;
import dev.mobarena.result.ResultKeys;
import dev.mobarena.team.TeamId;
import dev.mobarena.wave.WaveKeys;
import dev.mobarena.wave.WavePlan;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.monster.cubemob.Slime;
import java.util.*;

/** T8b part 2 (PLAN T8 section 7.7): the real result resolver starts the sudden death battle. */
public class SuddenDeathGameTest {
    private static final String ZOMBIE = "minecraft:zombie", COW = "minecraft:cow";
    private static final String SD_RED = WaveTags.tag(TeamId.RED, WaveTags.SUDDEN_DEATH_WAVE);

    /** Real tracker in ARENA; RED fights the BLUE plan and BLUE fights the RED plan. */
    private static ArenaFixture fixture(GameTestHelper h, List<Map<String, Integer>> redFights, List<Map<String, Integer>> blueFights) {
        var f = new ArenaFixture(h, false, true, true);
        var plans = new EnumMap<TeamId, WavePlan>(TeamId.class);
        plans.put(TeamId.BLUE, new WavePlan(TeamId.BLUE, redFights.stream().map(w -> (SortedMap<String, Integer>) new TreeMap<>(w)).toList()));
        plans.put(TeamId.RED, new WavePlan(TeamId.RED, blueFights.stream().map(w -> (SortedMap<String, Integer>) new TreeMap<>(w)).toList()));
        f.ctx.put(WaveKeys.PLANS, plans);
        return f;
    }
    private static ResultHooks hooks(ArenaFixture f) { return f.ctx.get(ResultHooks.KEY); }
    /** Equal ARENA eliminations: the resolver enters SUDDEN_DEATH at the end of this tick. */
    private static void equalEliminations(ArenaFixture f) {
        hooks(f).teamEliminated(TeamId.RED, new TeamProgress(1, 5, 0));
        hooks(f).teamEliminated(TeamId.BLUE, new TeamProgress(1, 5, 0));
    }
    private static List<? extends Slime> slimes(Arena arena) {
        return arena.level().getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(Slime.class), e -> !e.isRemoved());
    }
    private static String result(ArenaFixture f) {
        var r = f.ctx.get(ResultKeys.RESULT);
        return r == null ? "none" : r.winner().map(Enum::name).orElse("draw") + " / " + r.reason();
    }

    /** S1 + S6: real starter, merged wave with tag 4, players reset at the own spawn. */
    @GameTest(environment = "mobarena:arena_sd_start", maxTicks = 800, skyAccess = true)
    public void equalEliminationsStartSuddenDeath(GameTestHelper h) {
        var f = fixture(h, List.of(Map.of(ZOMBIE, 1), Map.of(ZOMBIE, 1, COW, 1), Map.of()), List.of(Map.of(ZOMBIE, 1), Map.of(), Map.of()));
        h.startSequence()
            .thenWaitUntil(() -> h.assertTrue(f.arena(TeamId.RED) != null && f.arena(TeamId.BLUE) != null, "Arenas exist"))
            .thenExecute(() -> {
                // BLUE watches the RED arena like an eliminated player; RED is hurt and has an effect.
                f.group().spectate(f.blue, f.arena(TeamId.RED));
                f.red.addEffect(new MobEffectInstance(MobEffects.SPEED, 2400));
                f.red.setHealth(5);
                equalEliminations(f);
            })
            .thenWaitUntil(() -> h.assertTrue(f.game.phase() == Phase.SUDDEN_DEATH, "SUDDEN_DEATH, phase " + f.game.phase()))
            .thenExecute(() -> {
                var group = f.group();
                h.assertTrue(group != null && group.battle(TeamId.RED).suddenDeath && group.battle(TeamId.BLUE).suddenDeath,
                        "Installed starter runs sudden death battles");
                h.assertTrue(f.blue.level() == f.arena(TeamId.BLUE).level() && !f.blue.isSpectator(),
                        "BLUE spectator is back in survival in the own arena at once");
                h.assertTrue(f.red.getActiveEffects().isEmpty(), "Effects are cleared");
                h.assertTrue(f.red.getHealth() == f.red.getMaxHealth(), "Full health");
                h.assertTrue(f.red.level() == f.arena(TeamId.RED).level(), "RED in the own arena");
            })
            .thenWaitUntil(() -> h.assertTrue(mobs(f.arena(TeamId.RED), SD_RED).size() == 3, "RED fights 2 zombies and 1 cow with tag 4"))
            .thenWaitUntil(() -> h.assertTrue(mobs(f.arena(TeamId.BLUE), WaveTags.tag(TeamId.BLUE, WaveTags.SUDDEN_DEATH_WAVE)).size() == 1,
                    "BLUE fights 1 zombie with tag 4"))
            .thenExecute(() -> {
                for (int wave = 1; wave <= 3; wave++)
                    h.assertTrue(mobs(f.arena(TeamId.RED), WaveTags.tag(TeamId.RED, wave)).isEmpty(), "No mob of wave " + wave);
            })
            .thenWaitUntil(() -> {
                var line = HudOverrides.of(f.ctx).get(TeamId.RED);
                h.assertTrue(line != null && line.text().getString().contains("Sudden death"), "HUD shows sudden death");
            })
            .thenSucceed();
    }

    /** S2 */
    @GameTest(environment = "mobarena:arena_sd_clear", maxTicks = 800, skyAccess = true)
    public void firstClearWins(GameTestHelper h) {
        var f = fixture(h, List.of(Map.of(ZOMBIE, 1), Map.of(COW, 1), Map.of()), List.of(Map.of(ZOMBIE, 2), Map.of(), Map.of()));
        h.startSequence()
            .thenExecute(() -> equalEliminations(f))
            .thenWaitUntil(() -> h.assertTrue(mobs(f.arena(TeamId.RED), SD_RED).size() == 2, "RED mobs spawn"))
            .thenExecute(() -> mobs(f.arena(TeamId.RED), SD_RED).forEach(m -> kill(m, f.red)))
            .thenWaitUntil(() -> h.assertTrue(f.game.phase() == Phase.END, "Game ends"))
            .thenExecute(() -> h.assertValueEqual(result(f), "RED / Sudden death: All waves cleared", "Result"))
            .thenSucceed();
    }

    /** S3 */
    @GameTest(environment = "mobarena:arena_sd_down", maxTicks = 800, skyAccess = true)
    public void allDownLosesAtOnce(GameTestHelper h) {
        var f = fixture(h, List.of(Map.of(ZOMBIE, 1), Map.of(), Map.of()), List.of(Map.of(ZOMBIE, 1), Map.of(), Map.of()));
        h.startSequence()
            .thenExecute(() -> equalEliminations(f))
            .thenWaitUntil(() -> h.assertTrue(f.game.phase() == Phase.SUDDEN_DEATH
                    && f.group().battle(TeamId.RED).state() == BattleFlow.State.FIGHT, "Sudden death fight"))
            .thenExecute(() -> killPlayer(f.red))
            .thenWaitUntil(() -> h.assertTrue(f.game.phase() == Phase.END, "Game ends"))
            .thenExecute(() -> h.assertValueEqual(result(f), "BLUE / Sudden death: Other team eliminated", "Result"))
            .thenSucceed();
    }

    /** S4: both time out in the same tick; RED killed more. */
    @GameTest(environment = "mobarena:arena_sd_kills", maxTicks = 800, skyAccess = true)
    public void timeOutComparesKills(GameTestHelper h) { timeOut(h, 1, "RED / Sudden death: More kills"); }

    /** S4 variant: equal kills at the time-out give a draw. */
    @GameTest(environment = "mobarena:arena_sd_draw", maxTicks = 800, skyAccess = true)
    public void timeOutWithEqualKillsIsDraw(GameTestHelper h) { timeOut(h, 0, "draw / Sudden death: equal result"); }

    private static void timeOut(GameTestHelper h, int redKills, String expected) {
        var f = fixture(h, List.of(Map.of(), Map.of(), Map.of()), List.of(Map.of(), Map.of(), Map.of()));
        h.startSequence()
            .thenExecute(() -> equalEliminations(f))
            .thenWaitUntil(() -> h.assertTrue(f.game.phase() == Phase.SUDDEN_DEATH, "SUDDEN_DEATH"))
            .thenExecute(() -> {
                // Short script for both teams, started in the same tick: the time-out comes in the same tick.
                var listener = BattleListener.of(hooks(f));
                for (TeamId team : TeamId.values())
                    f.group().start(team, () -> Arena.fromWorlds(f.ctx, team), new BattleScript(
                            List.of(new TreeMap<>(Map.of(ZOMBIE, 2))), 1, 0, 60, 20 * 600), listener, true);
            })
            .thenWaitUntil(() -> h.assertTrue(mobs(f.arena(TeamId.RED), SD_RED).size() == 2, "RED mobs spawn"))
            .thenExecute(() -> mobs(f.arena(TeamId.RED), SD_RED).stream().limit(redKills).forEach(m -> kill(m, f.red)))
            .thenWaitUntil(() -> h.assertTrue(f.game.phase() == Phase.END, "Game ends at the time-out"))
            .thenExecute(() -> h.assertValueEqual(result(f), expected, "Result"))
            .thenSucceed();
    }

    /** S5: a dying big slime of ARENA does not split into the sudden death arena. */
    @GameTest(environment = "mobarena:arena_sd_slime", maxTicks = 800, skyAccess = true)
    public void dyingSlimeAtTransitionLeavesNoChildren(GameTestHelper h) {
        var f = fixture(h, List.of(Map.of(), Map.of(), Map.of()), List.of(Map.of(), Map.of(), Map.of()));
        String tag = WaveTags.tag(TeamId.RED, 1);
        f.run(TeamId.RED, 1, 20, 20 * 600, 20 * 600, Map.of("minecraft:slime", 1), Map.of(), Map.of());
        h.startSequence()
            .thenWaitUntil(() -> h.assertTrue(mobs(f.arena(TeamId.RED), tag).size() == 1, "Slime spawns"))
            .thenExecute(() -> {
                var slime = (Slime) mobs(f.arena(TeamId.RED), tag).getFirst();
                slime.setSize(4, true);
                kill(slime, f.red);
                h.assertTrue(slime.isDeadOrDying(), "Slime is dying");
                equalEliminations(f);
            })
            .thenWaitUntil(() -> h.assertTrue(f.game.phase() == Phase.SUDDEN_DEATH, "SUDDEN_DEATH"))
            .thenIdle(30)
            .thenExecute(() -> h.assertTrue(slimes(f.arena(TeamId.RED)).isEmpty(),
                    "No slimes in the arena: " + slimes(f.arena(TeamId.RED)).size()))
            .thenSucceed();
    }
}
