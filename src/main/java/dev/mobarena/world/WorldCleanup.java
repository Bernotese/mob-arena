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
    /** Game levels that are open; Fantasy deletes them without close(), so this module closes them. */
    private static final Set<net.minecraft.server.level.ServerLevel> OPEN = Collections.newSetFromMap(new IdentityHashMap<>());
    static void track(RuntimeLevelHandle handle) { HANDLES.add(handle); }
    static void trackOpen(net.minecraft.server.level.ServerLevel level) { OPEN.add(level); }

    /**
     * Fantasy's delete removes the level from the server map, fires UNLOAD, then deletes the folder.
     * It never calls close(), so the chunk I/O worker stays open and can reschedule into the shared
     * I/O pool after the dedicated server shuts it down. Close only levels that left the map:
     * vanilla still closes levels that remain in it.
     */
    static void onUnload(MinecraftServer server, net.minecraft.server.level.ServerLevel level) {
        if (!OPEN.remove(level) || server.getLevel(level.dimension()) == level) return;
        long start = System.nanoTime();
        try { closeWithoutSave(level); }
        catch (Exception e) { MobArena.LOGGER.error("Cannot close removed game level {}", level.dimension().identifier(), e); }
        long ms = (System.nanoTime() - start) / 1_000_000;
        // This runs on the server thread, so a slow close shows up in the log.
        if (ms >= 250) MobArena.LOGGER.info("Closed removed game level {} in {} ms", level.dimension().identifier(), ms);
        else MobArena.LOGGER.debug("Closed removed game level {} in {} ms", level.dimension().identifier(), ms);
    }

    /**
     * ServerLevel.close() saves every loaded chunk and entity with a flush, and each I/O worker then
     * writes its whole queue (Fantasy unloads all chunks before the delete) before it shuts down.
     * That blocks the server thread for seconds per level. The folder is deleted next, so drop the
     * queued writes, skip the save, and only close the storages.
     */
    private static void closeWithoutSave(net.minecraft.server.level.ServerLevel level) throws java.io.IOException {
        var chunkMap = level.getChunkSource().chunkMap;
        var entities = (net.minecraft.world.level.chunk.storage.EntityStorage) ((dev.mobarena.world.mixin.EntitySectionManagerAccessor)
                ((dev.mobarena.world.mixin.ServerLevelAccessor) level).mobarena$entityManager()).mobarena$permanentStorage();
        var poi = ((dev.mobarena.world.mixin.SectionStorageAccessor) ((dev.mobarena.world.mixin.ChunkMapAccessor) chunkMap).mobarena$poiManager()).mobarena$storage();
        var drops = new ArrayList<java.util.concurrent.CompletableFuture<Object>>();
        for (var storage : List.of(chunkMap, poi, ((dev.mobarena.world.mixin.EntityStorageAccessor) entities).mobarena$storage())) {
            var worker = (dev.mobarena.world.mixin.IOWorkerAccessor) ((dev.mobarena.world.mixin.SimpleRegionStorageAccessor) storage).mobarena$worker();
            // pendingWrites belongs to the worker thread; clear it there, ahead of the queued stores.
            drops.add(worker.mobarena$executor().scheduleWithResult(IO_FOREGROUND, done -> {
                worker.mobarena$pendingWrites().clear();
                done.complete(null);
            }));
        }
        for (var drop : drops) {
            try { drop.get(5, java.util.concurrent.TimeUnit.SECONDS); }
            catch (Exception e) { MobArena.LOGGER.warn("Cannot drop queued writes of {}: {}", level.dimension().identifier(), e.toString()); }
        }
        // Worldgen and light dispatchers, POI storage, and chunk storage. No fsync: the folder is deleted next.
        CLOSING_DELETED.set(true);
        try {
            chunkMap.close();
            entities.close();
        } finally { CLOSING_DELETED.remove(); }
        // Saved data: do not save the dirty data again, only wait for writes that already run.
        var pendingData = ((dev.mobarena.world.mixin.SavedDataStorageAccessor) level.getDataStorage()).mobarena$pendingWrites();
        if (pendingData != null) {
            try { pendingData.get(5, java.util.concurrent.TimeUnit.SECONDS); }
            catch (Exception e) { MobArena.LOGGER.warn("Cannot wait for saved data of {}: {}", level.dimension().identifier(), e.toString()); }
        }
    }
    private static final ThreadLocal<Boolean> CLOSING_DELETED = ThreadLocal.withInitial(() -> false);
    /** True only on the thread that closes a removed game level (see RegionFileSyncMixin). */
    public static boolean closingDeletedLevel() { return CLOSING_DELETED.get(); }
    /** IOWorker.Priority.FOREGROUND.ordinal(). */
    private static final int IO_FOREGROUND = 0;

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
