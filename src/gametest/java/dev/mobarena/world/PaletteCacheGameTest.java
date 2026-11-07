package dev.mobarena.world;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import java.util.*;
import java.util.concurrent.*;

/**
 * Test of the StructurePaletteMixin (main). Structure templates are shared by all levels. RED and BLUE levels use the same seed, so worldgen threads
 * of both levels read the same template palette at the same time (T10e bug 1: CME in Palette.blocks).
 */
public class PaletteCacheGameTest {
    private static final int THREADS = 8, ROUNDS = 40;

    @GameTest(environment = "mobarena:world_palette", maxTicks = 400)
    public void paletteCacheIsThreadSafe(GameTestHelper helper) throws Exception {
        var server = helper.getLevel().getServer();
        var source = server.getStructureTemplateManager().get(Identifier.withDefaultNamespace("village/plains/houses/plains_small_house_1"))
                .orElseThrow(() -> new IllegalStateException("Vanilla village template is missing"));
        var tag = source.save(new CompoundTag());
        var blocks = server.registryAccess().lookupOrThrow(Registries.BLOCK);
        // Many distinct keys: the cache map resizes while other threads read and write it.
        List<Block> keys = BuiltInRegistries.BLOCK.stream().limit(200).toList();
        var settings = new StructurePlaceSettings();
        var expected = new HashMap<Block, Integer>();
        for (var key : keys) expected.put(key, source.filterBlocks(BlockPos.ZERO, settings, key).size());
        var pool = Executors.newFixedThreadPool(THREADS);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                var template = new StructureTemplate();
                template.load(blocks, tag);
                var start = new CountDownLatch(1);
                var futures = new ArrayList<Future<?>>();
                for (int t = 0; t < THREADS; t++) {
                    var order = new ArrayList<>(keys);
                    Collections.shuffle(order, new Random(round * 31L + t));
                    futures.add(pool.submit(() -> {
                        start.await();
                        for (var key : order) {
                            int size = template.filterBlocks(BlockPos.ZERO, settings, key).size();
                            if (size != expected.get(key)) throw new IllegalStateException("Wrong block list for " + key + ": " + size);
                        }
                        template.getJigsaws(BlockPos.ZERO, net.minecraft.world.level.block.Rotation.NONE);
                        return null;
                    }));
                }
                start.countDown();
                for (var future : futures) {
                    try { future.get(30, TimeUnit.SECONDS); }
                    catch (ExecutionException e) { helper.fail("Round " + round + ": parallel palette read failed: " + e.getCause()); return; }
                }
            }
        } finally { pool.shutdownNow(); }
        helper.succeed();
    }
}
