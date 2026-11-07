package dev.mobarena.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import java.util.List;

/** bounds.minY is the floor height used by T7. */
public record ArenaLayout(BlockPos teamSpawn, List<BlockPos> mobSpawns,
                          List<BlockPos> waterSpawns, AABB bounds) {
    public ArenaLayout {
        teamSpawn = teamSpawn.immutable();
        mobSpawns = mobSpawns.stream().map(BlockPos::immutable).toList();
        waterSpawns = waterSpawns.stream().map(BlockPos::immutable).toList();
    }
}
