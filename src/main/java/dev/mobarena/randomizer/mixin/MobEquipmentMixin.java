package dev.mobarena.randomizer.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.mobarena.randomizer.RandomizerModule;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Mob.class)
abstract class MobEquipmentMixin {
    // Vanilla drops generated equipment outside loot tables; preserved equipment belongs to players.
    @WrapOperation(method = "dropCustomDeathLoot",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Mob;spawnAtLocation(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/entity/item/ItemEntity;"))
    private ItemEntity mobarena$mapEquipment(Mob mob, ServerLevel level, ItemStack stack,
                                            Operation<ItemEntity> original, @Local EquipmentSlot slot) {
        ItemStack drop = mob.getDropChances().isPreserved(slot)
                ? stack : RandomizerModule.hook().mapStack(level, stack);
        return original.call(mob, level, drop);
    }
}
