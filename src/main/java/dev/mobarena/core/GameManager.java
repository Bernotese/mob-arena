package dev.mobarena.core;
import dev.mobarena.config.MobArenaConfig;
import dev.mobarena.team.TeamId;
import net.minecraft.server.MinecraftServer;
import java.util.*;
import java.util.function.Supplier;
public final class GameManager {
    private static final GameManager INSTANCE = new GameManager();
    private final List<PhaseListener> listeners = new ArrayList<>();
    private Phase phase = Phase.LOBBY;
    private GameContext context;
    private Supplier<MobArenaConfig> config = MobArenaConfig::new;
    private boolean transitioning;
    public static GameManager get() { return INSTANCE; }
    public void register(PhaseListener listener) { listeners.add(Objects.requireNonNull(listener)); }
    public Phase phase() { return phase; }
    public GameContext context() { return context; }
    public void setConfigSource(Supplier<MobArenaConfig> source) { config = source; }
    public boolean start(MinecraftServer server, Map<UUID, TeamId> teams, Set<UUID> online) {
        if (phase != Phase.LOBBY) return false;
        for (TeamId team : TeamId.values()) if (online.stream().noneMatch(id -> teams.get(id) == team)) return false;
        context = new GameContext(server, new Random().nextLong(), teams, config.get());
        transition(Phase.PREP); return true;
    }
    public void tick() {
        if (context == null) return;
        Phase before = phase;
        context.timer().tick();
        for (PhaseListener listener : List.copyOf(listeners)) {
            listener.onTick(before, context);
            if (phase != before) return;
        }
        if (context != null && context.timer().expired()) endPhaseNow();
    }
    public void endPhaseNow() {
        if (transitioning) return;
        switch (phase) {
            case LOBBY -> { }
            case PREP -> transition(Phase.WAVE_CONFIG);
            case WAVE_CONFIG -> transition(Phase.ARENA);
            case ARENA, SUDDEN_DEATH -> transition(Phase.END);
            case END -> transition(Phase.LOBBY);
        }
    }
    public void stop() { if (context != null) transition(Phase.LOBBY); }
    public void enterSuddenDeath() { if (phase == Phase.ARENA) transition(Phase.SUDDEN_DEATH); }
    public void finish() { if (context != null && phase != Phase.END) transition(Phase.END); }
    private void transition(Phase next) {
        if (transitioning) throw new IllegalStateException("Nested phase transition");
        transitioning = true;
        try {
            for (PhaseListener listener : List.copyOf(listeners)) listener.onExit(phase, context);
            phase = next;
            context.setConfig(config.get());
            MobArenaConfig c = context.config();
            long seconds = switch (next) {
                case PREP -> c.prepTimeSeconds;
                case WAVE_CONFIG -> c.waveConfigTimeSeconds;
                case END -> c.endScreenSeconds;
                case SUDDEN_DEATH -> -1;
                default -> -1;
            };
            context.timer().reset(seconds < 0 ? -1 : seconds * 20);
            for (PhaseListener listener : List.copyOf(listeners)) listener.onEnter(next, context);
            if (next == Phase.LOBBY) { context.clear(); context = null; }
        } finally { transitioning = false; }
    }
}
