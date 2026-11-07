package dev.mobarena.arena;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RespawnTimersTest {
    @Test void dueAtExactTickAndCancel() {
        var timers = new RespawnTimers();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        timers.put(a, 100); timers.put(b, 120);
        assertTrue(timers.isDown(a));
        assertEquals(20, timers.remaining(a, 80));
        assertEquals(List.of(), timers.takeDue(99));
        assertEquals(List.of(a), timers.takeDue(100));
        assertFalse(timers.isDown(a));
        assertEquals(-1, timers.remaining(a, 100));
        timers.cancel(b);
        assertEquals(List.of(), timers.takeDue(500));
        assertEquals(Set.of(), timers.down());
    }
    @Test void newTimerReplacesOld() {
        var timers = new RespawnTimers();
        UUID a = UUID.randomUUID();
        timers.put(a, 10); timers.put(a, 50);
        assertEquals(List.of(), timers.takeDue(10));
        assertEquals(List.of(a), timers.takeDue(50));
    }
}
