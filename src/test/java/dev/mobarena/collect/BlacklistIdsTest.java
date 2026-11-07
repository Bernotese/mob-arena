package dev.mobarena.collect;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class BlacklistIdsTest {
    @Test void usesLocaleIndependentCase() {
        var previous=Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertEquals("minecraft:iron_golem",BlacklistIds.normalize("IRON_GOLEM"));
        } finally { Locale.setDefault(previous); }
    }
    @Test void normalizesDefaultAndExplicitNamespaces() {
        assertEquals(Set.of("minecraft:ender_dragon","minecraft:cod","other:mob"),
                BlacklistIds.normalize(List.of("ender_dragon","minecraft:cod","OTHER:MOB"," COD ")));
        assertThrows(UnsupportedOperationException.class,()->BlacklistIds.normalize(List.of("cod")).clear());
    }
}
