package dev.mobarena.wave;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WavePlanTest {
    @Test void copiesAllInputsAndKeepsAuthor() {
        var wave = new TreeMap<>(Map.of("zombie", 2));
        var waves = new ArrayList<SortedMap<String, Integer>>(List.of(wave, new TreeMap<>(), new TreeMap<>()));
        var plan = new WavePlan<>("RED", waves);
        wave.put("zombie", 99);
        waves.clear();
        assertEquals("RED", plan.author());
        assertEquals(3, plan.waves().size());
        assertEquals(2, plan.waves().get(0).get("zombie"));
        assertThrows(UnsupportedOperationException.class, () -> plan.waves().clear());
        assertThrows(UnsupportedOperationException.class, () -> plan.waves().get(0).clear());
    }
    @Test void validatesShapeAndCounts() {
        assertThrows(IllegalArgumentException.class, () -> new WavePlan<>("RED", List.of()));
        assertThrows(NullPointerException.class, () -> new WavePlan<>(null,
                List.of(new TreeMap<>(), new TreeMap<>(), new TreeMap<>())));
        for (int count : new int[]{0, -1}) {
            assertThrows(IllegalArgumentException.class, () -> new WavePlan<>("RED",
                    List.of(new TreeMap<>(Map.of("zombie", count)), new TreeMap<>(), new TreeMap<>())));
        }
    }
}
