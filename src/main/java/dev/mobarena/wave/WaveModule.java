package dev.mobarena.wave;
import dev.mobarena.core.*;
import dev.mobarena.command.CommandRegistry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import static net.minecraft.commands.Commands.literal;
public final class WaveModule {
    private WaveModule() {}
    public static void init() { init(WaveMenuItem.Guard.NONE); }
    public static void init(WaveMenuItem.Guard guard) {
        var items=new WaveMenuItem(guard);items.register();init(items);
    }
    public static void init(WaveMenuItems items) {
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
