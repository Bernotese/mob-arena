package dev.mobarena.result;

import dev.mobarena.core.Phase;
import dev.mobarena.core.TeamProgress;
import dev.mobarena.team.TeamId;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResultTrackerTest {
    private Phase phase = Phase.ARENA;
    private final ResultTracker tracker = new ResultTracker(() -> phase);

    private static TeamProgress progress(int waves, int kills) { return new TeamProgress(waves, kills, 100); }

    @Test void hooksOnlyRecordUntilResolve() {
        tracker.teamFinished(TeamId.RED, 100);
        tracker.teamEliminated(TeamId.BLUE, progress(1, 2));
        assertTrue(tracker.dirty());
        assertEquals(new Verdict.Win(TeamId.RED), tracker.resolve());
        assertFalse(tracker.dirty());
        assertEquals(new Verdict.Pending(), tracker.resolve(), "No new event, no second verdict");
    }

    @Test void oneTeamDonePendsUntilTheOtherTeam() {
        tracker.teamFinished(TeamId.RED, 100);
        assertEquals(new Verdict.Pending(), tracker.resolve());
        tracker.teamFinished(TeamId.BLUE, 900);
        assertEquals(new Verdict.SuddenDeath(), tracker.resolve(), "Time is not a tie breaker");
    }

    @Test void firstEventPerTeamWins() {
        tracker.teamEliminated(TeamId.RED, progress(1, 5));
        tracker.teamFinished(TeamId.RED, 100);
        assertInstanceOf(Outcome.Eliminated.class, tracker.arenaOutcome(TeamId.RED));
    }

    @Test void eventsOutsideBattlePhasesAreIgnored() {
        for (Phase other : new Phase[] {Phase.LOBBY, Phase.PREP, Phase.WAVE_CONFIG, Phase.END}) {
            phase = other;
            tracker.teamFinished(TeamId.RED, 100);
            assertFalse(tracker.dirty(), other.name());
            assertEquals(new Verdict.Pending(), tracker.resolve());
        }
        phase = Phase.ARENA;
        assertInstanceOf(Outcome.Running.class, tracker.arenaOutcome(TeamId.RED));
    }

    @Test void suddenDeathStartsFreshAndKeepsArenaOutcomes() {
        tracker.teamEliminated(TeamId.RED, progress(1, 5));
        tracker.teamEliminated(TeamId.BLUE, progress(1, 5));
        assertEquals(new Verdict.SuddenDeath(), tracker.resolve());
        assertEquals(Optional.empty(), tracker.suddenDeathOutcome(TeamId.RED));
        phase = Phase.SUDDEN_DEATH;
        tracker.startSuddenDeath();
        assertInstanceOf(Outcome.Running.class, tracker.suddenDeathOutcome(TeamId.RED).orElseThrow());
        assertInstanceOf(Outcome.Eliminated.class, tracker.arenaOutcome(TeamId.RED));
        assertEquals(new Verdict.Pending(), tracker.resolve());
    }

    @Test void sameTickFinishInSuddenDeathIsDraw() {
        phase = Phase.SUDDEN_DEATH;
        tracker.startSuddenDeath();
        tracker.teamFinished(TeamId.RED, 400);
        tracker.teamFinished(TeamId.BLUE, 400);
        Verdict verdict = tracker.resolve();
        assertEquals(new Verdict.Draw(), verdict, "Both events give one verdict");
        assertEquals(Optional.empty(), tracker.result(verdict).winner());
    }

    @Test void resultReasons() {
        tracker.teamEliminated(TeamId.RED, progress(2, 0));
        tracker.teamEliminated(TeamId.BLUE, progress(1, 30));
        var win = tracker.resolve();
        assertEquals(new ResultKeys.GameResult(Optional.of(TeamId.RED), "More progress"), tracker.result(win));

        var other = new ResultTracker(() -> Phase.ARENA);
        other.teamEliminated(TeamId.RED, progress(0, 1));
        other.teamFinished(TeamId.BLUE, 50);
        assertEquals("All waves cleared", other.result(other.resolve()).reason());

        var sudden = new ResultTracker(() -> Phase.SUDDEN_DEATH);
        sudden.startSuddenDeath();
        sudden.teamEliminated(TeamId.BLUE, progress(0, 3));
        assertEquals(new ResultKeys.GameResult(Optional.of(TeamId.RED), "Sudden death: Other team eliminated"),
                sudden.result(sudden.resolve()));
    }
}
