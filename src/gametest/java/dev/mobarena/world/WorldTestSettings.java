package dev.mobarena.world;

import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/** GameTest uses FLAT_ALL_DIMENSIONS with structures off. Restore normal settings for this lifecycle test. */
final class WorldTestSettings implements AutoCloseable {
    private final WorldGenSettings settings;
    private final java.lang.reflect.Field optionsField, dimensionsField;
    private final Object options, dimensions;
    WorldTestSettings(MinecraftServer server) {
        settings = server.getWorldGenSettings();
        options = settings.options();
        dimensions = settings.dimensions();
        try {
            optionsField = WorldGenSettings.class.getDeclaredField("options");
            dimensionsField = WorldGenSettings.class.getDeclaredField("dimensions");
            optionsField.setAccessible(true);
            dimensionsField.setAccessible(true);
            var normal = server.registryAccess().lookupOrThrow(Registries.WORLD_PRESET)
                    .getOrThrow(WorldPresets.NORMAL).value().createWorldDimensions();
            dimensionsField.set(settings, normal);
            optionsField.set(settings, settings.options().withStructures(true));
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }
    @Override public void close() {
        try { optionsField.set(settings, options); dimensionsField.set(settings, dimensions); }
        catch (IllegalAccessException e) { throw new IllegalStateException(e); }
    }
}
