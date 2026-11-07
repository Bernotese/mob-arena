package dev.mobarena.debug;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
class WaveSpecTest {
    @Test void parsesCountsAndNamespaces() {
        assertEquals(Map.of("minecraft:zombie", 3, "minecraft:cow", 1), WaveSpec.parse("zombie:3,cow:1"));
        assertEquals(Map.of("minecraft:zombie", 5), WaveSpec.parse("minecraft:zombie:3,zombie:2"));
        assertTrue(WaveSpec.parse("-").isEmpty());
    }
    @Test void rejectsInvalidEntries() {
        for (String value : new String[]{"zombie", "zombie:0", "zombie:-1", "zombie:x", "zombie:1,", "Bad:1"})
            assertThrows(IllegalArgumentException.class, () -> WaveSpec.parse(value));
        assertThrows(ArithmeticException.class, () -> WaveSpec.parse("zombie:2147483647,zombie:1"));
    }
}
