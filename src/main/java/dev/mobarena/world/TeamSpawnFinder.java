package dev.mobarena.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.PlayerSpawnFinder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;

public final class TeamSpawnFinder {
    private TeamSpawnFinder() {}
    public static BlockPos find(ServerLevel level) {
        ChunkPos origin = level.getChunkSource().getGenerator().getOrigin(level.getChunkSource().randomState());
        for (int radius = 0; radius <= 5; radius++) {
            for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                BlockPos spawn = PlayerSpawnFinder.getSpawnPosInChunk(level, new ChunkPos(origin.x() + dx, origin.z() + dz));
                if (spawn != null) return spawn.immutable();
            }
        }
        BlockPos pos = origin.getWorldPosition();
        return new BlockPos(pos.getX(), level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ()), pos.getZ());
    }
}
