package dev.mobarena.world;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PortalLinkTest {
    @Test void teamTargets() {
        assertEquals(WorldKind.PREP_NETHER, PortalLink.kindFor("minecraft:the_nether"));
        assertEquals(WorldKind.PREP_END, PortalLink.kindFor("minecraft:the_end"));
        assertEquals(WorldKind.PREP_OVERWORLD, PortalLink.kindFor("minecraft:overworld"));
        assertEquals(WorldKind.PREP_OVERWORLD, PortalLink.kindFor("test:custom"));
    }
}
