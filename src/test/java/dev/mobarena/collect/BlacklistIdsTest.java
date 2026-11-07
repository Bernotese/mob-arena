package dev.mobarena.collect;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class BlacklistIdsTest {
    @Test void normalizesDefaultAndExplicitNamespaces() {
        assertEquals(Set.of("minecraft:ender_dragon","minecraft:cod","other:mob"),
                BlacklistIds.normalize(List.of("ender_dragon","minecraft:cod","other:mob"," cod ")));
        assertThrows(UnsupportedOperationException.class,()->BlacklistIds.normalize(List.of("cod")).clear());
    }
}
