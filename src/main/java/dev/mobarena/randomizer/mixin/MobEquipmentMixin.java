package dev.mobarena.randomizer.mixin;

import dev.mobarena.randomizer.RandomizerModule;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

// Owner T3: Fabric has no event for equipment dropped outside loot tables.
@Mixin(Mob.class)
abstract class MobEquipmentMixin {
    @ModifyArg(method = {"dropCustomDeathLoot", "equipItemIfPossible", "dropPreservedEquipment(Lnet/minecraft/server/level/ServerLevel;Ljava/util/function/Predicate;)Ljava/util/Set;"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Mob;spawnAtLocation(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/entity/item/ItemEntity;"), index = 1)
    private ItemStack mobarena$mapEquipment(ItemStack stack) {
        return RandomizerModule.hook().mapStack((ServerLevel) ((Mob) (Object) this).level(), stack);
    }

    @ModifyArg(method = "shearItem",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Mob;spawnAtLocation(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/entity/item/ItemEntity;"), index = 1)
    private ItemStack mobarena$mapShearedEquipment(ItemStack stack) {
        return RandomizerModule.hook().mapStack((ServerLevel) ((Mob) (Object) this).level(), stack);
    }
}

