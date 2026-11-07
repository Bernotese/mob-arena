package dev.mobarena.collect;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MobPoolTest {
    @Test void countsAndIndependentSortedSnapshots() {
        MobPool pool = new MobPool();
        assertEquals(0, pool.total());
        assertTrue(pool.snapshot().isEmpty());
        assertEquals(0, pool.count("missing"));
        pool.add("minecraft:zombie");
        pool.add("minecraft:cow");
        pool.add("minecraft:zombie");
        assertEquals(2, pool.count("minecraft:zombie"));
        assertEquals(1, pool.count("minecraft:cow"));
        assertEquals(3, pool.total());
        var snapshot = pool.snapshot();
        assertEquals(List.of("minecraft:cow", "minecraft:zombie"), new ArrayList<>(snapshot.keySet()));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.put("x", 1));
        pool.add("minecraft:zombie");
        assertEquals(2, snapshot.get("minecraft:zombie"));
    }
}
