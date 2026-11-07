package dev.mobarena.core;
import dev.mobarena.config.MobArenaConfig;
import dev.mobarena.team.TeamId;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import java.util.*;
import java.util.function.Supplier;
import dev.mobarena.MobArena;
/** Server thread only. Owns state for one game. */
public final class GameContext {
    private final MinecraftServer server;
    private final long seed;
    private final Map<UUID, TeamId> teams;
    private final Map<TeamId, ServerLevel> prepLevels = new EnumMap<>(TeamId.class);
    private final Map<TeamId, ServerLevel> arenaLevels = new EnumMap<>(TeamId.class);
    private final Map<Key<?>, Object> attachments = new IdentityHashMap<>();
    private final GameClock timer = new GameClock();
    private MobArenaConfig.Snapshot config;
    public GameContext(MinecraftServer server, long seed, Map<UUID, TeamId> teams, MobArenaConfig config) {
        this.server = server; this.seed = seed; this.teams = Map.copyOf(teams); this.config = config.snapshot();
    }
    public MinecraftServer server() { return server; }
    public long seed() { return seed; }
    public Map<UUID, TeamId> teams() { return teams; }
    public TeamId team(UUID player) { return teams.get(player); }
    /** Team overworld. Nether and End use T5 TeamWorlds.KEY. Resolve level ownership with T5 GameLevels. */
    public ServerLevel prepLevel(TeamId team) { return prepLevels.get(team); }
    public void setPrepLevel(TeamId team, ServerLevel level) { prepLevels.put(team, level); }
    /** Team arena. Resolve level ownership with T5 GameLevels. */
    public ServerLevel arenaLevel(TeamId team) { return arenaLevels.get(team); }
    public void setArenaLevel(TeamId team, ServerLevel level) { arenaLevels.put(team, level); }
    public GameClock timer() { return timer; }
    public MobArenaConfig.Snapshot config() { return config; }
    void setConfig(MobArenaConfig.Snapshot config) { this.config = config; }
    @SuppressWarnings("unchecked") public <T> T get(Key<T> key) { return (T) attachments.get(key); }
    public <T> void put(Key<T> key, T value) { if (value == null) attachments.remove(key); else attachments.put(key, value); }
    public <T> T getOrCreate(Key<T> key, Supplier<T> factory) {
        T value = get(key);
        if (value == null) { value = Objects.requireNonNull(factory.get()); put(key, value); }
        return value;
    }
    void clear() {
        Set<Object> closed = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Object value : List.copyOf(attachments.values())) if (value instanceof AutoCloseable closeable && closed.add(value)) {
            try { closeable.close(); } catch (Exception e) { MobArena.LOGGER.error("Cannot close game attachment", e); }
        }
        attachments.clear(); prepLevels.clear(); arenaLevels.clear();
    }
}
