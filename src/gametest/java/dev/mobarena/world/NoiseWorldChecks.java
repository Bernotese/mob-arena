package dev.mobarena.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;

final class NoiseWorldChecks {
    private NoiseWorldChecks() {}

    static void verify(GameTestHelper helper, ServerLevel red, ServerLevel blue) {
        helper.assertValueEqual(red.getSeed(), blue.getSeed(), "W1 equal level seeds");
        var redGenerator = red.getChunkSource().getGenerator();
        var blueGenerator = blue.getChunkSource().getGenerator();
        var redState = red.getChunkSource().randomState();
        var blueState = blue.getChunkSource().randomState();
        helper.assertTrue(redState != blueState, "W1 separate random states");
        helper.assertValueEqual(redState.seed(), blueState.seed(), "W1 equal terrain seeds");
        for (int x : new int[]{-16, 0, 16}) {
            int z = 8;
            red.getChunk(x >> 4, z >> 4);
            blue.getChunk(x >> 4, z >> 4);
            helper.assertValueEqual(redGenerator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, red, redState),
                    blueGenerator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, blue, blueState), "W1 equal noise height at " + x);
            // Neighbor features overlap. Compare the noise blocks before trees and ore replacement.
            var redColumn = redGenerator.getBaseColumn(x, z, red, redState);
            var blueColumn = blueGenerator.getBaseColumn(x, z, blue, blueState);
            for (int y = red.getMinY(); y < red.getMaxY(); y++) {
                helper.assertValueEqual(redColumn.getBlock(y), blueColumn.getBlock(y), "W1 equal noise block at " + x + "," + y);
            }
            for (int y : new int[]{0, 63, 80}) {
                var pos = new BlockPos(x, y, z);
                helper.assertValueEqual(red.getBiome(pos), blue.getBiome(pos), "W1 equal biome at " + pos);
            }
        }
    }

    static void safeSpawn(GameTestHelper helper, ServerLevel level, BlockPos spawn) {
        var head = spawn.above();
        var below = spawn.below();
        helper.assertTrue(level.getFluidState(spawn).isEmpty() && level.getFluidState(head).isEmpty(), "Spawn has no fluid");
        helper.assertTrue(level.getBlockState(spawn).getCollisionShape(level, spawn).isEmpty(), "Spawn feet are clear");
        helper.assertTrue(level.getBlockState(head).getCollisionShape(level, head).isEmpty(), "Spawn head is clear");
        helper.assertTrue(Block.isFaceFull(level.getBlockState(below).getCollisionShape(level, below), Direction.UP),
                "Spawn has solid support");
    }
}
