package dev.mobarena.world;

import dev.mobarena.MobArena;
import dev.mobarena.core.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameType;
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
    private WorldCleanup() {}
    public static final WorldCleanup INSTANCE = new WorldCleanup();
    static void track(RuntimeLevelHandle handle) { HANDLES.add(handle); }

    public static void clean(MinecraftServer server) {
        var handles = HANDLES.stream().filter(h -> h.asLevel().getServer() == server).toList();
        for (var handle : handles) {
            var level = handle.asLevel();
            for (var chunk : FORCED.getOrDefault(level, Set.of())) {
                level.setChunkForced(chunk.x(), chunk.z(), false);
            }
            FORCED.remove(level);
            handle.delete();
            GameLevels.remove(level);
            HANDLES.remove(handle);
        }
    }
    @Override public void onEnter(Phase phase, GameContext ctx) {
        if (phase == Phase.LOBBY && ctx.server() != null) clean(ctx.server());
    }
}
