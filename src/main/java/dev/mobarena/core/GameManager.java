package dev.mobarena.core;
import dev.mobarena.config.MobArenaConfig;
import dev.mobarena.team.TeamId;
import net.minecraft.server.MinecraftServer;
import java.util.*;
import java.util.function.Supplier;
import java.util.function.Function;
import java.util.function.Consumer;
import dev.mobarena.MobArena;
/** Server thread only. Listeners run in registration order. Nested calls keep the last transition, but LOBBY has priority. */
public final class GameManager {
    private static final GameManager INSTANCE = new GameManager();
    private final List<PhaseListener> listeners = new ArrayList<>();
    private Phase phase = Phase.LOBBY;
    private GameContext context;
    private Supplier<MobArenaConfig> config = MobArenaConfig::new;
    private boolean transitioning;
    private Phase pending;
    private Function<GameContext, ResultHooks> resultHooks = ctx -> new ResultHooks() {
        public void teamFinished(TeamId team, long timeTicks) {}
        public void teamEliminated(TeamId team, TeamProgress progress) {}
    };
    private StartPreparer preparer = StartPreparer.NONE;
    public StartPreparer preparer() { return preparer; }
    public void setPreparer(StartPreparer preparer) { this.preparer = Objects.requireNonNull(preparer); }
    public void setResultHooksFactory(Function<GameContext, ResultHooks> factory) { resultHooks = Objects.requireNonNull(factory); }
    private void notifyListeners(String event, Consumer<PhaseListener> call) {
        for (PhaseListener listener : List.copyOf(listeners)) {
            try { call.accept(listener); }
            catch (Exception e) { MobArena.LOGGER.error("Phase listener failed during {}: {}", event, listener.getClass().getName(), e); }
        }
    }
    public static GameManager get() { return INSTANCE; }
    public void register(PhaseListener listener) { listeners.add(Objects.requireNonNull(listener)); }
    public Phase phase() { return phase; }
    public GameContext context() { return context; }
    public void setConfigSource(Supplier<MobArenaConfig> source) { config = source; }
    public boolean start(MinecraftServer server, Map<UUID, TeamId> teams, Set<UUID> online) {
        return start(server, teams, online, new Random().nextLong());
    }
    /** True in the lobby when each team has an online player. */
    public boolean canStart(Map<UUID, TeamId> teams, Set<UUID> online) {
        if (phase != Phase.LOBBY || context != null || transitioning) return false;
        for (TeamId team : TeamId.values()) if (online.stream().noneMatch(id -> teams.get(id) == team)) return false;
        return true;
    }
    /** Start with a known seed, for example for worlds that were prepared before. */
    public boolean start(MinecraftServer server, Map<UUID, TeamId> teams, Set<UUID> online, long seed) {
        if (!canStart(teams, online)) return false;
        context = new GameContext(server, seed, teams, config.get());
        context.put(ResultHooks.KEY, Objects.requireNonNull(resultHooks.apply(context)));
        transition(Phase.PREP); return true;
    }
    public void tick() {
        if (context == null) return;
        Phase before = phase;
        context.timer().tick();
        for (PhaseListener listener : List.copyOf(listeners)) {
            try { listener.onTick(before, context); }
            catch (Exception e) { MobArena.LOGGER.error("Phase tick listener failed: {}", listener.getClass().getName(), e); }
            if (phase != before) return;
        }
        if (context != null && context.timer().expired()) endPhaseNow();
    }
    public void endPhaseNow() {
        switch (phase) {
            case LOBBY -> { }
            case PREP -> transition(Phase.WAVE_CONFIG);
            case WAVE_CONFIG -> transition(Phase.ARENA);
            case ARENA, SUDDEN_DEATH -> transition(Phase.END);
            case END -> transition(Phase.LOBBY);
        }
    }
    /** Server shutdown: dispose state without phase callbacks or player teleports. */
    public void shutdown() {
        if (context != null) context.clear();
        context = null; phase = Phase.LOBBY; pending = null;
    }
    public void stop() { if (context != null) transition(Phase.LOBBY); }
    public void enterSuddenDeath() { if (phase == Phase.ARENA) transition(Phase.SUDDEN_DEATH); }
    public void finish() { if (context != null && phase != Phase.END) transition(Phase.END); }
    /** Nested calls keep the last request, but a queued LOBBY cannot be replaced. */
    private void transition(Phase next) {
        if (transitioning) { if (pending != Phase.LOBBY) pending = next; return; }
        if (context == null) return;
        MobArenaConfig.Snapshot nextConfig = config.get().snapshot();
        transitioning = true;
        try {
            notifyListeners("exit", listener -> listener.onExit(phase, context));
            phase = next;
            context.setConfig(nextConfig);
            MobArenaConfig.Snapshot c = context.config();
            long seconds = switch (next) {
                case PREP -> c.prepTimeSeconds();
                case WAVE_CONFIG -> c.waveConfigTimeSeconds();
                case END -> c.endScreenSeconds();
                case SUDDEN_DEATH -> -1;
                default -> -1;
            };
            context.timer().reset(seconds < 0 ? -1 : seconds * 20);
            notifyListeners("enter", listener -> listener.onEnter(next, context));
            if (next == Phase.LOBBY) { context.clear(); context = null; }
        } finally { transitioning = false; }
        Phase queued = pending; pending = null;
        if (queued != null && context != null && queued != phase) transition(queued);
    }
}
