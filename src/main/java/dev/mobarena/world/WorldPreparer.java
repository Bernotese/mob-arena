package dev.mobarena.world;

import dev.mobarena.MobArena;
import dev.mobarena.team.TeamId;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import xyz.nucleoid.fantasy.RuntimeLevelHandle;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;

/**
 * Prepares the prep worlds before PREP starts, without blocking the server thread for long.
 * Chunks load through tickets on the worldgen threads; each search step runs on the server thread
 * only when its chunks are loaded. It uses the same ring search as the blocking path, so both
 * paths pick the same spawn, and BLUE uses RED's spawn X/Z.
 */
public final class WorldPreparer implements dev.mobarena.core.StartPreparer {
    public static final WorldPreparer INSTANCE = new WorldPreparer();
    /** Keeps the searched chunks loaded until SPAWN_TICKET_TICKS after the start. Not saved, no timeout. */
    private static TicketType ticket;
    private static Job job;
    private static Prepared prepared;
    /** Chunks around the spawn that the start loads before the teleport. */
    private static final int SPAWN_RADIUS = 2;
    private static final int SPAWN_TICKET_TICKS = 200;
    private record Delayed(int tick, Runnable action) {}
    private static final List<Delayed> delayed = new ArrayList<>();

    private WorldPreparer() {}

    public record Prepared(MinecraftServer server, long seed, Map<TeamId, TeamWorlds> worlds) {}

