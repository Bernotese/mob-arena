package dev.mobarena.arena;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class SpawnPlannerTest {
    SortedMap<String,Integer> wave() { return new TreeMap<>(Map.of("zombie",33,"squid",17)); }
    @Test void countsAndDeterminism() {
        var a = SpawnPlanner.plan(wave(),8,3,"squid"::equals,42);
        assertEquals(50,a.size());
        assertEquals(17,a.stream().filter(SpawnPlanner.SpawnOrder::water).count());
        assertEquals(a,SpawnPlanner.plan(wave(),8,3,"squid"::equals,42));
        assertNotEquals(a,SpawnPlanner.plan(wave(),8,3,"squid"::equals,43));
    }
    @Test void spreadsLandAndWaterSeparately() {
        var a = SpawnPlanner.plan(wave(),8,3,"squid"::equals,42);
        for (boolean water : List.of(false,true)) {
            int[] counts = new int[water ? 3 : 8];
            a.stream().filter(o -> o.water()==water).forEach(o -> counts[o.pointIndex()]++);
            assertTrue(Arrays.stream(counts).max().orElseThrow()-Arrays.stream(counts).min().orElseThrow()<=1);
        }
    }
    @Test void waterFallbackUsesRingAndKeepsWaterFlag() {
        var a=SpawnPlanner.plan(new TreeMap<>(Map.of("squid",17)),8,0,id->true,42);
        int[] counts=new int[8];
        a.forEach(o->{assertTrue(o.water()); counts[o.pointIndex()]++;});
        assertEquals(1,Arrays.stream(counts).max().orElseThrow()-Arrays.stream(counts).min().orElseThrow());
    }
    @Test void queueLimitsAndResumesAfterDeaths() {
        var q=new SpawnQueue(SpawnPlanner.plan(wave(),8,3,"squid"::equals,42),15);
        assertEquals(10,q.tick(0).size());
        q.report(10);
        assertEquals(5,q.tick().size());
        q.report(15);
        assertEquals(0,q.tick().size());
        assertEquals(0,q.tick(20).size());
        q.report(12);
        assertEquals(3,q.tick().size());
        assertEquals(32,q.remaining());
        while(!q.spawningDone()) {
            q.report(0);
            assertTrue(q.tick().size()<=10);
        }
        assertTrue(q.tick(0).isEmpty());
    }
    @Test void staleAliveReportsKeepReservations() {
        var orders=SpawnPlanner.plan(new TreeMap<>(Map.of("zombie",300)),8,0,id->false,42);
        var q=new SpawnQueue(orders,150);
        q.report(145);
        int spawned=0;
        for(int tick=0;tick<9;tick++) {
            spawned+=q.tick().size();
            assertTrue(145+spawned<=150);
        }
        assertEquals(5,spawned);
        assertEquals(295,q.remaining());
        q.report(147);
        assertEquals(3,q.tick().size());
        assertTrue(q.tick().isEmpty());
        q.report(0);
        assertEquals(10,q.tick().size());
    }
    @Test void freshReportsDrainLargeWave() {
        var orders=SpawnPlanner.plan(new TreeMap<>(Map.of("zombie",300)),8,0,id->false,42);
        var q=new SpawnQueue(orders,150);
        int spawned=0;
        for(int tick=0;tick<30;tick++) spawned+=q.tick(0).size();
        assertEquals(300,spawned);
        assertTrue(q.spawningDone());
    }
    @Test void aliveEstimateIncludesUnscannedSpawns() {
        var orders=SpawnPlanner.plan(new TreeMap<>(Map.of("zombie",3)),8,0,id->false,42);
        var q=new SpawnQueue(orders,150);
        q.report(0);
        assertEquals(3,q.tick().size());
        assertEquals(3,q.aliveEstimate());
        assertTrue(q.spawningDone());
        q.report(2);
        assertEquals(2,q.aliveEstimate());
    }
    @Test void emptyQueueAndInvalidArguments() {
        assertTrue(new SpawnQueue(List.of(),1).spawningDone());
        assertThrows(IllegalArgumentException.class,()->new SpawnQueue(List.of(),0));
        assertThrows(IllegalArgumentException.class,()->SpawnPlanner.plan(wave(),0,0,id->false,1));
        assertThrows(IllegalArgumentException.class,()->SpawnPlanner.plan(new TreeMap<>(Map.of("zombie",-1)),8,0,id->false,1));
    }
}
