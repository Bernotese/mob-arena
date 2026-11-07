package dev.mobarena.backpack.mixin;

import dev.mobarena.backpack.GuardedItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// T4: Fabric has no event that cancels every player drop route.
@Mixin(ServerPlayer.class)
abstract class ServerPlayerMixin {
    @Inject(method = "drop(Z)V", at = @At("HEAD"), cancellable = true)
    private void blockDropKey(boolean all, CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        if (GuardedItems.isGuarded(player.getInventory().getSelectedItem())) ci.cancel();
    }

    @Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;ZLnet/minecraft/util/Prediction;)Lnet/minecraft/world/entity/item/ItemEntity;",
        at = @At("HEAD"), cancellable = true)
    private void blockDrop(ItemStack stack, boolean random, Prediction prediction, CallbackInfoReturnable<ItemEntity> cir) {
        if (GuardedItems.isGuarded(stack)) cir.setReturnValue(null);
    }
}
