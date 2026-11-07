package dev.mobarena.result;

import dev.mobarena.team.TeamId;
import java.util.Objects;

public final class WinResolver {
    public Verdict arena(Outcome red, Outcome blue) {
        Objects.requireNonNull(red);
        Objects.requireNonNull(blue);
        if (red instanceof Outcome.Running || blue instanceof Outcome.Running) return new Verdict.Pending();
        if (red instanceof Outcome.Finished && blue instanceof Outcome.Finished) return new Verdict.SuddenDeath();
        if (red instanceof Outcome.Finished) return new Verdict.Win(TeamId.RED);
        if (blue instanceof Outcome.Finished) return new Verdict.Win(TeamId.BLUE);
        var r = ((Outcome.Eliminated) red).progress();
        var b = ((Outcome.Eliminated) blue).progress();
        int comparison = Integer.compare(r.clearedWaves(), b.clearedWaves());
        if (comparison == 0) comparison = Integer.compare(r.killsInCurrentWave(), b.killsInCurrentWave());
        return comparison == 0 ? new Verdict.SuddenDeath() : winner(comparison);
    }

    public Verdict suddenDeath(Outcome red, Outcome blue) {
        Objects.requireNonNull(red);
        Objects.requireNonNull(blue);
        if (red instanceof Outcome.Finished && blue instanceof Outcome.Finished) return new Verdict.Draw();
        if (red instanceof Outcome.Finished) return new Verdict.Win(TeamId.RED);
        if (blue instanceof Outcome.Finished) return new Verdict.Win(TeamId.BLUE);
        if (red instanceof Outcome.Eliminated r && blue instanceof Outcome.Eliminated b) {
            int comparison = Integer.compare(r.progress().killsInCurrentWave(), b.progress().killsInCurrentWave());
            return comparison == 0 ? new Verdict.Draw() : winner(comparison);
        }
        if (red instanceof Outcome.Eliminated) return new Verdict.Win(TeamId.BLUE);
        if (blue instanceof Outcome.Eliminated) return new Verdict.Win(TeamId.RED);
        return new Verdict.Pending();
    }

    private Verdict winner(int comparison) {
        return new Verdict.Win(comparison > 0 ? TeamId.RED : TeamId.BLUE);
    }
}
