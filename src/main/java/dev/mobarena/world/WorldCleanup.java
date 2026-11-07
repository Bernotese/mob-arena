package dev.mobarena.world;

import dev.mobarena.MobArena;
import dev.mobarena.core.*;
import net.minecraft.server.MinecraftServer;
import xyz.nucleoid.fantasy.RuntimeLevelHandle;
import java.util.*;

/** Tracks handles immediately, including partially created teams. */
public final class WorldCleanup implements PhaseListener {
    private static final Set<RuntimeLevelHandle> HANDLES = new LinkedHashSet<>();
    private static final Map<net.minecraft.server.level.ServerLevel, Set<net.minecraft.world.level.ChunkPos>> FORCED = new IdentityHashMap<>();
    public static void force(net.minecraft.server.level.ServerLevel level, int x, int z) {
        level.setChunkForced(x, z, true);
        FORCED.computeIfAbsent(level, ignored -> new HashSet<>()).add(new net.minecraft.world.level.ChunkPos(x, z));
    }
    /** Deleted level directories, with the ticks left to delete them again. */
    private static final Map<java.nio.file.Path, Integer> SWEEP = new LinkedHashMap<>();
    private static final int SWEEP_TICKS = 200;
    private WorldCleanup() {}
    public static final WorldCleanup INSTANCE = new WorldCleanup();
    static void track(RuntimeLevelHandle handle) { HANDLES.add(handle); }

    public static void clearRespawnPoints(MinecraftServer server) {
        for (var player : server.getPlayerList().getPlayers()) {
            var respawn = player.getRespawnConfig();
            if (respawn != null && "mobarena".equals(respawn.respawnData().dimension().identifier().getNamespace())) {
                player.setRespawnPosition(null, false);
            }
        }
    }

    /** Remove only this module's runtime directories. Never follow directory symlinks. */
    public static void removeCrashLeftovers(MinecraftServer server) {
        var parent = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("dimensions/mobarena");
        try { removeCrashLeftovers(parent); }
        catch (java.io.IOException e) { MobArena.LOGGER.error("Cannot delete old game level directories", e); }
    }
    static void removeCrashLeftovers(java.nio.file.Path parent) throws java.io.IOException {
        if (!java.nio.file.Files.isDirectory(parent, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return;
        try (var children = java.nio.file.Files.list(parent)) {
            for (var path : children.toList()) {
                if (!path.getFileName().toString().matches("(red|blue)_(overworld|nether|end|arena)(_[a-z0-9_]+)?")) continue;
                try (var entries = java.nio.file.Files.walk(path)) {
                    for (var entry : entries.sorted(java.util.Comparator.reverseOrder()).toList()) java.nio.file.Files.delete(entry);
                }
            }
        }
    }

    public static void clean(MinecraftServer server) {
        dev.mobarena.randomizer.RandomizerModule.hook().clear();
        clearRespawnPoints(server);
        var handles = HANDLES.stream().filter(h -> h.asLevel().getServer() == server).toList();
        for (var handle : handles) {
            var level = handle.asLevel();
            for (var chunk : FORCED.getOrDefault(level, Set.of())) {
                level.setChunkForced(chunk.x(), chunk.z(), false);
            }
            FORCED.remove(level);
            var id = level.dimension().identifier();
            SWEEP.put(server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("dimensions")
                    .resolve(id.getNamespace()).resolve(id.getPath()), SWEEP_TICKS);
            handle.delete();
            GameLevels.remove(level);
            HANDLES.remove(handle);
        }
    }
    /**
     * Fantasy deletes the directory while async chunk writes can still be queued; a late write recreates
     * the region file. Delete such leftovers again for a short time after the level is gone.
     */
    static void sweep(MinecraftServer server) {
        if (SWEEP.isEmpty()) return;
        var it = SWEEP.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            int left = entry.getValue() - 1;
            if (left % 20 == 0 && java.nio.file.Files.isDirectory(entry.getKey(), java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                try (var entries = java.nio.file.Files.walk(entry.getKey())) {
                    for (var path : entries.sorted(java.util.Comparator.reverseOrder()).toList()) java.nio.file.Files.deleteIfExists(path);
                } catch (java.io.IOException | java.io.UncheckedIOException e) {
                    MobArena.LOGGER.warn("Cannot delete game level leftovers in {}", entry.getKey(), e);
                }
            }
            if (left <= 0) it.remove(); else entry.setValue(left);
        }
    }
    @Override public void onEnter(Phase phase, GameContext ctx) {
        if (phase == Phase.LOBBY && ctx.server() != null) {
            clean(ctx.server());
        }
    }
}
