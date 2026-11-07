package dev.mobarena.wave;
import dev.mobarena.core.*;
import dev.mobarena.team.TeamId;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;
/** One shared model per team; view state belongs to each menu. */
public final class WaveConfigHub implements AutoCloseable {
    final GameContext ctx;
    final TeamId team;
    final WaveSetup setup;
    final WavePhaseListener owner;
    private final Set<WaveConfigGui> open=new HashSet<>();
    private boolean dirty;
    WaveConfigHub(GameContext ctx, TeamId team, WaveSetup setup, WavePhaseListener owner) {
        this.ctx=ctx;this.team=team;this.setup=setup;this.owner=owner;
    }
    public WaveConfigGui open(ServerPlayer player) {
        if(!active(player)) return null;
        var gui=new WaveConfigGui(player,this);
        if(!gui.open()) return null;
        open.add(gui);return gui;
    }
    boolean active(ServerPlayer player) {
        var game=GameManager.get();
        return game.context()==ctx && game.phase()==Phase.WAVE_CONFIG && ctx.team(player.getUUID())==team;
    }
    public void dirty() { dirty=true; }
    void removed(WaveConfigGui gui) { open.remove(gui); }
    public void tick(boolean second) {
        if(dirty || second) {
            for(var gui:List.copyOf(open)) if(gui.isOpen()) gui.render(); else open.remove(gui);
            dirty=false;
        }
    }
    void leave(UUID id) { for(var gui:List.copyOf(open)) if(gui.viewer().getUUID().equals(id)) gui.close(); }
    @Override public void close() { for(var gui:List.copyOf(open)) gui.close();open.clear(); }
}
