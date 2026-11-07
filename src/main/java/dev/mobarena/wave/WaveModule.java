package dev.mobarena.wave;
import dev.mobarena.core.*;
import dev.mobarena.command.CommandRegistry;
import dev.mobarena.backpack.GuardedItems;
import dev.mobarena.backpack.TeamBackpack;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import static net.minecraft.commands.Commands.literal;
public final class WaveModule {
    private WaveModule() {}
    public static void init() {
        init(new WaveMenuItem.Guard() {
            @Override public boolean storeOverflow(ServerPlayer player,GameContext ctx,ItemStack stack) {
                var team=ctx.team(player.getUUID());
                if(team==null || GuardedItems.isGuarded(stack)) return false;
                var backpack=TeamBackpack.get(ctx.server()).inventory(team);
                // Check the full transfer before changing shared storage.
                var trial=new SimpleContainer(backpack.getContainerSize());
                for(int i=0;i<backpack.getContainerSize();i++) trial.setItem(i,backpack.getItem(i).copy());
                if(!trial.addItem(stack.copy()).isEmpty()) return false;
                return backpack.addItem(stack.copy()).isEmpty();
            }
        });
    }
    public static void init(WaveMenuItem.Guard guard) {
        var items=new WaveMenuItem(guard);items.register();init(items);
    }
    public static void init(WaveMenuItems items) {
        GuardedItems.register(WaveMenuItem.TAG,WaveMenuItem::createStack,player -> {
            var game=GameManager.get();
            return game.phase()==Phase.WAVE_CONFIG && game.context()!=null
                    && game.context().team(player.getUUID())!=null;
        });
        dev.mobarena.collect.CollectionListener.init();
        var listener=new WavePhaseListener(items);GameManager.get().register(listener);
        CommandRegistry.register(root->{
            root.then(literal("waves").executes(c->listener.open(c.getSource().getPlayerOrException())==null?0:1));
            root.then(literal("ready").executes(c->listener.toggle(c.getSource().getPlayerOrException())?1:0));
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for(var player:server.getPlayerList().getPlayers()) items.remove(player);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->listener.leave(handler.player));
        ServerTickEvents.END_SERVER_TICK.register(server->{
            var game=GameManager.get();
            if(game.phase()==Phase.WAVE_CONFIG)
                game.context().get(WaveKeys.HUBS).values().forEach(h->h.tick(server.getTickCount()%20==0));
        });
    }
}
