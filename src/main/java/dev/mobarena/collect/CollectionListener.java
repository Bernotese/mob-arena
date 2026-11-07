package dev.mobarena.collect;
import dev.mobarena.core.*;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.network.chat.Component;
import java.util.*;

public final class CollectionListener implements PhaseListener {
    public static void init() {
        var listener = new CollectionListener();
        GameManager.get().register(listener);
        ServerLivingEntityEvents.AFTER_DAMAGE.register((victim,source,base,taken,blocked) -> listener.damage(victim,source,blocked));
        ServerLivingEntityEvents.AFTER_DEATH.register(listener::death);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            var game=GameManager.get();
            if(game.phase()==Phase.PREP && server.getTickCount()%200==0)
                game.context().get(CollectKeys.HITS).prune(server.getTickCount());
        });
    }
    @Override public void onEnter(Phase phase, GameContext ctx) {
        if(phase==Phase.PREP) {
            ctx.put(CollectKeys.POOLS,new MobPools());
            ctx.put(CollectKeys.HITS,new LastHitTracker<>(200));
        }
    }
    @Override public void onExit(Phase phase, GameContext ctx) {
        if(phase==Phase.PREP) ctx.get(CollectKeys.HITS).clear();
    }
    private static boolean eligible(GameContext ctx, UUID id) {
        var p=ctx.server().getPlayerList().getPlayer(id);
        return p!=null && !p.isSpectator() && ctx.team(id)!=null;
    }
    private static Map<UUID,KillAttribution.Actor> actors(Entity causing) {
        Map<UUID,KillAttribution.Actor> result=new HashMap<>();
        describe(causing,result,0);
        return result;
    }
    private static void describe(Entity entity, Map<UUID,KillAttribution.Actor> result, int depth) {
        if(entity==null || depth>5 || result.containsKey(entity.getUUID())) return;
        Entity root=entity instanceof OwnableEntity own ? own.getRootOwner() : null;
        Entity owner=entity instanceof TraceableEntity trace ? trace.getOwner() : null;
        result.put(entity.getUUID(),new KillAttribution.Actor(entity instanceof ServerPlayer,
                root==null?null:root.getUUID(),owner==null?null:owner.getUUID()));
        describe(root,result,depth+1); describe(owner,result,depth+1);
    }
    public void damage(LivingEntity victim, DamageSource source, boolean blocked) {
        var game=GameManager.get();
        if(game.phase()!=Phase.PREP || !victim.isAlive()) return;
        var ctx=game.context();var causing=source.getEntity();var actors=actors(causing);
        KillAttribution.recordHit(victim.getUUID(),causing==null?null:causing.getUUID(),blocked,
                ctx.server().getTickCount(),actors::get,id->eligible(ctx,id),ctx.get(CollectKeys.HITS));
    }
    public void death(LivingEntity victim, DamageSource source) {
        var game=GameManager.get();if(game.phase()!=Phase.PREP) return;
        var ctx=game.context();var causing=source.getEntity();var actors=actors(causing);
        var killer=KillAttribution.resolveKiller(victim.getUUID(),causing==null?null:causing.getUUID(),
                ctx.server().getTickCount(),actors::get,id->eligible(ctx,id),ctx.get(CollectKeys.HITS));
        if(!MobEligibility.counts(victim,BlacklistIds.normalize(ctx.config().mobBlacklist())) || killer.isEmpty()) return;
        UUID id=killer.get();var pool=ctx.get(CollectKeys.POOLS).get(ctx.team(id));
        String type=EntityType.getKey(victim.getType()).toString();pool.add(type);
        ctx.server().getPlayerList().getPlayer(id).sendOverlayMessage(Component.literal("+1 ")
                .append(victim.getType().getDescription()).append(" (total "+pool.count(type)+")"));
    }
}
