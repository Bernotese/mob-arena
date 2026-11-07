package dev.mobarena.result;

import dev.mobarena.core.TeamProgress;
import java.util.Objects;

public sealed interface Outcome {
    record Running() implements Outcome {}
    record Finished(long ticks) implements Outcome {}
    record Eliminated(TeamProgress progress) implements Outcome {
        public Eliminated { Objects.requireNonNull(progress); }
    }
}
