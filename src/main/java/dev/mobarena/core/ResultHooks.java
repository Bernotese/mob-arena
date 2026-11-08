package dev.mobarena.core;
import dev.mobarena.team.TeamId;
/** Result module installs its implementation in GameContext. */
public interface ResultHooks {
    Key<ResultHooks> KEY = Key.of("result_hooks");
    /** Time starts at the first wave countdown after the arena is ready. */
    void teamFinished(TeamId team, long timeTicks);
    void teamEliminated(TeamId team, TeamProgress progress);
    default void teamEliminated(TeamId team, TeamProgress progress, EliminationReason reason) {
        teamEliminated(team, progress);
    }
}
