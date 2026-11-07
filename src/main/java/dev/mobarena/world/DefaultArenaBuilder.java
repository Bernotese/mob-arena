package dev.mobarena.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.AABB;

public final class DefaultArenaBuilder {
    private DefaultArenaBuilder() {}
    public static ArenaLayout build(ServerLevel level, long seed) {
        var geometry = ArenaGeometry.create(seed);
        geometry.blocks().forEach((pos, material) -> level.setBlock(point(pos), block(material).defaultBlockState(), Block.UPDATE_CLIENTS));
        return new ArenaLayout(point(geometry.spawn()), geometry.mobs().stream().map(DefaultArenaBuilder::point).toList(),
                geometry.water().stream().map(DefaultArenaBuilder::point).toList(), new AABB(-32, 60, -32, 33, 92, 33));
    }
    private static BlockPos point(ArenaGeometry.Point point) { return new BlockPos(point.x(), point.y(), point.z()); }
    private static Block block(ArenaGeometry.Material material) {
        return switch (material) {
            case STONE -> Blocks.STONE;
            case ANDESITE -> Blocks.ANDESITE;
            case COBBLESTONE -> Blocks.COBBLESTONE;
            case BEDROCK -> Blocks.BEDROCK;
            case BARRIER -> Blocks.BARRIER;
            case POLISHED_ANDESITE -> Blocks.POLISHED_ANDESITE;
            case STONE_BRICKS -> Blocks.STONE_BRICKS;
            case WATER -> Blocks.WATER;
            case MOSSY_COBBLESTONE -> Blocks.MOSSY_COBBLESTONE;
            case SEA_LANTERN -> Blocks.SEA_LANTERN;
        };
    }
}
