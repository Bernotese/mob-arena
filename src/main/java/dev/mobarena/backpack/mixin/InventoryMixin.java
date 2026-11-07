package dev.mobarena.backpack.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.mobarena.backpack.GuardedItems;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// T4: Fabric has no event that preserves individual inventory slots during death drops.
@Mixin(Inventory.class)
abstract class InventoryMixin {
    @WrapOperation(method = "dropAll", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;isEmpty()Z"))
    private boolean skipGuardedSlot(ItemStack stack, Operation<Boolean> original) {
        return original.call(stack) || GuardedItems.isGuarded(stack);
    }
}
