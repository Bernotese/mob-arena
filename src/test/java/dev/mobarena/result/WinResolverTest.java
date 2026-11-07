package dev.mobarena.result;
import dev.mobarena.core.TeamProgress;
import dev.mobarena.team.TeamId;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class WinResolverTest {
    final WinResolver resolver = new WinResolver();
    final Outcome r = new Outcome.Running();
    Outcome f(long ticks) { return new Outcome.Finished(ticks); }
    Outcome e(int waves, int kills, long ticks) { return new Outcome.Eliminated(new TeamProgress(waves,kills,ticks)); }
    Verdict win(TeamId team) { return new Verdict.Win(team); }
    @Test void arenaBothRunning() { assertEquals(new Verdict.Pending(), resolver.arena(r, r)); }
    @Test void arenaFinishedRunning() { assertEquals(new Verdict.Pending(), resolver.arena(f(100), r)); }
    @Test void arenaEliminatedRunning() { assertEquals(new Verdict.Pending(), resolver.arena(e(1,4,10), r)); }
    @Test void arenaFinishedEliminated() { assertEquals(win(TeamId.RED), resolver.arena(f(100), e(0,0,10))); }
    @Test void arenaEliminatedFinished() { assertEquals(win(TeamId.BLUE), resolver.arena(e(2,9,10), f(100))); }
    @Test void arenaMoreWaves() { assertEquals(win(TeamId.RED), resolver.arena(e(2,0,10), e(1,30,10))); }
    @Test void arenaMoreKills() { assertEquals(win(TeamId.BLUE), resolver.arena(e(1,5,10), e(1,7,10))); }
    @Test void arenaEqualProgressIgnoresTime() { assertEquals(new Verdict.SuddenDeath(), resolver.arena(e(1,5,100), e(1,5,900))); }
    @Test void arenaBothFinishedIgnoresTime() { assertEquals(new Verdict.SuddenDeath(), resolver.arena(f(100), f(900))); }
    @Test void arenaMirrorFinished() { assertEquals(win(TeamId.BLUE), resolver.arena(e(0,0,10), f(100))); }
    @Test void arenaMirrorWaves() { assertEquals(win(TeamId.BLUE), resolver.arena(e(1,30,10), e(2,0,10))); }
    @Test void arenaMirrorKills() { assertEquals(win(TeamId.RED), resolver.arena(e(1,7,10), e(1,5,10))); }
    @Test void suddenFinishedRunning() { assertEquals(win(TeamId.RED), resolver.suddenDeath(f(100), r)); }
    @Test void suddenEliminatedRunning() { assertEquals(win(TeamId.BLUE), resolver.suddenDeath(e(0,3,10), r)); }
    @Test void suddenMoreKills() { assertEquals(win(TeamId.RED), resolver.suddenDeath(e(0,12,10), e(0,8,10))); }
    @Test void suddenEqualKills() { assertEquals(new Verdict.Draw(), resolver.suddenDeath(e(0,8,100), e(0,8,900))); }
    @Test void suddenFinishedEliminated() { assertEquals(win(TeamId.RED), resolver.suddenDeath(f(100), e(0,2,100))); }
    @Test void suddenBothFinished() { assertEquals(new Verdict.Draw(), resolver.suddenDeath(f(400), f(400))); }
    @Test void suddenBothRunning() { assertEquals(new Verdict.Pending(), resolver.suddenDeath(r, r)); }
    @Test void suddenMirrors() {
        assertEquals(win(TeamId.BLUE), resolver.suddenDeath(r, f(100)));
        assertEquals(win(TeamId.RED), resolver.suddenDeath(r, e(0,3,10)));
        assertEquals(win(TeamId.BLUE), resolver.suddenDeath(e(0,8,10), e(0,12,10)));
        assertEquals(win(TeamId.BLUE), resolver.suddenDeath(e(0,2,100), f(100)));
    }
    @Test void suddenIgnoresWavesAndTime() {
        assertEquals(win(TeamId.BLUE), resolver.suddenDeath(e(2,1,5), e(0,2,900)));
    }
}
