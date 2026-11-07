package dev.mobarena;

import net.fabricmc.api.ModInitializer;
import dev.mobarena.core.GameManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MobArena implements ModInitializer {
	public static final String MOD_ID = "mobarena";
	public static final Logger LOGGER = LoggerFactory.getLogger("mobarena");

	@Override
	public void onInitialize() {
        // Phase listeners run in the order registered here, before module listeners.
        ServerTickEvents.END_SERVER_TICK.register(server -> GameManager.get().tick());
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> GameManager.get().stop());
        dev.mobarena.world.WorldModule.init();
		LOGGER.info("Mob Arena loaded");
	}
}
