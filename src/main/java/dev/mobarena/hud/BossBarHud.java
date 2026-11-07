package dev.mobarena.hud;

import java.util.*;
import dev.mobarena.core.*;
import dev.mobarena.team.TeamId;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent.BossBarColor;
import net.minecraft.world.BossEvent.BossBarOverlay;

public final class BossBarHud implements PhaseListener {
    private final EnumMap<TeamId, ServerBossEvent> teams = new EnumMap<>(TeamId.class);
    private final ServerBossEvent neutral = create();
    public BossBarHud() { for (TeamId team : TeamId.values()) teams.put(team, create()); }
    private static ServerBossEvent create() {
        var bar = new ServerBossEvent(UUID.randomUUID(), Component.empty(), BossBarColor.WHITE, BossBarOverlay.PROGRESS);
        bar.setVisible(false);
        return bar;
    }
    public ServerBossEvent bar(TeamId team) { return team == null ? neutral : teams.get(team); }
    public void remove(ServerPlayer player) {
        neutral.removePlayer(player);
        teams.values().forEach(bar -> bar.removePlayer(player));
    }
    public void add(ServerPlayer player, Phase phase, GameContext ctx) {
        remove(player);
        if (phase != Phase.LOBBY && ctx != null) bar(ctx.team(player.getUUID())).addPlayer(player);
    }
    public void close() {
        neutral.removeAllPlayers(); neutral.setVisible(false);
        teams.values().forEach(bar -> { bar.removeAllPlayers(); bar.setVisible(false); });
    }
    @Override public void onEnter(Phase phase, GameContext ctx) { update(phase, ctx); }
    @Override public void onExit(Phase phase, GameContext ctx) { HudOverrides.of(ctx).clearBattleLines(); }
    @Override public void onTick(Phase phase, GameContext ctx) {
        if (ctx.timer().elapsedTicks() % 20 == 0) update(phase, ctx);
    }
    public void update(Phase phase, GameContext ctx) {
        if (phase == Phase.LOBBY || ctx == null) { close(); return; }
        apply(neutral, phaseLine(phase, ctx, true));
        var overrides = HudOverrides.of(ctx);
        for (TeamId team : TeamId.values()) {
            HudLine line = (phase == Phase.ARENA || phase == Phase.SUDDEN_DEATH) ? overrides.get(team) : null;
            apply(teams.get(team), line == null ? phaseLine(phase, ctx, false) : line);
        }
        // Reconcile assignments and disconnects without exposing another team's override.
        Set<ServerPlayer> online = new HashSet<>(ctx.server().getPlayerList().getPlayers());
        for (ServerBossEvent bar : List.of(neutral, teams.get(TeamId.RED), teams.get(TeamId.BLUE))) {
            for (ServerPlayer player : List.copyOf(bar.getPlayers()))
                if (!online.contains(player) || bar != bar(ctx.team(player.getUUID()))) bar.removePlayer(player);
        }
        for (ServerPlayer player : online) bar(ctx.team(player.getUUID())).addPlayer(player);
    }
    private void apply(ServerBossEvent bar, HudLine line) {
        bar.setName(line.text()); bar.setProgress(line.progress()); bar.setColor(line.color()); bar.setVisible(true);
    }
    private HudLine phaseLine(Phase phase, GameContext ctx, boolean neutralViewer) {
        long remaining = ctx.timer().remainingTicks();
        long total = switch (phase) {
            case PREP -> ctx.config().prepTimeSeconds() * 20L;
            case WAVE_CONFIG -> ctx.config().waveConfigTimeSeconds() * 20L;
            case END -> ctx.config().endScreenSeconds() * 20L;
            default -> -1;
        };
        String name = switch (phase) {
            case PREP -> "Preparation";
            case WAVE_CONFIG -> "Wave setup";
            case ARENA -> "Arena";
            case SUDDEN_DEATH -> "Sudden death";
            case END -> "End";
            case LOBBY -> "Lobby";
        };
        if (phase == Phase.END && !neutralViewer) return HudOverrides.of(ctx).result();
        if (remaining >= 0) name += " – " + TimeFormat.mmss(remaining);
        if (phase == Phase.WAVE_CONFIG && !neutralViewer) name += " – " + HudOverrides.of(ctx).readyText();
        BossBarColor color = switch (phase) {
            case PREP -> BossBarColor.GREEN;
            case WAVE_CONFIG -> BossBarColor.YELLOW;
            case ARENA -> BossBarColor.RED;
            case SUDDEN_DEATH -> BossBarColor.PURPLE;
            default -> BossBarColor.WHITE;
        };
        return new HudLine(Component.literal(name), total > 0 ? (float) remaining / total : 1, color);
    }
}
