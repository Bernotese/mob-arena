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
// T5: generate gateway exits within each team's End.
@Mixin(net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity.class)
abstract class EndGatewayMixin {
    @WrapOperation(method = "getPortalPosition", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;dimension()Lnet/minecraft/resources/ResourceKey;"))
    private ResourceKey<Level> mobarena$key(ServerLevel level, Operation<ResourceKey<Level>> original) {
        return PortalKeys.vanillaKeyOf(level);
    }
}
