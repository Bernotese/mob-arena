package dev.mobarena.arena;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SuddenDeathScriptTest {
    @Test void mergeSumsSameIds() {
        var merged = SuddenDeathScript.merge(List.of(Map.of("minecraft:zombie", 2), Map.of("minecraft:zombie", 3, "minecraft:cow", 1),
                Map.of("minecraft:spider", 4)));
        assertEquals(Map.of("minecraft:zombie", 5, "minecraft:cow", 1, "minecraft:spider", 4), merged);
        assertEquals(List.of("minecraft:cow", "minecraft:spider", "minecraft:zombie"), List.copyOf(merged.keySet()), "Sorted ids");
    }
    @Test void emptyPlanGivesEmptyWave() {
        assertTrue(SuddenDeathScript.merge(List.of(Map.of(), Map.of(), Map.of())).isEmpty());
    }
    @Test void missingPlanGivesEmptyWave() {
        assertTrue(SuddenDeathScript.merge(null).isEmpty());
    }
}
