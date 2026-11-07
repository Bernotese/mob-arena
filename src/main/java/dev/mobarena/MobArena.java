package dev.mobarena;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MobArena implements ModInitializer {
	public static final String MOD_ID = "mobarena";
	public static final Logger LOGGER = LoggerFactory.getLogger("mobarena");

	@Override
	public void onInitialize() {
		dev.mobarena.randomizer.RandomizerModule.init();
		LOGGER.info("Mob Arena loaded");
	}
}
