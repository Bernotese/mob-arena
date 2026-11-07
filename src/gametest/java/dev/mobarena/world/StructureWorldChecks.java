package dev.mobarena.world;

import dev.mobarena.team.TeamId;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.*;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.structure.Structure;
import xyz.nucleoid.fantasy.*;
import java.util.*;

final class StructureWorldChecks {
    private StructureWorldChecks() {}
    static void verify(GameTestHelper h) {
        var server = h.getLevel().getServer();
        var access = server.registryAccess();
        var generator = new NoiseBasedChunkGenerator(MultiNoiseBiomeSource.createFromPreset(
                access.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                        .getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD)),
                access.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD));
        // GameTest uses a flat overworld. Use the vanilla noise generator for this structure check.
        List<RuntimeLevelHandle> handles = new ArrayList<>();
        var settings = server.getWorldGenSettings();
        var options = settings.options();
        java.lang.reflect.Field field;
        try {
            // The GameTest server disables structures. Enable them only for this synchronous check.
            field = WorldGenSettings.class.getDeclaredField("options");
            field.setAccessible(true);
            field.set(settings, options.withStructures(true));
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        try {
            for (var team : TeamId.values()) {
                var config = LevelFactory.config(server, 1536, WorldKind.PREP_OVERWORLD).setGenerator(generator);
                handles.add(Fantasy.get(server).openTemporaryLevel(
                        Identifier.fromNamespaceAndPath("mobarena", "structure_test_" + UUID.randomUUID()), config));
            }
            var villages = HolderSet.direct(access.lookupOrThrow(Registries.STRUCTURE).getOrThrow(
                    ResourceKey.create(Registries.STRUCTURE, Identifier.withDefaultNamespace("village_plains"))));
            var red = handles.get(0).asLevel();
            var blue = handles.get(1).asLevel();
            var redVillage = red.findNearestMapStructure(villages, BlockPos.ZERO, 100, false);
            var blueVillage = blue.findNearestMapStructure(villages, BlockPos.ZERO, 100, false);
            h.assertTrue(redVillage != null, "W3 village can be located in a runtime noise world");
            h.assertValueEqual(redVillage, blueVillage, "W3 both teams locate the same village");
            // Generate the located chunks so the structure placement is tested, not only its candidate position.
            var chunk = red.getChunk(redVillage.getX() >> 4, redVillage.getZ() >> 4);
            h.assertTrue(!chunk.getAllStarts().isEmpty(), "W3 a village structure is generated");
            for (var pos : new BlockPos[]{new BlockPos(0, 63, 0), new BlockPos(16, 70, 16), new BlockPos(-16, 50, 0)}) {
                h.assertValueEqual(red.getBlockState(pos), blue.getBlockState(pos), "W1 noise terrain agrees for equal seeds");
            }
        } finally {
            for (var handle : handles) handle.delete();
            try { field.set(settings, options); }
            catch (IllegalAccessException e) { throw new IllegalStateException(e); }
        }
    }
}
