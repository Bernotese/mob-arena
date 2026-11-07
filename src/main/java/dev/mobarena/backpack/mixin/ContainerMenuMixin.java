package dev.mobarena.backpack.mixin;

import dev.mobarena.backpack.GuardedItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// T4: Fabric has no general event for container clicks, swaps, throws and quick craft.
@Mixin(AbstractContainerMenu.class)
abstract class ContainerMenuMixin {
    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void guardClick(int slot, int button, ContainerInput input, Player player, CallbackInfo ci) {
        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
        if (player instanceof ServerPlayer serverPlayer
            && GuardedItems.blocksClick(menu, slot, button, input, serverPlayer)) {
            menu.sendAllDataToRemote();
            ci.cancel();
        }
    }
}
