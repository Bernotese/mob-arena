package dev.mobarena.collect;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LastHitTrackerTest {
    @Test void inclusiveWindowAndNewestHit() {
        var hits = new LastHitTracker<String, String>(200);
        hits.record("victim", "first", 100);
        assertEquals(Optional.of("first"), hits.lookup("victim", 300));
        assertTrue(hits.lookup("victim", 301).isEmpty());
        hits.record("victim", "second", 200);
        hits.record("victim", "older", 150);
        assertEquals(Optional.of("second"), hits.lookup("victim", 201));
        hits.record("victim", "sameTick", 200);
        assertEquals(Optional.of("sameTick"), hits.lookup("victim", 200));
    }
    @Test void forgetPruneAndClear() {
        var hits = new LastHitTracker<String, String>(200);
        hits.record("old", "a", 99);
        hits.record("boundary", "b", 100);
        hits.record("new", "c", 200);
        hits.prune(300);
        assertEquals(2, hits.size());
        assertTrue(hits.lookup("old", 300).isEmpty());
        assertEquals(Optional.of("b"), hits.lookup("boundary", 300));
        hits.forget("boundary");
        assertEquals(1, hits.size());
        hits.clear();
        assertEquals(0, hits.size());
    }
    @Test void validatesWindowAndRejectsFutureHits() {
        assertThrows(IllegalArgumentException.class, () -> new LastHitTracker<>(-1));
        var hits = new LastHitTracker<String, String>(0);
        hits.record("v", "p", 100);
        assertTrue(hits.lookup("v", 99).isEmpty());
        assertEquals(Optional.of("p"), hits.lookup("v", 100));
        assertTrue(hits.lookup("v", 101).isEmpty());
    }
}
