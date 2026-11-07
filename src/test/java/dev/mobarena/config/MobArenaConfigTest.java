package dev.mobarena.config;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class MobArenaConfigTest {
    @TempDir Path directory;
    @Test void roundTripAllFieldsAndStoreUpdates() throws Exception {
        Path path = directory.resolve("mob-arena.json");
        MobArenaConfig config = MobArenaConfig.load(path);
        assertEquals(2700, config.prepTimeSeconds); assertEquals(900, config.waveMaxTimeSeconds); assertEquals(150, config.maxAliveMobs);
        config.prepTimeSeconds = 120; config.randomizerEnabled = false; config.mobBlacklist = List.of("minecraft:zombie"); config.arenaTemplate = "arena_2";
        config.save(path); assertEquals(config.snapshot(), MobArenaConfig.load(path).snapshot());
        ConfigStore store = new ConfigStore(path);
        store.set("waveMaxTimeSeconds", "125"); assertEquals(125, MobArenaConfig.load(path).waveMaxTimeSeconds);
        store.set("randomizerEnabled", "true"); store.set("mobBlacklist", "[\"minecraft:cow\"]");
        assertEquals(List.of("minecraft:cow"), store.current().mobBlacklist);
        MobArenaConfig detached = store.current(); detached.maxAliveMobs = 1; assertEquals(150, store.current().maxAliveMobs);
        store.save(); store.reset(); assertEquals(new MobArenaConfig().snapshot(), MobArenaConfig.load(path).snapshot());
    }
    @Test void rejectsInvalidValuesWithoutSaving() throws Exception {
        Path path = directory.resolve("config.json"); ConfigStore store = new ConfigStore(path); String before = Files.readString(path);
        assertThrows(IllegalArgumentException.class, () -> store.set("prepTimeSeconds", "0"));
        assertThrows(IllegalArgumentException.class, () -> store.set("randomizerEnabled", "yes"));
        assertThrows(IllegalArgumentException.class, () -> store.set("unknown", "1"));
        assertThrows(IllegalArgumentException.class, () -> store.set("arenaTemplate", "../other"));
        assertEquals(before, Files.readString(path));
        var snapshot = store.current().snapshot();
        assertThrows(UnsupportedOperationException.class, () -> snapshot.mobBlacklist().clear());
    }
    @Test void sharedBoundsRejectBothSidesAndAcceptRange() throws Exception {
        ConfigStore store = new ConfigStore(directory.resolve("bounds.json"));
        var missing = assertThrows(IllegalArgumentException.class, () -> ConfigBounds.requireBounds("missingInt"));
        assertTrue(missing.getMessage().contains("No ConfigBounds entry for integer key: missingInt"));
        assertEquals(new ConfigBounds.Bounds(60, 14400), store.bounds("prepTimeSeconds"));
        assertEquals(new ConfigBounds.Bounds(10, 500), store.bounds("maxAliveMobs"));
        store.set("maxAliveMobs", "250"); assertEquals(250, store.current().maxAliveMobs);
        assertThrows(IllegalArgumentException.class, () -> store.set("maxAliveMobs", "9"));
        assertThrows(IllegalArgumentException.class, () -> store.set("maxAliveMobs", "501"));
    }
    @Test void badStartupJsonUsesDefaultsAndPreservesFile() throws Exception {
        Path path = directory.resolve("broken.json"); Files.writeString(path, "{broken");
        ConfigStore store = new ConfigStore(path);
        assertEquals(new MobArenaConfig().snapshot(), store.current().snapshot());
        assertEquals("{broken", Files.readString(path));
        Files.writeString(path, "{\"unknownSetting\": 2, \"prepTimeSeconds\": 120}");
        assertEquals(120, new ConfigStore(path).current().prepTimeSeconds);
    }

}