    static void init() {
        ticket = Registry.register(BuiltInRegistries.TICKET_TYPE, Identifier.fromNamespaceAndPath("mobarena", "prep_spawn"),
                new TicketType(TicketType.NO_TIMEOUT, TicketType.FLAG_LOADING));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (delayed.isEmpty()) return;
            var due = delayed.stream().filter(d -> d.tick() <= server.getTickCount()).toList();
            delayed.removeAll(due);
            due.forEach(d -> quietly(d.action()));
        });
        // Removing tickets of deleted levels is harmless; drop the list at stop.
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> delayed.clear());
    }

    private static void quietly(Runnable action) {
        try { action.run(); } catch (RuntimeException ignored) { /* level already closed */ }
    }

    /** True from begin() until the ready or failure callback has run. */
    @Override public boolean busy() { return job != null; }

    /**
     * Start to prepare worlds for this seed. {@code ready} runs on the server thread and must start
     * the game (which takes the worlds) or return false; then the worlds are deleted.
     */
    @Override public void begin(MinecraftServer server, long seed, BooleanSupplier ready, Consumer<RuntimeException> failed) {
        if (job != null) throw new IllegalStateException("Worlds are already being prepared");
        discard();
        // Retire handles of a context that a shutdown cleared without phase callbacks.
        WorldCleanup.clean(server);
        job = new Job(server, seed, ready, failed);
        job.open();
    }

    /** The prepared worlds for this seed, once; null if none. */
    public static Map<TeamId, TeamWorlds> take(MinecraftServer server, long seed) {
        var result = prepared;
        if (result == null || result.server() != server || result.seed() != seed) return null;
        prepared = null;
        return result.worlds();
    }

    /** Stop a running preparation and delete its worlds. */
    @Override public void cancel(MinecraftServer server) {
        if (job != null && job.server == server) {
            var stopped = job;
            stopped.cancelled = true;
            job = null;
            stopped.removeTickets();
            WorldCleanup.delete(server, stopped.owned);
        }
        discard();
    }

    /** Delete prepared worlds that no game took. Never touches other game levels. */
    private static void discard() {
        if (prepared == null) return;
        var unused = prepared;
        prepared = null;
        WorldCleanup.delete(unused.server(), unused.worlds().values().stream().flatMap(TeamWorlds::handles).toList());
    }

    /** The ticket type of the start preparation (for tests). */
    public static TicketType ticketType() { return ticket; }

    private static final class Job {
        final MinecraftServer server;
        final long seed;
        final BooleanSupplier ready;
        final Consumer<RuntimeException> failed;
        final long started = System.nanoTime();
        final Map<TeamId, RuntimeLevelHandle[]> handles = new EnumMap<>(TeamId.class);
        /** Every level this job opened, also if a later open fails. */
        final List<RuntimeLevelHandle> owned = new ArrayList<>();
        final List<Runnable> tickets = new ArrayList<>();
        final List<Runnable> spawnTickets = new ArrayList<>();
        boolean cancelled;
        ChunkPos origin;
        long busyNanos;

        Job(MinecraftServer server, long seed, BooleanSupplier ready, Consumer<RuntimeException> failed) {
            this.server = server; this.seed = seed; this.ready = ready; this.failed = failed;
        }

        ServerLevel overworld(TeamId team) { return handles.get(team)[0].asLevel(); }

        void open() {
            step(() -> {
                for (TeamId team : TeamId.values()) {
                    var levels = new RuntimeLevelHandle[3];
                    var kinds = List.of(WorldKind.PREP_OVERWORLD, WorldKind.PREP_NETHER, WorldKind.PREP_END);
                    for (int i = 0; i < 3; i++) owned.add(levels[i] = LevelFactory.open(server, seed, team, kinds.get(i)));
                    handles.put(team, levels);
                }
                // Same seed and generator: both overworlds have the same origin.
                origin = TeamSpawnFinder.origin(overworld(TeamId.RED));
                searchRing(0);
            });
        }

        /** Load ring {@code radius} in both overworlds, then search it in RED. */
        void searchRing(int radius) {
            var loads = new ArrayList<CompletableFuture<?>>();
            for (TeamId team : TeamId.values()) loads.add(load(overworld(team), origin, radius));
            after(loads, () -> {
                BlockPos spawn = TeamSpawnFinder.searchRing(overworld(TeamId.RED), origin, radius);
                if (spawn == null && radius < TeamSpawnFinder.MAX_RADIUS) { searchRing(radius + 1); return; }
                if (spawn == null) spawn = TeamSpawnFinder.fallback(overworld(TeamId.RED), origin);
                var column = spawn;
                // A player at the spawn touches neighbor chunks at once; a missing one blocks its tick.
                var spawnChunk = new ChunkPos(column.getX() >> 4, column.getZ() >> 4);
                var area = new ArrayList<CompletableFuture<?>>();
                for (TeamId team : TeamId.values()) area.add(loadSpawnArea(overworld(team), spawnChunk));
                after(area, () -> finish(column));
            });
        }

        /** Loaded before the teleport, so the first player ticks do not wait for neighbor chunks. */
        CompletableFuture<?> loadSpawnArea(ServerLevel level, ChunkPos center) {
            var cache = level.getChunkSource();
            spawnTickets.add(() -> cache.removeTicketWithRadius(ticket, center, SPAWN_RADIUS));
            return cache.addTicketAndLoadWithRadius(ticket, center, SPAWN_RADIUS);
        }

        void finish(BlockPos redSpawn) {
            Map<TeamId, TeamWorlds> worlds = new EnumMap<>(TeamId.class);
            for (TeamId team : TeamId.values()) {
                var level = overworld(team);
                var spawn = team == TeamId.RED ? redSpawn : TeamSpawnFinder.atColumn(level, redSpawn);
                var h = handles.get(team);
                worlds.put(team, new TeamWorlds(h[0], h[1], h[2], null, spawn, null));
            }
            MobArena.LOGGER.info("Prepared game worlds in {} ms ({} ms on the server thread)",
                    (System.nanoTime() - started) / 1_000_000, busyNanos / 1_000_000);
            job = null;
            prepared = new Prepared(server, seed, worlds);
            boolean started = false;
            try { started = ready.getAsBoolean(); }
            catch (RuntimeException e) { MobArena.LOGGER.error("Cannot start the game with the prepared worlds", e); }
            finally {
                if (started) {
                    // Player tickets are added gradually after a teleport. Removing ours at once
                    // unloads chunks that the players then wait for on the server thread.
                    var later = new ArrayList<>(spawnTickets);
                    later.addAll(tickets);
                    spawnTickets.clear();
                    tickets.clear();
                    delayed.add(new Delayed(server.getTickCount() + SPAWN_TICKET_TICKS, () -> later.forEach(WorldPreparer::quietly)));
                } else { removeTickets(); discard(); } // Delete the worlds if the start was refused or PREP did not take them.
            }
        }

        CompletableFuture<?> load(ServerLevel level, ChunkPos center, int radius) {
            var cache = level.getChunkSource();
            tickets.add(() -> cache.removeTicketWithRadius(ticket, center, radius));
            return cache.addTicketAndLoadWithRadius(ticket, center, radius);
        }

        void after(List<CompletableFuture<?>> loads, Runnable next) {
            CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new))
                    .whenCompleteAsync((ignored, error) -> {
                        if (error != null) fail(new IllegalStateException("Cannot load prep chunks", error));
                        else step(next);
                    }, server);
        }

        /** Run one step on the server thread, unless the job was cancelled or replaced. */
        void step(Runnable action) {
            if (cancelled || job != this) return;
            long t = System.nanoTime();
            try { action.run(); }
            catch (RuntimeException e) { fail(e); }
            finally { busyNanos += System.nanoTime() - t; }
        }

        void fail(RuntimeException e) {
            if (cancelled || job != this) return;
            job = null;
            removeTickets();
            WorldCleanup.delete(server, owned);
            failed.accept(e);
        }

        void removeTickets() {
            tickets.forEach(WorldPreparer::quietly);
            tickets.clear();
            spawnTickets.forEach(WorldPreparer::quietly);
            spawnTickets.clear();
        }
    }
}
