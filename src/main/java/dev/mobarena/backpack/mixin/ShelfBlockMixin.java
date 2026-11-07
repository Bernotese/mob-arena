package dev.mobarena.backpack.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.mobarena.backpack.GuardedItems;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ShelfBlock;
import net.minecraft.world.level.block.entity.ShelfBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// T4: Powered shelves bypass container clicks when swapping whole hotbar sections.
@Mixin(ShelfBlock.class)
abstract class ShelfBlockMixin {
    @WrapOperation(method = "swapHotbar", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/block/entity/ShelfBlockEntity;swapItemNoUpdate(ILnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack keepGuardedHotbarStack(ShelfBlockEntity shelf, int slot, ItemStack incoming, Operation<ItemStack> original) {
        // The caller restores this returned stack to the same hotbar slot.
        if (GuardedItems.isGuarded(incoming)) return incoming;
        return original.call(shelf, slot, incoming);
    }
}
