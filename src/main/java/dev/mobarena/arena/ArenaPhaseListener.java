package dev.mobarena.arena;

import dev.mobarena.MobArena;
import dev.mobarena.hud.GameSound;
import dev.mobarena.hud.Sounds;
import dev.mobarena.core.*;
import dev.mobarena.team.TeamId;
import dev.mobarena.wave.WaveKeys;
import dev.mobarena.wave.WavePlan;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Runs the battles in ARENA (and in SUDDEN_DEATH once T8b starts them). */
public final class ArenaPhaseListener implements PhaseListener {
    @Override public void onEnter(Phase phase, GameContext ctx) {
        if (phase != Phase.ARENA || ctx.server() == null) return;
        var group = new ArenaBattles(ctx);
        ctx.put(ArenaBattles.KEY, group);
        var plans = ctx.get(WaveKeys.PLANS);
        var config = ctx.config();
        for (TeamId team : TeamId.values()) {
            // The enemy team built the waves this team fights.
            WavePlan plan = plans == null ? null : plans.get(team.opponent());
            List<SortedMap<String, Integer>> waves = plan != null ? plan.waves()
                    : List.of(new TreeMap<>(), new TreeMap<>(), new TreeMap<>());
            var script = new BattleScript(waves, config.waveCountdownSeconds() * 20, config.waveCooldownSeconds() * 20,
                    config.waveMaxTimeSeconds() * 20, config.mobGlowAfterSeconds() * 20);
            group.start(team, () -> Arena.fromWorlds(ctx, team), script, BattleListener.resultHooks(ctx), false);
        }
    }

    /**
     * T8b: called in SUDDEN_DEATH.onEnter (ResultModule starter). Each team fights all mobs of its 3 enemy
     * waves at once (tag wave 4), countdown as normal, no cooldown, max time suddenDeathMaxTimeSeconds.
     * All online team players are reset at the own spawn at once (SPEC 8.1).
     */
    public static ArenaBattles startSuddenDeath(GameContext ctx, BattleListener listener) {
        var old = ArenaBattles.get(ctx);
        if (old != null) old.close();
        var group = new ArenaBattles(ctx);
        ctx.put(ArenaBattles.KEY, group);
        var plans = ctx.get(WaveKeys.PLANS);
        var config = ctx.config();
        for (TeamId team : TeamId.values()) {
            WavePlan plan = plans == null ? null : plans.get(team.opponent());
            var script = new BattleScript(List.of(SuddenDeathScript.merge(plan == null ? null : plan.waves())), config.waveCountdownSeconds() * 20, 0,
                    config.suddenDeathMaxTimeSeconds() * 20, config.mobGlowAfterSeconds() * 20);
            group.start(team, () -> Arena.fromWorlds(ctx, team), script, listener, true);
        }
        if (ctx.server() != null) {
            group.broadcastToTeams(Component.literal("Sudden death! Each team fights all enemy waves at once. "
                    + "The first team that kills all mobs wins.").withStyle(ChatFormatting.DARK_PURPLE));
            group.resetPlayers();
            for (var team : TeamId.values()) Sounds.playAll(group.onlinePlayers(team), GameSound.SUDDEN_DEATH);
        }
        return group;
    }

    @Override public void onTick(Phase phase, GameContext ctx) {
        if (phase != Phase.ARENA && phase != Phase.SUDDEN_DEATH) return;
        var group = ArenaBattles.get(ctx);
        if (group == null || group.tick()) return;
        MobArena.LOGGER.error("Arena not ready after {} s; cancelling the game", ArenaBattles.READY_TIMEOUT_TICKS / 20);
        group.broadcast(Component.literal("The arena is not ready after 60 s. The game is cancelled.").withStyle(ChatFormatting.RED));
        GameManager.get().stop();
    }

    @Override public void onExit(Phase phase, GameContext ctx) {
        if (phase != Phase.ARENA && phase != Phase.SUDDEN_DEATH) return;
        var group = ArenaBattles.get(ctx);
        if (group != null) group.end();
        ctx.put(ArenaBattles.KEY, null);
    }
}
