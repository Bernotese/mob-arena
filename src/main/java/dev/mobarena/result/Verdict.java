package dev.mobarena.result;

import dev.mobarena.team.TeamId;
import java.util.Objects;

public sealed interface Verdict {
    record Pending() implements Verdict {}
    record Win(TeamId team) implements Verdict {
        public Win { Objects.requireNonNull(team); }
    }
    record SuddenDeath() implements Verdict {}
    record Draw() implements Verdict {}
}
