package dev.mobarena.world;

import org.junit.jupiter.api.Test;
import java.util.*;
import static dev.mobarena.world.ArenaGeometry.*;
import static org.junit.jupiter.api.Assertions.*;

class ArenaGeometryTest {
    @Test void floorSpawnsAndBasinStayInsideTheWall() {
        var arena = create(55);
        assertEquals(arena, create(55));
        assertNotEquals(arena.blocks(), create(56).blocks());
        assertEquals(Material.POLISHED_ANDESITE, arena.blocks().get(new Point(0, 63, 0)));
        assertEquals(16, new HashSet<>(arena.mobs()).size());
        for (var pos : arena.mobs()) {
            assertTrue(pos.x() * pos.x() + pos.z() * pos.z() <= 30 * 30);
            assertEquals(64, pos.y());
            assertEquals(Material.MOSSY_COBBLESTONE, arena.blocks().get(new Point(pos.x(), 63, pos.z())));
            assertNull(arena.blocks().get(pos));
        }
        for (var pos : arena.water()) {
            assertTrue(pos.x() * pos.x() + pos.z() * pos.z() < 31 * 31);
            assertEquals(Material.WATER, arena.blocks().get(pos));
        }
        for (int x = -32; x <= 32; x++) for (int z = -32; z <= 32; z++) {
            int r2 = x*x + z*z;
            if (r2 <= 30*30) assertNotNull(arena.blocks().get(new Point(x, 63, z)));
            if (r2 > 30*30 && r2 < 31*31 && !(x >= 14 && x <= 22 && z >= 14 && z <= 22)) assertFalse(arena.blocks().containsKey(new Point(x, 63, z)));
            if (r2 >= 31*31 && r2 <= 32*32) {
                for (int y = 62; y <= 91; y++) assertEquals(Material.BARRIER, arena.blocks().get(new Point(x, y, z)));
            }
            if (r2 <= 32*32) assertEquals(Material.BARRIER, arena.blocks().get(new Point(x, 91, z)));
        }
    }
    @Test void wallHasNoCardinalEscapeAtAnyHeight() {
        var arena = create(55);
        for (int y : new int[]{62,63,64,90}) {
            Set<Point> visited = new HashSet<>();
            Deque<Point> pending = new ArrayDeque<>();
            pending.add(new Point(0, y, 0));
            while (!pending.isEmpty()) {
                Point p = pending.remove();
                if (!visited.add(p) || arena.blocks().get(p) == Material.BARRIER) continue;
                assertTrue(Math.abs(p.x()) < 33 && Math.abs(p.z()) < 33, "Closed wall");
                pending.add(new Point(p.x()+1, y, p.z()));
                pending.add(new Point(p.x()-1, y, p.z()));
                pending.add(new Point(p.x(), y, p.z()+1));
                pending.add(new Point(p.x(), y, p.z()-1));
            }
        }
    }
}
