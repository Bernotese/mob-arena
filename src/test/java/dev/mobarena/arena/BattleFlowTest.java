package dev.mobarena.arena;
import dev.mobarena.core.TeamProgress;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class BattleFlowTest {
    final TickInput live=new TickInput(true,false,1,0,false,true);
    final TickInput clear=new TickInput(true,true,0,3,false,true);
    BattleFlow flow(int waves,int countdown,int cooldown,int max,int glow,boolean empty) {
        var wave=new TreeMap<String,Integer>();
        if(!empty) wave.put("zombie",3);
        return new BattleFlow(new BattleScript(Collections.nCopies(waves,wave),countdown,cooldown,max,glow));
    }
    @Test void waitsWithoutCountingTime() {
        var f=flow(1,20,0,100,50,false);
        for(int i=0;i<100;i++) assertTrue(f.tick(new TickInput(false,false,0,0,false,true)).isEmpty());
        assertEquals(BattleFlow.State.WAITING_ARENA,f.state());
        assertEquals(0,f.elapsedTicks());
    }
    @Test void countdownAndSpawnAtExactTick() {
        var f=flow(1,40,0,100,50,false);
        assertEquals(List.of(new BattleEvent.Countdown(1,2)),f.tick(live));
        for(int i=1;i<20;i++) assertTrue(f.tick(live).isEmpty());
        assertEquals(List.of(new BattleEvent.Countdown(1,1)),f.tick(live));
        for(int i=21;i<40;i++) assertTrue(f.tick(live).isEmpty());
        assertEquals(List.of(new BattleEvent.SpawnWave(1)),f.tick(live));
        assertEquals(40,f.elapsedTicks());
    }
    @Test void queueMustFinishBeforeClear() {
        var f=flow(1,0,0,100,50,false); f.tick(live); f.tick(live);
        assertTrue(f.tick(new TickInput(true,false,0,0,false,true)).isEmpty());
        assertEquals(BattleFlow.State.FIGHT,f.state());
        assertEquals(List.of(new BattleEvent.WaveCleared(1),new BattleEvent.Finished(3)),f.tick(clear));
        assertTrue(f.tick(live).isEmpty());
    }
    @Test void cooldownThenNextCountdown() {
        var f=flow(2,1,2,100,50,false); f.tick(live); f.tick(live);
        f.tick(clear); assertEquals(new TeamProgress(1,0,2),f.progress());
        assertTrue(f.tick(live).isEmpty());
        assertEquals(List.of(new BattleEvent.Countdown(2,1)),f.tick(live));
        assertEquals(List.of(new BattleEvent.SpawnWave(2)),f.tick(live));
        assertEquals(List.of(new BattleEvent.WaveCleared(2),new BattleEvent.Finished(6)),f.tick(clear));
    }
    @Test void zeroCooldownStartsCountdownImmediately() {
        var f=flow(2,20,0,100,50,false);
        f.tick(live); for(int i=0;i<20;i++) f.tick(live);
        assertEquals(List.of(new BattleEvent.WaveCleared(1),new BattleEvent.Countdown(2,1)),f.tick(clear));
    }
    @Test void emptyWaveClearsAtSpawn() {
        var f=flow(1,0,0,100,50,true);
        assertEquals(List.of(new BattleEvent.Countdown(1,0)),f.tick(live));
        assertEquals(List.of(new BattleEvent.SpawnWave(1),new BattleEvent.WaveCleared(1),new BattleEvent.Finished(1)),f.tick(live));
    }
    @Test void glowOnceAndTimeoutIncludesKills() {
        var f=flow(1,0,0,3,1,false); f.tick(live); f.tick(live);
        assertEquals(List.of(new BattleEvent.Glow(1)),f.tick(live));
        assertTrue(f.tick(live).isEmpty());
        assertEquals(List.of(new BattleEvent.Eliminated(new TeamProgress(0,2,4),BattleEvent.Reason.TIME)),
            f.tick(new TickInput(true,false,1,2,false,true)));
        assertTrue(f.tick(clear).isEmpty());
    }
    @Test void clearWinsOverTimeout() {
        var f=flow(1,0,0,1,1,false); f.tick(live); f.tick(live);
        assertEquals(List.of(new BattleEvent.WaveCleared(1),new BattleEvent.Finished(2)),f.tick(clear));
    }
    @Test void allDownWinsOverClear() {
        var f=flow(1,0,0,100,50,false); f.tick(live); f.tick(live);
        var events=f.tick(new TickInput(true,true,0,3,true,true));
        assertEquals(BattleEvent.Reason.ALL_DOWN,((BattleEvent.Eliminated)events.getFirst()).reason());
        assertEquals(0,f.progress().clearedWaves());
        assertEquals(3,f.progress().killsInCurrentWave());
        assertEquals(2,f.elapsedTicks());
    }
    @Test void allDownInCountdownAndCooldown() {
        for(boolean cooldown:List.of(false,true)) {
            var f=flow(2,1,10,100,50,false); f.tick(live);
            if(cooldown) { f.tick(live); f.tick(clear); }
            assertInstanceOf(BattleEvent.Eliminated.class,f.tick(new TickInput(true,false,0,0,true,true)).getFirst());
        }
    }
    @Test void noOnlinePlayersEliminatesOnce() {
        var f=flow(1,0,0,100,50,false);
        for (int tick=0;tick<1200;tick++)
            assertTrue(f.tick(new TickInput(false,false,0,0,true,false)).isEmpty());
        assertEquals(BattleFlow.State.WAITING_ARENA,f.state());
        assertEquals(0,f.elapsedTicks());
        assertEquals(List.of(new BattleEvent.Countdown(1,0)),
            f.tick(new TickInput(true,false,0,0,true,false)));
        assertEquals(BattleEvent.Reason.NO_PLAYERS,((BattleEvent.Eliminated)
            f.tick(new TickInput(true,false,0,0,true,false)).getFirst()).reason());
        assertTrue(f.tick(live).isEmpty());
    }
    @Test void zeroTimersClearAllEmptyWavesImmediately() {
        var f=flow(3,0,0,100,50,true);
        assertEquals(List.of(new BattleEvent.Countdown(1,0)),f.tick(live));
        assertEquals(7,f.tick(live).size());
        assertEquals(BattleFlow.State.FINISHED,f.state());
        assertEquals(3,f.progress().clearedWaves());
    }
    @Test void readyTickIgnoresAllDownUntilNextTick() {
        var f=flow(1,20,0,100,50,false);
        var down=new TickInput(true,false,0,0,true,true);
        assertEquals(List.of(new BattleEvent.Countdown(1,1)),f.tick(down));
        assertEquals(BattleFlow.State.COUNTDOWN,f.state());
        assertEquals(0,f.elapsedTicks());
        assertEquals(List.of(new BattleEvent.Eliminated(new TeamProgress(0,0,1),BattleEvent.Reason.ALL_DOWN)),f.tick(down));
    }
    @Test void scriptCopiesWavesAndValidatesTimers() {
        var w=new TreeMap<>(Map.of("zombie",3));
        var script=new BattleScript(List.of(w),0,0,1,0); w.clear();
        assertEquals(3,script.waves().getFirst().get("zombie"));
        assertThrows(UnsupportedOperationException.class,()->script.waves().getFirst().clear());
        assertThrows(IllegalArgumentException.class,()->new BattleScript(List.of(w),-1,0,1,0));
    }
}
