package dev.mobarena.core;
import dev.mobarena.config.MobArenaConfig;
import dev.mobarena.team.TeamId;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import java.util.*;
public final class GameContext {
    private final MinecraftServer server;
    private final long seed;
    private final Map<UUID, TeamId> teams;
    private final Map<TeamId, ServerLevel> prepLevels = new EnumMap<>(TeamId.class);
    private final Map<TeamId, ServerLevel> arenaLevels = new EnumMap<>(TeamId.class);
    private final Map<Key<?>, Object> attachments = new IdentityHashMap<>();
    private final GameClock timer = new GameClock();
    private MobArenaConfig config;
    public GameContext(MinecraftServer server, long seed, Map<UUID, TeamId> teams, MobArenaConfig config) {
        this.server = server; this.seed = seed; this.teams = Map.copyOf(teams); this.config = config.copy();
    }
    public MinecraftServer server() { return server; }
    public long seed() { return seed; }
    public Map<UUID, TeamId> teams() { return teams; }
    public TeamId team(UUID player) { return teams.get(player); }
    public ServerLevel prepLevel(TeamId team) { return prepLevels.get(team); }
    public void setPrepLevel(TeamId team, ServerLevel level) { prepLevels.put(team, level); }
    public ServerLevel arenaLevel(TeamId team) { return arenaLevels.get(team); }
    public void setArenaLevel(TeamId team, ServerLevel level) { arenaLevels.put(team, level); }
    public GameClock timer() { return timer; }
    public MobArenaConfig config() { return config.copy(); }
    void setConfig(MobArenaConfig config) { this.config = config.copy(); }
    @SuppressWarnings("unchecked") public <T> T get(Key<T> key) { return (T) attachments.get(key); }
    public <T> void put(Key<T> key, T value) { if (value == null) attachments.remove(key); else attachments.put(key, value); }
    void clear() { attachments.clear(); prepLevels.clear(); arenaLevels.clear(); }
}
