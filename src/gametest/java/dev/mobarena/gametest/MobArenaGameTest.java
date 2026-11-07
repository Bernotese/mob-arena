package dev.mobarena.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

public class MobArenaGameTest {
	// Example: place a block and check it in the test world.
	@GameTest
	public void placeBlock(GameTestHelper helper) {
		BlockPos pos = new BlockPos(0, 1, 0);
		helper.setBlock(pos, Blocks.DIAMOND_BLOCK);
		helper.assertBlockPresent(Blocks.DIAMOND_BLOCK, pos);
		helper.succeed();
	}
}
