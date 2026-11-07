package dev.mobarena.backpack;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Shared protection for backpack and wave menu items. All calls use the server thread. */
public final class GuardedItems {
    private record Rule(Supplier<ItemStack> factory, Predicate<ServerPlayer> required) { }
    private static final Map<String, Rule> RULES = new LinkedHashMap<>();
    private GuardedItems() { }

    /** Factories must return items with a boolean custom-data tag matching key. */
    public static void register(String key, Supplier<ItemStack> factory, Predicate<ServerPlayer> required) {
        if (RULES.putIfAbsent(key, new Rule(factory, required)) != null) {
            throw new IllegalArgumentException("Duplicate guarded item: " + key);
        }
    }

    public static boolean hasTag(ItemStack stack, String key) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
            .copyTag().getBoolean(key).orElse(false);
    }

    public static boolean isGuarded(ItemStack stack) {
        return !stack.isEmpty() && RULES.keySet().stream().anyMatch(key -> hasTag(stack, key));
    }

    public static void restore(ServerPlayer player) {
        RULES.forEach((key, rule) -> {
            if (!rule.required.test(player)) return;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                if (hasTag(player.getInventory().getItem(i), key)) return;
            }
            if (hasTag(player.containerMenu.getCarried(), key)) return;
            ItemStack replacement = rule.factory.get();
            int free = player.getInventory().getFreeSlot();
            if (free >= 0) {
                player.getInventory().setItem(free, replacement);
            } else {
                // Reserve one slot even when the inventory is full. Preserve its previous item.
                ItemStack displaced = player.getInventory().removeItemNoUpdate(35);
                player.getInventory().setItem(35, replacement);
                player.drop(displaced, false, net.minecraft.util.Prediction.SERVER_ONLY);
            }
        });
    }

    /** Keep guarded items in player storage; reject all container transfers and special clicks. */
    public static boolean blocksClick(AbstractContainerMenu menu, int slot, int button,
                                      ContainerInput input, ServerPlayer player) {
        ItemStack clicked = slot >= 0 && slot < menu.slots.size()
            ? menu.getSlot(slot).getItem() : ItemStack.EMPTY;
        ItemStack carried = menu.getCarried();
        if (input == ContainerInput.SWAP && button >= 0 && button < player.getInventory().getContainerSize()
            && isGuarded(player.getInventory().getItem(button))) return true;
        if (!isGuarded(clicked) && !isGuarded(carried)) return false;
        if (input != ContainerInput.PICKUP) return true;
        if (slot < 0 || slot >= menu.slots.size()) return true;
        return menu.getSlot(slot).container != player.getInventory();
    }
}
