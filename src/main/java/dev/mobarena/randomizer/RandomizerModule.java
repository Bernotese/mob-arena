package dev.mobarena.randomizer;

import dev.mobarena.randomizer.mixin.LootContextAccessor;
import net.minecraft.world.level.storage.loot.LootTable;
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
            // Map only after the outer table finishes, so nested tables do not map twice.
            if (((LootContextAccessor) context).mobarena$visitedElements().stream()
                    .anyMatch(entry -> entry.value() instanceof LootTable)) return;
            // Equipment is mapped when dropped, not when generated for a living mob.
            if (table.value().getParamSet() == LootContextParamSets.EQUIPMENT
                    || table.value().getParamSet() == LootContextParamSets.VILLAGER_TRADE) return;
            drops.replaceAll(stack -> HOOK.mapStack(context.getLevel(), stack));
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> HOOK.clear());
    }
}
