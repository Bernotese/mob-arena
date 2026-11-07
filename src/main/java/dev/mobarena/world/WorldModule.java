package dev.mobarena.world;

import dev.mobarena.core.GameManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

public final class WorldModule {
    private WorldModule() {}
    public static void init() {
        GameManager.get().register(new PrepWorldManager());
        GameManager.get().register(WorldCleanup.INSTANCE);
        ServerLifecycleEvents.SERVER_STOPPING.register(WorldCleanup::clean);
    }
}
