package dev.mobarena.wave;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WaveSetupTest {
    @Test void copiesInputAndIgnoresNonpositiveCounts() {
        var input = new HashMap<>(Map.of("zombie", 100, "cow", 0, "pig", -1));
        var setup = new WaveSetup(input);
        input.put("zombie", 5);
        assertEquals(Map.of("zombie", 100), setup.pool());
        assertEquals(100, setup.poolTotal());
        assertThrows(UnsupportedOperationException.class, () -> setup.pool().clear());
        assertThrows(UnsupportedOperationException.class, () -> setup.wave(1).put("x", 1));
    }
    @Test void movesOneTenAllAndClampsInBothDirections() {
        var setup = new WaveSetup(Map.of("zombie", 25));
        assertEquals(1, setup.toWave("zombie", 1, 1));
        assertEquals(10, setup.toWave("zombie", 1, 10));
        assertEquals(14, setup.toWave("zombie", 1, Integer.MAX_VALUE));
        assertTrue(setup.pool().isEmpty());
        assertEquals(25, setup.waveCount(1, "zombie"));
        assertEquals(25, setup.waveTotal(1));
        assertEquals(1, setup.toPool(1, "zombie", 1));
        assertEquals(10, setup.toPool(1, "zombie", 10));
        assertEquals(14, setup.toPool(1, "zombie", Integer.MAX_VALUE));
        assertTrue(setup.wave(1).isEmpty());
        assertEquals(25, setup.poolCount("zombie"));
        assertEquals(25, setup.toWave("zombie", 2, 50));
        assertEquals(25, setup.toPool(2, "zombie", 50));
    }
    @Test void invalidMovesAndVersion() {
        var setup = new WaveSetup(Map.of("zombie", 12));
        assertEquals(0, setup.version());
        assertEquals(0, setup.toWave("missing", 1, 1));
        assertEquals(0, setup.toWave("zombie", 1, 0));
        assertEquals(0, setup.toWave("zombie", 1, -1));
        assertEquals(0, setup.toPool(1, "missing", 1));
        assertEquals(0, setup.toPool(1, "zombie", 0));
        assertEquals(0, setup.toPool(1, "zombie", -1));
        assertEquals(0, setup.version());
        setup.toWave("zombie", 1, 10);
        assertEquals(1, setup.version());
        setup.toPool(1, "zombie", 1);
        assertEquals(2, setup.version());
        for (int w : new int[]{0, 4}) {
            assertThrows(IllegalArgumentException.class, () -> setup.toWave("zombie", w, 1));
            assertThrows(IllegalArgumentException.class, () -> setup.toPool(w, "zombie", 1));
            assertThrows(IllegalArgumentException.class, () -> setup.wave(w));
            assertThrows(IllegalArgumentException.class, () -> setup.waveCount(w, "zombie"));
            assertThrows(IllegalArgumentException.class, () -> setup.waveTotal(w));
        }
    }
    @Test void finishAddsLeftoversAndLocksMoves() {
        var setup = new WaveSetup(Map.of("zombie", 12, "cow", 3));
        setup.toWave("zombie", 3, 2);
        setup.finish();
        assertTrue(setup.finished());
        assertEquals(Map.of("zombie", 12, "cow", 3), setup.wave(3));
        assertTrue(setup.pool().isEmpty());
        assertTrue(setup.wave(1).isEmpty());
        assertTrue(setup.wave(2).isEmpty());
        assertEquals(2, setup.version());
        setup.finish();
        assertEquals(2, setup.version());
        assertThrows(IllegalStateException.class, () -> setup.toWave("zombie", 1, 0));
        assertThrows(IllegalStateException.class, () -> setup.toPool(3, "zombie", 1));
        var empty = new WaveSetup(Map.of());
        empty.finish();
        assertTrue(empty.finished());
        assertEquals(0, empty.version());
    }
    @Test void snapshotsAreDeeplyImmutableAndIndependent() {
        var setup = new WaveSetup(Map.of("zombie", 12, "cow", 3));
        setup.toWave("zombie", 1, 2);
        setup.toWave("cow", 1, 1);
        var snapshot = setup.snapshot();
        assertEquals(3, snapshot.size());
        assertEquals(List.of("cow", "zombie"), new ArrayList<>(snapshot.get(0).keySet()));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.get(0).clear());
        setup.toWave("zombie", 1, 2);
        assertEquals(2, snapshot.get(0).get("zombie"));
    }
    @Test void randomMovesConserveEveryType() {
        var initial = Map.of("zombie", 123, "cow", 77, "pig", 18);
        var setup = new WaveSetup(initial);
        var random = new Random(610);
        var ids = new ArrayList<>(initial.keySet());
        for (int i = 0; i < 1000; i++) {
            String id = ids.get(random.nextInt(ids.size()));
            int wave = random.nextInt(3) + 1;
            int amount = switch (random.nextInt(4)) {
                case 0 -> 1;
                case 1 -> 10;
                case 2 -> Integer.MAX_VALUE;
                default -> 0;
            };
            long before = setup.version();
            int moved = random.nextBoolean() ? setup.toWave(id, wave, amount) : setup.toPool(wave, id, amount);
            assertEquals(before + (moved > 0 ? 1 : 0), setup.version());
            initial.forEach((type, count) -> {
                int sum = setup.poolCount(type);
                assertTrue(sum >= 0);
                for (int w = 1; w <= 3; w++) {
                    assertTrue(setup.waveCount(w, type) >= 0);
                    sum += setup.waveCount(w, type);
                }
                assertEquals(count.intValue(), sum);
            });
        }
        setup.finish();
        initial.forEach((type, count) -> assertEquals(count.intValue(),
                setup.waveCount(1, type) + setup.waveCount(2, type) + setup.waveCount(3, type)));
    }
}
