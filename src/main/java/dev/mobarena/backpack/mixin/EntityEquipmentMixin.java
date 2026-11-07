package dev.mobarena.backpack.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.mobarena.backpack.GuardedItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// T4: Fabric has no event that preserves guarded offhand equipment during death drops.
@Mixin(EntityEquipment.class)
abstract class EntityEquipmentMixin {
    @WrapOperation(method = "dropAll", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;createItemStackToDrop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;"))
    private ItemEntity skipGuardedDrop(LivingEntity owner, ItemStack stack, boolean random, boolean retainOwner, Operation<ItemEntity> original) {
        if (owner instanceof ServerPlayer && GuardedItems.isGuarded(stack)) return null;
        return original.call(owner, stack, random, retainOwner);
    }

    @WrapOperation(method = "dropAll", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/EntityEquipment;clear()V"))
    private void keepGuardedEquipment(EntityEquipment equipment, Operation<Void> original, LivingEntity owner) {
        if (!(owner instanceof ServerPlayer)) { original.call(equipment); return; }
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (!GuardedItems.isGuarded(equipment.get(slot))) equipment.set(slot, ItemStack.EMPTY);
        }
    }
}
