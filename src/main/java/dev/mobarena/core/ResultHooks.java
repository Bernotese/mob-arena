package dev.mobarena.core;
import dev.mobarena.team.TeamId;
/** Result module installs its implementation in GameContext. */
public interface ResultHooks {
    Key<ResultHooks> KEY = Key.of("result_hooks");
    void teamFinished(TeamId team, long timeTicks);
    void teamEliminated(TeamId team, TeamProgress progress);
}
