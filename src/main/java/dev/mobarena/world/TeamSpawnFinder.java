package dev.mobarena.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

public final class TeamSpawnFinder {
    private TeamSpawnFinder() {}
    public static BlockPos find(ServerLevel level) {
        ChunkPos origin = level.getChunkSource().getGenerator().getOrigin(level.getChunkSource().randomState());
        for (int radius = 0; radius <= 5; radius++) {
            for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                var chunk = new ChunkPos(origin.x() + dx, origin.z() + dz);
                for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++) {
                    for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); z++) {
                        var spawn = safeColumn(level, x, z);
                        if (spawn != null) return spawn;
                    }
                }
            }
        }
        var pos = origin.getWorldPosition();
        return fallback(level, pos.getX(), pos.getZ());
    }

    /** Use the same horizontal position; neighboring decorations can change the safe height. */
    public static BlockPos atColumn(ServerLevel level, BlockPos column) {
        var spawn = safeColumn(level, column.getX(), column.getZ());
        return spawn != null ? spawn : fallback(level, column.getX(), column.getZ());
    }

    private static BlockPos fallback(ServerLevel level, int x, int z) {
        int height = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        var feet = new BlockPos(x, Math.clamp(height, level.getMinY() + 1, level.getMaxY() - 2), z);
        var below = feet.below();
        if (!level.getFluidState(below).isEmpty()
                || !Block.isFaceFull(level.getBlockState(below).getCollisionShape(level, below), Direction.UP)) {
            level.setBlockAndUpdate(below, Blocks.STONE.defaultBlockState());
        }
        // The height limit can put the fallback inside existing blocks.
        level.setBlockAndUpdate(feet, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(feet.above(), Blocks.AIR.defaultBlockState());
        return feet;
    }

    private static BlockPos safeColumn(ServerLevel level, int x, int z) {
        int height = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (height <= level.getMinY() || !level.getFluidState(new BlockPos(x, height - 1, z)).isEmpty()) return null;
        for (int y = Math.max(height, level.getMinY() + 1); y < level.getMaxY() - 1; y++) {
            var feet = new BlockPos(x, y, z);
            var head = feet.above();
            var below = feet.below();
            if (!level.getFluidState(feet).isEmpty() || !level.getFluidState(head).isEmpty()) continue;
            if (!level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                    || !level.getBlockState(head).getCollisionShape(level, head).isEmpty()) continue;
            if (Block.isFaceFull(level.getBlockState(below).getCollisionShape(level, below), Direction.UP)) return feet;
        }
        return null;
    }
}
