package dev.mobarena.randomizer;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;

public final class RandomizerModule {
    private static final RandomizerLootHook HOOK = new RandomizerLootHook();

    private RandomizerModule() { }

    public static RandomizerLootHook hook() {
        return HOOK;
    }

    public static void init() {
        LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
            // Equipment is mapped when dropped, not when generated for a living mob.
            if (table.value().getParamSet() == LootContextParamSets.EQUIPMENT
                    || table.value().getParamSet() == LootContextParamSets.VILLAGER_TRADE) return;
            drops.replaceAll(stack -> HOOK.mapStack(context.getLevel(), stack));
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> HOOK.clear());
    }
}
