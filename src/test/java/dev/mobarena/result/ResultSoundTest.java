package dev.mobarena.result;

import dev.mobarena.hud.GameSound;
import dev.mobarena.team.TeamId;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResultSoundTest {
    @Test void resultSoundsFollowTeamAndVerdict() {
        var win = new ResultKeys.GameResult(Optional.of(TeamId.RED), "All waves cleared");
        assertEquals(Optional.of(GameSound.WIN), EndSequence.resultSound(TeamId.RED, win, false));
        assertEquals(Optional.of(GameSound.LOSE), EndSequence.resultSound(TeamId.BLUE, win, false));
        assertEquals(Optional.of(GameSound.DRAW), EndSequence.resultSound(null, win, false));
        var draw = new ResultKeys.GameResult(Optional.empty(), "Equal result");
        for (var team : TeamId.values()) {
            assertEquals(Optional.of(GameSound.DRAW), EndSequence.resultSound(team, draw, false));
            assertTrue(EndSequence.resultSound(team, win, true).isEmpty());
        }
        assertEquals(Optional.of(GameSound.DRAW), EndSequence.resultSound(null, draw, false));
        assertTrue(EndSequence.resultSound(null, draw, true).isEmpty());
    }
}
