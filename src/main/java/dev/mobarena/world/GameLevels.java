package dev.mobarena.world;

import dev.mobarena.team.TeamId;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import java.util.*;

/** Server-thread registry. Entries retain the level instance to reject stale servers. */
public final class GameLevels {
    public record Entry(TeamId team, WorldKind kind, ServerLevel level) {}
    private static final Map<ResourceKey<Level>, Entry> LEVELS = new HashMap<>();
    private GameLevels() {}

    public static void register(TeamId team, WorldKind kind, ServerLevel level) {
        if (level(team, kind) != null || LEVELS.containsKey(level.dimension())) {
            throw new IllegalStateException("Duplicate game level");
        }
        LEVELS.put(level.dimension(), new Entry(team, kind, level));
    }
    private static Entry entry(Level level) {
        Entry entry = LEVELS.get(level.dimension());
        return entry != null && entry.level() == level ? entry : null;
    }
    public static Optional<TeamId> teamOf(Level level) {
        Entry entry = entry(level);
        return entry == null ? Optional.empty() : Optional.of(entry.team());
    }
    public static Optional<WorldKind> kindOf(Level level) {
        Entry entry = entry(level);
        return entry == null ? Optional.empty() : Optional.of(entry.kind());
    }
    public static ServerLevel level(TeamId team, WorldKind kind) {
        return LEVELS.values().stream().filter(e -> e.team() == team && e.kind() == kind)
                .map(Entry::level).findFirst().orElse(null);
    }
    public static boolean isGameLevel(Level level) { return entry(level) != null; }
    public static boolean isPrepLevel(Level level) {
        return kindOf(level).filter(kind -> kind != WorldKind.ARENA).isPresent();
    }
    public static boolean isArena(Level level) { return kindOf(level).orElse(null) == WorldKind.ARENA; }
    public static void remove(ServerLevel level) {
        if (entry(level) != null) LEVELS.remove(level.dimension());
    }
    public static void clear() { LEVELS.clear(); }
}
