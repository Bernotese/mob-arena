package dev.mobarena.world;

import dev.mobarena.gametest.TestEnd;
import dev.mobarena.team.TeamId;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import java.util.*;
import java.util.concurrent.*;

/**
 * Stronghold, fortress and End city pieces use static mutable state in vanilla. The same start in RED
 * and BLUE (same seed), generated at the same time, must give the same pieces as a lone generation.
 */
public class StructureLockGameTest {
    private static final int THREADS = 4, ROUNDS = 25;

    private static Structure.GenerationContext context(ServerLevel level, ChunkPos chunk) {
        var source = level.getChunkSource();
        var generator = source.getGenerator();
        return new Structure.GenerationContext(level.registryAccess(), generator, generator.getBiomeSource(),
                source.randomState().createClimateSampler(net.minecraft.world.level.levelgen.densityfunction.SamplerContext.builder().enableCaches().build()),
                source.randomState(), level.getServer().getStructureTemplateManager(),
                level.getSeed(), chunk, level, biome -> true);
    }
    /** Piece list as text, or null if the structure has no start here. */
    private static String pieces(Structure structure, ServerLevel level, ChunkPos chunk) {
        if (structure instanceof net.minecraft.world.level.levelgen.structure.structures.EndCityStructure endCity)
            return endCityPieces(endCity, level, chunk);
        var stub = structure.findValidGenerationPoint(context(level, chunk));
        if (stub.isEmpty()) return null;
        return text(stub.get().position(), stub.get().getPiecesBuilder());
    }
    /**
     * End city starts need outer-island terrain; the race is in the piece generation only, so call the
     * (locked) generatePieces at a fixed position.
     */
    private static String endCityPieces(net.minecraft.world.level.levelgen.structure.structures.EndCityStructure endCity,
                                        ServerLevel level, ChunkPos chunk) {
        var builder = new net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder();
        var pos = new net.minecraft.core.BlockPos(chunk.getMiddleBlockX(), 70, chunk.getMiddleBlockZ());
        try {
            var method = net.minecraft.world.level.levelgen.structure.structures.EndCityStructure.class.getDeclaredMethod("generatePieces",
                    net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder.class, net.minecraft.core.BlockPos.class,
                    net.minecraft.world.level.block.Rotation.class, Structure.GenerationContext.class);
            method.setAccessible(true);
            method.invoke(endCity, builder, pos, net.minecraft.world.level.block.Rotation.NONE, context(level, chunk));
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        return text(pos, builder);
    }
    private static String text(net.minecraft.core.BlockPos position, net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder builder) {
        var out = new StringBuilder(position.toShortString());
        for (var piece : builder.build().pieces())
            out.append('\n').append(piece.getType()).append(' ').append(piece.getBoundingBox()).append(' ')
               .append(piece.getOrientation()).append(' ').append(piece.getGenDepth());
        return out.toString();
    }

    private static void check(GameTestHelper helper, ResourceKey<Structure> key, WorldKind kind) throws Exception {
        var server = helper.getLevel().getServer();
        ServerLevel red = LevelFactory.open(server, 11, TeamId.RED, kind).asLevel();
        ServerLevel blue = LevelFactory.open(server, 11, TeamId.BLUE, kind).asLevel();
        var holder = server.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(key);
        var structure = holder.value();
        // Vanilla candidate chunks of this structure; stronghold rings use concentric placement.
        var candidates = new ArrayList<ChunkPos>();
        var state = red.getChunkSource().getGeneratorState();
        for (var placement : state.getPlacementsForStructure(holder)) {
            if (placement instanceof net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement spread)
                for (int r = 0; r < 40; r++) for (int dz = -2; dz <= 2; dz++)
                    candidates.add(spread.getPotentialStructureChunk(state.getLevelSeed(), r + 2, dz));
        }
        if (candidates.isEmpty()) for (int x = 0; x < 400; x += 3) candidates.add(new ChunkPos(x, x / 2));
        ChunkPos chunk = null; String expected = null;
        for (var candidate : candidates) {
            expected = pieces(structure, red, candidate);
            if (expected != null) { chunk = candidate; break; }
        }
        helper.assertTrue(expected != null, "No " + key.identifier() + " start found");
        helper.assertValueEqual(pieces(structure, blue, chunk), expected, "Same seed gives the same " + key.identifier());
        var pool = Executors.newFixedThreadPool(THREADS);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                var start = new CountDownLatch(1);
                var futures = new ArrayList<Future<String>>();
                for (int t = 0; t < THREADS; t++) {
                    var level = t % 2 == 0 ? red : blue; var at = chunk;
                    futures.add(pool.submit(() -> { start.await(); return pieces(structure, level, at); }));
                }
                start.countDown();
                for (int t = 0; t < THREADS; t++) {
                    String actual;
                    try { actual = futures.get(t).get(30, TimeUnit.SECONDS); }
                    catch (ExecutionException e) { helper.fail(key.identifier() + " round " + round + " failed: " + e.getCause()); return; }
                    if (!expected.equals(actual))
                        helper.fail(key.identifier() + " round " + round + ": parallel " + (t % 2 == 0 ? "RED" : "BLUE")
                                + " pieces differ (" + (actual == null ? "no start" : actual.lines().count() + " lines") + " vs "
                                + expected.lines().count() + ")");
                }
            }
        } finally { pool.shutdownNow(); }
    }

    /** Jigsaw structure: shared template palettes (T10e bug 1, Palette cache CME). */
    @GameTest(environment = "mobarena:world_lock_village", maxTicks = 400)
    public void parallelVillagesAreEqual(GameTestHelper helper) throws Exception {
        TestEnd.run(helper, () -> WorldCleanup.clean(helper.getLevel().getServer()));
        check(helper, BuiltinStructures.VILLAGE_PLAINS, WorldKind.PREP_OVERWORLD);
        helper.succeed();
    }

    @GameTest(environment = "mobarena:world_lock_stronghold", maxTicks = 400)
    public void parallelStrongholdsAreEqual(GameTestHelper helper) throws Exception {
        TestEnd.run(helper, () -> WorldCleanup.clean(helper.getLevel().getServer()));
        check(helper, BuiltinStructures.STRONGHOLD, WorldKind.PREP_OVERWORLD);
        helper.succeed();
    }

    @GameTest(environment = "mobarena:world_lock_fortress", maxTicks = 400)
    public void parallelFortressesAreEqual(GameTestHelper helper) throws Exception {
        TestEnd.run(helper, () -> WorldCleanup.clean(helper.getLevel().getServer()));
        check(helper, BuiltinStructures.FORTRESS, WorldKind.PREP_NETHER);
        helper.succeed();
    }

    @GameTest(environment = "mobarena:world_lock_end_city", maxTicks = 400)
    public void parallelEndCitiesAreEqual(GameTestHelper helper) throws Exception {
        TestEnd.run(helper, () -> WorldCleanup.clean(helper.getLevel().getServer()));
        check(helper, BuiltinStructures.END_CITY, WorldKind.PREP_END);
        helper.succeed();
    }
}
