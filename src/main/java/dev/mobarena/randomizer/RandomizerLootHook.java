package dev.mobarena.randomizer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.GameMasterBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Server-thread state. Replace this state at each game start and clear it at game end. */
public final class RandomizerLootHook {
    private TeamWorldLookup worlds = level -> Optional.empty();
    private final Map<String, Map<Item, Item>> maps = new HashMap<>();
    private boolean enabled;

    public void startGame(long seed, List<String> teams, TeamWorldLookup worlds, boolean enabled) {
        clear();
        this.worlds = worlds;
        this.enabled = enabled;
        List<Item> items = BuiltInRegistries.ITEM.stream().toList();
        for (String team : teams) {
            maps.put(team, ItemRandomizer.create(seed, team, items, RandomizerLootHook::allowed));
        }
    }

    public void clear() {
        maps.clear();
        worlds = level -> Optional.empty();
        enabled = false;
    }

    public Map<Item, Item> mapFor(String team) {
        return maps.getOrDefault(team, Map.of());
    }

    public ItemStack mapStack(ServerLevel level, ItemStack stack) {
        if (!enabled || stack.isEmpty()) return stack;
        Item target = worlds.teamFor(level).map(maps::get).map(map -> map.get(stack.getItem())).orElse(null);
        if (target == null) return stack;
        ItemStack result = new ItemStack(target);
        result.setCount(ItemRandomizer.capCount(stack.getCount(), result.getMaxStackSize()));
        return result;
    }

    public static boolean allowed(Item item) {
        return item != Items.AIR && item != Items.BEDROCK && !(item instanceof GameMasterBlockItem)
                && item != Items.STRUCTURE_VOID && item != Items.DEBUG_STICK && item != Items.BARRIER && item != Items.LIGHT;
    }
}
