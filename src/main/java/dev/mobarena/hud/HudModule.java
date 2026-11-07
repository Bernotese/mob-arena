package dev.mobarena.hud;

import dev.mobarena.command.CommandRegistry;
import dev.mobarena.config.ConfigStore;
import dev.mobarena.config.gui.ConfigGuiHub;
import dev.mobarena.core.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import static net.minecraft.commands.Commands.*;

public final class HudModule {
    private HudModule() {}
    public static void init(ConfigStore store) {
        var hud = new BossBarHud();
        var menus = new ConfigGuiHub(store);
        GameManager.get().register(hud);
        GameManager.get().register(new PhaseListener() {
            @Override public void onEnter(Phase phase, GameContext ctx) { menus.renderAll(); }
        });
        CommandRegistry.register(root -> root.then(literal("config")
                .requires(hasPermission(LEVEL_GAMEMASTERS)).executes(context ->
                        menus.open(context.getSource().getPlayerOrException()) == null ? 0 : 1)));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            var game = GameManager.get();
            if (game.context() != null) hud.update(game.phase(), game.context());
            hud.add(handler.player, game.phase(), game.context());
        });
        net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            var game = GameManager.get();
            hud.remove(oldPlayer);
            hud.add(newPlayer, game.phase(), game.context());
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> hud.remove(handler.player));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 == 0) menus.tick();
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { hud.close(); menus.closeAll(); });
    }
}
