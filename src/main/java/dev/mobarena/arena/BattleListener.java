package dev.mobarena.arena;

import dev.mobarena.core.EliminationReason;
import dev.mobarena.core.GameContext;
import dev.mobarena.core.ResultHooks;
import dev.mobarena.core.TeamProgress;
import dev.mobarena.team.TeamId;

/** Battle results. ARENA uses resultHooks(ctx); T8 sudden death passes its own listener. */
public interface BattleListener {
    void finished(TeamId team, long ticks);
    void eliminated(TeamId team, TeamProgress progress);
    default void eliminated(TeamId team, TeamProgress progress, EliminationReason reason) { eliminated(team, progress); }
    default void kill(TeamId team, int killsInWave) {}

    /** Sudden death: report straight to the given hooks (the ResultTracker). */
    static BattleListener of(ResultHooks hooks) {
        java.util.Objects.requireNonNull(hooks);
        return new BattleListener() {
            @Override public void finished(TeamId team, long ticks) { hooks.teamFinished(team, ticks); }
            @Override public void eliminated(TeamId team, TeamProgress progress) { hooks.teamEliminated(team, progress); }
            @Override public void eliminated(TeamId team, TeamProgress progress, EliminationReason reason) { hooks.teamEliminated(team, progress, reason); }
        };
    }

    static BattleListener resultHooks(GameContext ctx) {
        return new BattleListener() {
            @Override public void finished(TeamId team, long ticks) { hooks(ctx).teamFinished(team, ticks); }
            @Override public void eliminated(TeamId team, TeamProgress progress) { hooks(ctx).teamEliminated(team, progress); }
            @Override public void eliminated(TeamId team, TeamProgress progress, EliminationReason reason) { hooks(ctx).teamEliminated(team, progress, reason); }
        };
    }
    private static ResultHooks hooks(GameContext ctx) {
        ResultHooks hooks = ctx.get(ResultHooks.KEY);
        if (hooks == null) throw new IllegalStateException("ResultHooks missing in GameContext");
        return hooks;
    }
}
