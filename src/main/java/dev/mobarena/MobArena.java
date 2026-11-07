package dev.mobarena;

import net.fabricmc.api.ModInitializer;
import dev.mobarena.core.GameManager;
import dev.mobarena.core.LobbyManager;
import dev.mobarena.core.Phase;
import dev.mobarena.config.ConfigStore;
import dev.mobarena.command.CommandRegistry;
import dev.mobarena.command.CoreCommands;
import dev.mobarena.team.TeamManager;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import java.io.IOException;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MobArena implements ModInitializer {
	public static final String MOD_ID = "mobarena";
	public static final Logger LOGGER = LoggerFactory.getLogger("mobarena");

	@Override
	public void onInitialize() {
        ConfigStore config;
        try { config = new ConfigStore(FabricLoader.getInstance().getConfigDir().resolve("mob-arena.json")); }
        catch (IOException | RuntimeException e) { throw new IllegalStateException("Cannot load Mob Arena config", e); }
        LobbyManager lobby = new LobbyManager(FabricLoader.getInstance().getConfigDir().resolve("mob-arena-lobby.json"));
        GameManager.get().setConfigSource(config::snapshot);
        // Register the lobby first. Module listeners run after inventory cleanup.
        GameManager.get().register(lobby);
        // Modules: one init line each (PLAN 2.2). They run after the lobby listener.
        dev.mobarena.randomizer.RandomizerModule.init();
        dev.mobarena.world.WorldModule.init(lobby);
        dev.mobarena.arena.ArenaModule.init(lobby);
        dev.mobarena.backpack.BackpackModule.init();
        dev.mobarena.wave.WaveModule.init();
        CoreCommands.register(config, lobby);
        dev.mobarena.hud.HudModule.init(config);
        dev.mobarena.debug.DebugModule.init();
        CommandRegistry.initialize();
        ServerLifecycleEvents.SERVER_STARTED.register(server -> { TeamManager.get().initialize(server); lobby.prepare(server); });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            TeamManager.get().sync(handler.player);
            lobby.join(handler.player);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> lobby.leave(handler.player));
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
            !(entity instanceof net.minecraft.server.level.ServerPlayer player) || !lobby.protectedPlayer(player));
        ServerTickEvents.END_SERVER_TICK.register(lobby::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> TeamManager.get().clear());
        // END_SERVER_TICK drives the state machine after normal world ticks.
        ServerTickEvents.END_SERVER_TICK.register(server -> GameManager.get().tick());
        ServerLifecycleEvents.SERVER_STOPPING.register(lobby::shutdown);
		LOGGER.info("Mob Arena loaded");
	}
}
