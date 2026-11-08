package dev.mobarena.arena;

import dev.mobarena.core.GameContext;
import dev.mobarena.core.Key;
import dev.mobarena.team.TeamId;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import java.util.function.Supplier;

/**
 * All battles of one phase (ARENA or SUDDEN_DEATH). Stored in GameContext; closing it stops the battles.
 * T8b starts sudden death with {@link ArenaPhaseListener#startSuddenDeath}.
 */
public final class ArenaBattles implements AutoCloseable {
    public static final Key<ArenaBattles> KEY = Key.of("arena.battles");
    /** Arena level to running battle, for the Fabric event handlers. */
    private static final Map<ServerLevel, ArenaBattle> BY_LEVEL = new IdentityHashMap<>();
    /** Game is cancelled if an arena is not ready after this many ticks (T7 Q7). */
    public static final int READY_TIMEOUT_TICKS = 60 * 20;

    private final GameContext ctx;
    private final EnumMap<TeamId, ArenaBattle> battles = new EnumMap<>(TeamId.class);
    private final RespawnManager respawns;
    private int maxAliveMobs;
    private long waitingTicks;

    ArenaBattles(GameContext ctx) {
        this.ctx = Objects.requireNonNull(ctx);
        this.respawns = new RespawnManager(this, ctx.config().arenaRespawnSeconds() * 20);
        this.maxAliveMobs = ctx.config().maxAliveMobs();
    }

    public static ArenaBattles get(GameContext ctx) { return ctx == null ? null : ctx.get(KEY); }
    public static ArenaBattle at(net.minecraft.world.level.Level level) { return BY_LEVEL.get(level); }

    GameContext ctx() { return ctx; }
    RespawnManager respawns() { return respawns; }
    int maxAliveMobs() { return maxAliveMobs; }
    /** Test hook: shorter respawn timer. */
    void setRespawnTicks(int ticks) { respawns.setRespawnTicks(ticks); }
    void setMaxAliveMobs(int max) { maxAliveMobs = max; }
    public ArenaBattle battle(TeamId team) { return battles.get(team); }
    public Collection<ArenaBattle> all() { return List.copyOf(battles.values()); }

    /** Start (or restart) the battle of one team. */
    public ArenaBattle start(TeamId team, Supplier<Optional<Arena>> arena, BattleScript script,
                             BattleListener listener, boolean suddenDeath) {
        var old = battles.remove(team);
        if (old != null) old.stop();
        var battle = new ArenaBattle(this, team, arena, script, listener, suddenDeath);
        battles.put(team, battle);
        return battle;
    }

    void register(ServerLevel level, ArenaBattle battle) { BY_LEVEL.put(level, battle); }
    void unregister(ArenaBattle battle) { BY_LEVEL.values().removeIf(b -> b == battle); }

    /** Returns false when the arena-ready timeout ends the game. */
    boolean tick() {
        long now = ctx.server().getTickCount();
        for (var team : TeamId.values()) for (var player : onlinePlayers(team)) {
            if (player.isDeadOrDying()) dev.mobarena.core.DeadPlayers.revive(player);
        }
        boolean waiting = false;
        for (var battle : all()) { battle.tick(now); waiting |= battle.waiting(); }
        respawns.tick(now);
        waitingTicks = waiting ? waitingTicks + 1 : 0;
        return waitingTicks < READY_TIMEOUT_TICKS;
    }

    List<ServerPlayer> onlinePlayers(TeamId team) {
        return ctx.server().getPlayerList().getPlayers().stream().filter(p -> ctx.team(p.getUUID()) == team).toList();
    }
    boolean anyOnline(TeamId team) { return !onlinePlayers(team).isEmpty(); }

    /** Alive = online, in the own arena, not in the respawn timer, not a spectator. */
    boolean alive(ServerPlayer player, ArenaBattle battle) {
        return battle.arena().isPresent() && player.level() == battle.arena().get().level() && player.isAlive()
                && !player.isSpectator() && !respawns.isDown(player.getUUID());
    }
    boolean allDown(ArenaBattle battle) {
        return onlinePlayers(battle.team).stream().noneMatch(p -> alive(p, battle));
    }

    void onEliminated(ArenaBattle battle) {
        respawns.cancelTeam(battle.team);
        // SPEC 7.6: eliminated players watch the other arena as spectators.
        var other = battles.get(battle.team.opponent());
        var target = other != null && other.arena().isPresent() ? other.arena().get() : battle.arena().orElse(null);
        for (ServerPlayer player : onlinePlayers(battle.team)) spectate(player, target);
    }

    void spectate(ServerPlayer player, Arena target) {
        player.setGameMode(GameType.SPECTATOR);
        if (target == null) return;
        Vec3 pos = Vec3.atBottomCenterOf(target.layout().teamSpawn()).add(0, 4, 0);
        player.teleportTo(target.level(), pos.x, pos.y, pos.z, Set.of(), player.getYRot(), 30, true);
        dev.mobarena.core.Teleports.clearSupport(player);
    }

    /** LobbyManager rejoin hook for ARENA and SUDDEN_DEATH (SPEC 2: back to the team, with respawn timer). */
    void rejoin(ServerPlayer player) {
        var team = ctx.team(player.getUUID());
        var battle = team == null ? null : battles.get(team);
        if (battle == null || battle.arena().isEmpty()) return;
        switch (battle.state()) {
            case ELIMINATED -> onEliminatedRejoin(player, battle);
            case PREP, FINISHED -> {
                respawns.cancel(player.getUUID());
                battle.placeAtSpawn(player);
            }
            case WAITING_ARENA -> { }
            default -> respawns.down(player, battle, false);
        }
    }
    private void onEliminatedRejoin(ServerPlayer player, ArenaBattle battle) {
        var other = battles.get(battle.team.opponent());
        spectate(player, other != null && other.arena().isPresent() ? other.arena().get() : battle.arena().get());
    }

    /** Sudden death start: every online team player alive at the own spawn, no effects (T8 Q1). */
    void resetPlayers() {
        for (var battle : all()) for (ServerPlayer player : onlinePlayers(battle.team)) {
            player.removeAllEffects();
            // Arena not loaded yet: onReady places the player.
            if (battle.loadArena().isPresent()) battle.placeAtSpawn(player);
        }
    }

    /** Only players of the game teams (not lobby players). */
    void broadcastToTeams(Component message) {
        for (TeamId team : TeamId.values()) for (var player : onlinePlayers(team)) player.sendSystemMessage(message);
    }

    void broadcast(Component message) {
        for (var player : ctx.server().getPlayerList().getPlayers()) player.sendSystemMessage(message);
    }

    /** Phase end: bring players in the respawn timer back to the spawn, then close. */
    public void end() {
        for (UUID id : respawns.down()) {
            var player = ctx.server().getPlayerList().getPlayer(id);
            var battle = player == null ? null : battles.get(ctx.team(id));
            if (player != null && battle != null && battle.arena().isPresent()) battle.placeAtSpawn(player);
        }
        close();
    }

    /** Stop all battles and remove wave mobs. No teleports: GameContext.clear also calls this at server shutdown. */
    @Override public void close() {
        for (var battle : all()) battle.stop();
        respawns.clear();
        battles.clear();
    }
}
