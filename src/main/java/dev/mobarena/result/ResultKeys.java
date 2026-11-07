package dev.mobarena.result;

import dev.mobarena.core.Key;
import dev.mobarena.team.TeamId;
import java.util.Objects;
import java.util.Optional;

/** Game context keys owned by the result module. */
public final class ResultKeys {
    private ResultKeys() {}

    /** Set once when the game reaches END through a verdict. Missing if an admin skipped to END. */
    public static final Key<GameResult> RESULT = Key.of("result.game_result");

    /** Winner is empty for a draw. */
    public record GameResult(Optional<TeamId> winner, String reason) {
        public GameResult {
            Objects.requireNonNull(winner);
            Objects.requireNonNull(reason);
        }
    }
}
