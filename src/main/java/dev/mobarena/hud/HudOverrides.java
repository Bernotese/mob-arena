package dev.mobarena.hud;

import java.util.EnumMap;
import dev.mobarena.core.GameContext;
import dev.mobarena.core.Key;
import dev.mobarena.team.TeamId;
import net.minecraft.network.chat.Component;
import net.minecraft.world.BossEvent.BossBarColor;

/** Server-thread integration for arena text, ready counts, and the final result. */
public final class HudOverrides {
    public static final Key<HudOverrides> KEY = Key.of("hud.overrides");
    private final EnumMap<TeamId, HudLine> lines = new EnumMap<>(TeamId.class);
    private int ready;
    private int total;
    private HudLine result = new HudLine(Component.literal("Draw"), 1, BossBarColor.WHITE);
    public static HudOverrides of(GameContext ctx) {
        return ctx.getOrCreate(KEY, () -> {
            var overrides = new HudOverrides();
            overrides.setReady(0, ctx.teams().size());
            return overrides;
        });
    }
    public void set(TeamId team, HudLine line) { lines.put(team, line); }
    public void clear(TeamId team) { lines.remove(team); }
    public void clearBattleLines() { lines.clear(); }
    public HudLine get(TeamId team) { return lines.get(team); }
    public void setReady(int ready, int total) {
        if (ready < 0 || total < ready) throw new IllegalArgumentException("Invalid ready count");
        this.ready = ready;
        this.total = total;
    }
    public String readyText() { return ready + "/" + total + " ready"; }
    /** A null winner denotes a draw. */
    public void setResult(TeamId winner) {
        result = new HudLine(Component.literal(winner == null ? "Draw" : "Team " + winner + " wins"), 1,
                winner == null ? BossBarColor.WHITE : winner == TeamId.RED ? BossBarColor.RED : BossBarColor.BLUE);
    }
    public HudLine result() { return result; }
}
