package dev.mobarena.wave;
import dev.mobarena.collect.*;
import dev.mobarena.core.*;
import dev.mobarena.team.TeamId;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import java.util.*;

public final class WavePhaseListener implements PhaseListener {
    private static final class Hubs extends EnumMap<TeamId,WaveConfigHub> implements AutoCloseable {
        Hubs() { super(TeamId.class); }
        @Override public void close() { values().forEach(WaveConfigHub::close);clear(); }
    }
    private final WaveMenuItems items;
    public WavePhaseListener(WaveMenuItems items) { this.items=Objects.requireNonNull(items); }
    @Override public void onEnter(Phase phase, GameContext ctx) {
        if(phase!=Phase.WAVE_CONFIG) return;
        var setups=new EnumMap<TeamId,WaveSetup>(TeamId.class);
        var hubs=new Hubs();
        var pools=ctx.get(CollectKeys.POOLS);
        for(var team:TeamId.values()) {
            var setup=new WaveSetup(pools==null?Map.of():pools.get(team).snapshot());
            setups.put(team,setup);hubs.put(team,new WaveConfigHub(ctx,team,setup,this));
        }
        ctx.put(WaveKeys.SETUPS,setups);ctx.put(WaveKeys.HUBS,hubs);ctx.put(WaveKeys.READY,new HashSet<>());
        publishReady(ctx,null);
        for(var player:players(ctx,null)) items.give(player,ctx,()->open(player));
    }
    @Override public void onExit(Phase phase, GameContext ctx) {
        if(phase!=Phase.WAVE_CONFIG) return;
        ctx.get(WaveKeys.HUBS).values().forEach(WaveConfigHub::close);
        var plans=new EnumMap<TeamId,WavePlan>(TeamId.class);
        ctx.get(WaveKeys.SETUPS).forEach((team,setup)->{setup.finish();plans.put(team,new WavePlan(team,setup.snapshot()));});
        ctx.put(WaveKeys.PLANS,plans);
        for(var player:ctx.server().getPlayerList().getPlayers()) items.remove(player);
    }
    public WaveConfigGui open(ServerPlayer player) {
        var game=GameManager.get();var ctx=game.context();
        if(game.phase()!=Phase.WAVE_CONFIG || ctx==null || ctx.team(player.getUUID())==null) {
            player.sendSystemMessage(Component.literal("Open this menu during wave setup with a team.").withStyle(net.minecraft.ChatFormatting.RED));return null;
        }
        return ctx.get(WaveKeys.HUBS).get(ctx.team(player.getUUID())).open(player);
    }
    static List<ServerPlayer> players(GameContext ctx, UUID leaving) {
        return ctx.server().getPlayerList().getPlayers().stream()
                .filter(p->ctx.team(p.getUUID())!=null && !p.getUUID().equals(leaving)).toList();
    }
    public boolean toggle(ServerPlayer player) {
        var game=GameManager.get();var ctx=game.context();
        if(game.phase()!=Phase.WAVE_CONFIG || ctx==null || ctx.team(player.getUUID())==null) {
            player.sendSystemMessage(Component.literal("Use ready during wave setup with a team.").withStyle(net.minecraft.ChatFormatting.RED));return false;
        }
        var ready=ctx.get(WaveKeys.READY);UUID id=player.getUUID();boolean added=ready.add(id);
        if(!added) ready.remove(id);
        publishReady(ctx,null);
        var message=Component.literal(player.getName().getString()+(added?" is ready (":" is not ready (")+readyCount(ctx)+"/"+players(ctx,null).size()+")");
        for(var p:players(ctx,null)) p.sendSystemMessage(message);
        checkReady(ctx,null);return true;
    }
    static int readyCount(GameContext ctx) {
        return (int)players(ctx,null).stream().filter(p->ctx.get(WaveKeys.READY).contains(p.getUUID())).count();
    }
    private void publishReady(GameContext ctx, UUID leaving) {
        var online=players(ctx,leaving);
        int count=(int)online.stream().filter(p->ctx.get(WaveKeys.READY).contains(p.getUUID())).count();
        dev.mobarena.hud.HudOverrides.of(ctx).setReady(count,online.size());
        ctx.get(WaveKeys.HUBS).values().forEach(WaveConfigHub::dirty);
    }
    private void checkReady(GameContext ctx, UUID leaving) {
        var online=players(ctx,leaving);
        if(!online.isEmpty() && online.stream().allMatch(p->ctx.get(WaveKeys.READY).contains(p.getUUID())))
            GameManager.get().endPhaseNow();
    }
    public void leave(ServerPlayer player) {
        var game=GameManager.get();var ctx=game.context();
        items.remove(player);
        if(game.phase()!=Phase.WAVE_CONFIG || ctx==null) return;
        ctx.get(WaveKeys.HUBS).values().forEach(h->h.leave(player.getUUID()));
        ctx.get(WaveKeys.READY).remove(player.getUUID());
        publishReady(ctx,player.getUUID());checkReady(ctx,player.getUUID());
    }
}
