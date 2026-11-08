package dev.mobarena.result;

import dev.mobarena.core.EliminationReason;
import dev.mobarena.core.Phase;
import dev.mobarena.core.ResultHooks;
import dev.mobarena.core.TeamProgress;
import dev.mobarena.team.TeamId;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Records battle results for one game. Hooks only record; {@link #resolve()} runs once per server tick,
 * so events from the same tick give one verdict. Server thread only.
 */
public final class ResultTracker implements ResultHooks {
    private final Supplier<Phase> phase;
    private final WinResolver resolver = new WinResolver();
    private final Map<TeamId, Outcome> arena = running();
    private final Map<TeamId, Outcome> suddenDeath = running();
    private boolean suddenDeathStarted;
    private boolean dirty;

    public ResultTracker(Supplier<Phase> phase) { this.phase = Objects.requireNonNull(phase); }

    private static Map<TeamId, Outcome> running() {
        var map = new EnumMap<TeamId, Outcome>(TeamId.class);
        for (TeamId team : TeamId.values()) map.put(team, new Outcome.Running());
        return map;
    }

    /** Outcomes of the phase that runs now (ARENA or SUDDEN_DEATH). Null in other phases. */
    private Map<TeamId, Outcome> active() {
        return switch (phase.get()) {
            case ARENA -> arena;
            case SUDDEN_DEATH -> suddenDeath;
            default -> null;
        };
    }

    @Override public void teamFinished(TeamId team, long timeTicks) { record(team, new Outcome.Finished(timeTicks)); }

    @Override public void teamEliminated(TeamId team, TeamProgress progress) { record(team, new Outcome.Eliminated(progress)); }

    @Override public void teamEliminated(TeamId team, TeamProgress progress, EliminationReason reason) {
        record(team, new Outcome.Eliminated(progress, reason));
    }

    /** The first event per team and phase wins. Events outside ARENA and SUDDEN_DEATH are ignored. */
    private void record(TeamId team, Outcome outcome) {
        Objects.requireNonNull(team);
        var outcomes = active();
        if (outcomes == null || !(outcomes.get(team) instanceof Outcome.Running)) return;
        outcomes.put(team, outcome);
        dirty = true;
    }

    /** Called when SUDDEN_DEATH starts: both teams run again. */
    void startSuddenDeath() {
        suddenDeath.putAll(running());
        suddenDeathStarted = true;
        dirty = false;
    }

    public Outcome arenaOutcome(TeamId team) { return arena.get(team); }
    public Optional<Outcome> suddenDeathOutcome(TeamId team) {
        return suddenDeathStarted ? Optional.of(suddenDeath.get(team)) : Optional.empty();
    }
    public boolean dirty() { return dirty; }

    /** Runs the resolver once if new events exist. Returns {@code Pending} if nothing changed. */
    public Verdict resolve() {
        var outcomes = active();
        if (!dirty || outcomes == null) return new Verdict.Pending();
        dirty = false;
        Outcome red = outcomes.get(TeamId.RED), blue = outcomes.get(TeamId.BLUE);
        return phase.get() == Phase.ARENA ? resolver.arena(red, blue) : resolver.suddenDeath(red, blue);
    }

    /** Result text for a final verdict of the phase that runs now. */
    ResultKeys.GameResult result(Verdict verdict) {
        boolean sudden = phase.get() == Phase.SUDDEN_DEATH;
        var outcomes = sudden ? suddenDeath : arena;
        if (verdict instanceof Verdict.Win win) {
            Outcome winner = outcomes.get(win.team()), loser = outcomes.get(win.team().opponent());
            String reason;
            if (winner instanceof Outcome.Finished) reason = "All waves cleared";
            else if (loser instanceof Outcome.Eliminated && winner instanceof Outcome.Running) reason = "Other team eliminated";
            else reason = sudden ? "More kills" : "More progress";
            return new ResultKeys.GameResult(Optional.of(win.team()), sudden ? "Sudden death: " + reason : reason);
        }
        return new ResultKeys.GameResult(Optional.empty(), sudden ? "Sudden death: equal result" : "Equal result");
    }
}
