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
        assertEquals(5,q.tick(10).size());
        assertEquals(0,q.tick(15).size());
        assertEquals(0,q.tick(20).size());
        assertEquals(3,q.tick(12).size());
        assertEquals(32,q.remaining());
        while(!q.spawningDone()) assertTrue(q.tick(0).size()<=10);
        assertTrue(q.tick(0).isEmpty());
    }
    @Test void emptyQueueAndInvalidArguments() {
        assertTrue(new SpawnQueue(List.of(),1).spawningDone());
        assertThrows(IllegalArgumentException.class,()->new SpawnQueue(List.of(),0));
        assertThrows(IllegalArgumentException.class,()->SpawnPlanner.plan(wave(),0,0,id->false,1));
        assertThrows(IllegalArgumentException.class,()->SpawnPlanner.plan(new TreeMap<>(Map.of("zombie",-1)),8,0,id->false,1));
    }
}
