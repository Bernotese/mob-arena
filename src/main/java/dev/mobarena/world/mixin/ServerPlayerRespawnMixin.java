package dev.mobarena.world.mixin;

import dev.mobarena.world.*;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
// T5: select a team respawn level before restoreFrom reads keepInventory.
@Mixin(net.minecraft.server.level.ServerPlayer.class)
abstract class ServerPlayerRespawnMixin {
    @Inject(method = "findRespawnPositionAndUseSpawnBlock", at = @At("RETURN"), cancellable = true)
    private void mobarena$respawn(boolean alive, net.minecraft.world.level.portal.TeleportTransition.PostTeleportTransition action,
            CallbackInfoReturnable<net.minecraft.world.level.portal.TeleportTransition> cir) {
        var player = (net.minecraft.server.level.ServerPlayer) (Object) this;
        if (!RespawnRouter.accepts(player, cir.getReturnValue())) {
            RespawnRouter.route(player).map(result -> new net.minecraft.world.level.portal.TeleportTransition(
                    result.newLevel(), result.position(), net.minecraft.world.phys.Vec3.ZERO, 0, 0, action))
                    .ifPresent(cir::setReturnValue);
        }
    }
}
