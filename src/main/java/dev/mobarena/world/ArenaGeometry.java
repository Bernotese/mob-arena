package dev.mobarena.world;

import java.util.*;

/** Pure geometry shared by the builder and JUnit tests. */
public final class ArenaGeometry {
    public static final int FLOOR_Y = 63;
    public static final int RADIUS = 30;
    public static final int ROOF_Y = 91;
    public record Point(int x, int y, int z) {}
    public enum Material { STONE, ANDESITE, COBBLESTONE, BEDROCK, BARRIER, POLISHED_ANDESITE, STONE_BRICKS, WATER, MOSSY_COBBLESTONE, SEA_LANTERN }
    public record Blueprint(Map<Point, Material> blocks, Point spawn, List<Point> mobs, List<Point> water) {
        public Blueprint { blocks = Map.copyOf(blocks); mobs = List.copyOf(mobs); water = List.copyOf(water); }
    }
    private ArenaGeometry() {}
    public static Blueprint create(long seed) {
        Map<Point, Material> blocks = new HashMap<>();
        var random = new Random(seed);
        for (int x = -32; x <= 32; x++) for (int z = -32; z <= 32; z++) {
            int r2 = x * x + z * z;
            if (r2 <= 31 * 31) blocks.put(new Point(x, 62, z), Material.BEDROCK);
            if (r2 <= RADIUS * RADIUS) blocks.put(new Point(x, FLOOR_Y, z), switch (random.nextInt(5)) {
                case 0 -> Material.ANDESITE;
                case 1 -> Material.COBBLESTONE;
                default -> Material.STONE;
            });
            if (r2 >= 31 * 31 && r2 <= 32 * 32) {
                for (int y = 62; y < ROOF_Y; y++) blocks.put(new Point(x, y, z), Material.BARRIER);
            }
            if (r2 <= 32 * 32) blocks.put(new Point(x, ROOF_Y, z), Material.BARRIER);
        }
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            blocks.put(new Point(x, FLOOR_Y, z), Material.POLISHED_ANDESITE);
        }
        for (int n = 0; n < 8; n++) {
            double angle = n * Math.PI / 4;
            int x = (int) Math.round(14 * Math.cos(angle));
            int z = (int) Math.round(14 * Math.sin(angle));
            for (int dx = 0; dx < 2; dx++) for (int dz = 0; dz < 2; dz++) for (int y = 64; y < 68; y++) {
                blocks.put(new Point(x + dx, y, z + dz), y == 66 ? Material.SEA_LANTERN : Material.STONE_BRICKS);
            }
        }
        for (int n = 0; n < 4; n++) {
            double angle = Math.PI / 4 + n * Math.PI / 2;
            int x = (int) Math.round(22 * Math.cos(angle));
            int z = (int) Math.round(22 * Math.sin(angle));
            for (int t = -2; t <= 2; t++) for (int y = 64; y <= 65; y++) {
                blocks.put(new Point(x + t, y, z), Material.COBBLESTONE);
            }
        }
        for (int x = 14; x <= 22; x++) for (int z = 14; z <= 22; z++) {
            boolean rim = x == 14 || x == 22 || z == 14 || z == 22;
            blocks.put(new Point(x, 60, z), Material.STONE_BRICKS);
            for (int y = 61; y <= 63; y++) {
                if (x * x + z * z < 31 * 31) blocks.put(new Point(x, y, z), rim ? Material.STONE_BRICKS : Material.WATER);
            }
        }
        List<Point> mobs = new ArrayList<>();
        for (int n = 0; n < 16; n++) {
            double angle = n * Math.PI / 8;
            int x = (int) Math.round(27 * Math.cos(angle));
            int z = (int) Math.round(27 * Math.sin(angle));
            blocks.put(new Point(x, FLOOR_Y, z), Material.MOSSY_COBBLESTONE);
            // Ensure each ring point stays dry, including the point near the basin.
            blocks.remove(new Point(x, 64, z));
            blocks.remove(new Point(x, 65, z));
            mobs.add(new Point(x, 64, z));
        }
        return new Blueprint(blocks, new Point(0, 64, 0), mobs, List.of(new Point(18, 62, 18)));
    }
}
