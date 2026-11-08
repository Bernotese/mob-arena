package dev.mobarena.result;

import dev.mobarena.core.EliminationReason;
import dev.mobarena.core.TeamProgress;
import java.util.Objects;

public sealed interface Outcome {
    record Running() implements Outcome {}
    record Finished(long ticks) implements Outcome {}
    record Eliminated(TeamProgress progress, EliminationReason reason) implements Outcome {
        public Eliminated(TeamProgress progress) { this(progress, null); }
        public Eliminated { Objects.requireNonNull(progress); }
    }
}
