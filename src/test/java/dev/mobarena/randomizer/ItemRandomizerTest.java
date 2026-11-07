package dev.mobarena.randomizer;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ItemRandomizerTest {
    private final List<Integer> items = IntStream.range(0, 100).boxed().toList();

    @Test void seedAndTeamAreRepeatable() {
        assertEquals(ItemRandomizer.create(42, "RED", items, i -> true),
                ItemRandomizer.create(42, "RED", items, i -> true));
    }

    @Test void teamsAndGamesHaveDifferentMaps() {
        var red = ItemRandomizer.create(42, "RED", items, i -> true);
        assertNotEquals(red, ItemRandomizer.create(42, "BLUE", items, i -> true));
        assertNotEquals(red, ItemRandomizer.create(43, "RED", items, i -> true));
        assertEquals(items.size(), red.values().stream().distinct().count());
    }

    @Test void blacklistExcludesSourcesAndTargets() {
        var map = ItemRandomizer.create(42, "RED", items, i -> i > 10);
        assertTrue(map.keySet().stream().allMatch(i -> i > 10));
        assertTrue(map.values().stream().allMatch(i -> i > 10));
        assertEquals(89, map.size());
    }

    @Test void countKeepsSmallStacksAndCapsLargeStacks() {
        assertEquals(12, ItemRandomizer.capCount(12, 64));
        assertEquals(16, ItemRandomizer.capCount(64, 16));
        assertEquals(1, ItemRandomizer.capCount(64, 1));
    }
}
