package dev.mobarena.backpack;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Shared protection for backpack and wave menu items. All calls use the server thread. */
public final class GuardedItems {
    private record Rule(Supplier<ItemStack> factory, Predicate<ServerPlayer> required) { }
    private static final Map<String, Rule> RULES = new LinkedHashMap<>();
    private static final Map<String, CompoundTag> TAG_MATCHERS = new LinkedHashMap<>();
    private GuardedItems() { }

    /** Factories must return items with a boolean custom-data tag matching key. */
    public static void register(String key, Supplier<ItemStack> factory, Predicate<ServerPlayer> required) {
        if (RULES.putIfAbsent(key, new Rule(factory, required)) != null) {
            throw new IllegalArgumentException("Duplicate guarded item: " + key);
        }
    }

    public static boolean hasTag(ItemStack stack, String key) {
        return hasTag((DataComponentGetter) stack, key);
    }

    private static boolean hasTag(DataComponentGetter stack, String key) {
        CompoundTag pattern = TAG_MATCHERS.computeIfAbsent(key, name -> {
            CompoundTag tag = new CompoundTag(); tag.putBoolean(name, true); return tag;
        });
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).matchedBy(pattern);
    }

    public static boolean isGuarded(ItemStack stack) {
        return !stack.isEmpty() && containsGuarded(stack);
    }

    private static boolean containsGuarded(DataComponentGetter stack) {
        if (RULES.keySet().stream().anyMatch(key -> hasTag(stack, key))) return true;
        var bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (bundle != null && bundle.items().stream().anyMatch(GuardedItems::containsGuarded)) return true;
        var container = stack.get(DataComponents.CONTAINER);
        if (container != null) for (var item : container.nonEmptyItems()) if (containsGuarded(item)) return true;
        return false;
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
                for (int slot = 35; slot >= 0; slot--) {
                    if (isGuarded(player.getInventory().getItem(slot))) continue;
                    ItemStack displaced = player.getInventory().removeItemNoUpdate(slot);
                    player.getInventory().setItem(slot, replacement);
                    player.drop(displaced, false, net.minecraft.util.Prediction.SERVER_ONLY);
                    break;
                }
            }
        });
    }

    private static boolean isContainerItem(ItemStack stack) {
        return stack.has(DataComponents.BUNDLE_CONTENTS) || stack.has(DataComponents.CONTAINER);
    }

    /** Keep guarded items in player storage; reject all container transfers and special clicks. */
    public static boolean blocksClick(AbstractContainerMenu menu, int slot, int button,
                                      ContainerInput input, ServerPlayer player) {
        ItemStack clicked = slot >= 0 && slot < menu.slots.size()
            ? menu.getSlot(slot).getItem() : ItemStack.EMPTY;
        ItemStack carried = menu.getCarried();
        if (input == ContainerInput.SWAP && button >= 0 && button < player.getInventory().getContainerSize()
            && isGuarded(player.getInventory().getItem(button))) return true;
        boolean clickedGuarded = isGuarded(clicked), carriedGuarded = isGuarded(carried);
        if ((clickedGuarded && isContainerItem(carried)) || (carriedGuarded && isContainerItem(clicked))) return true;
        if (!clickedGuarded && !carriedGuarded) return false;
        if (input != ContainerInput.PICKUP) return true;
        if (slot < 0 || slot >= menu.slots.size()) return true;
        return menu.getSlot(slot).container != player.getInventory();
    }
}
