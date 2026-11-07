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
// T5: allow lighting Nether portals in team overworlds and Nethers.
@Mixin(BaseFireBlock.class)
abstract class BaseFireBlockMixin {
    @Inject(method = "inPortalDimension", at = @At("HEAD"), cancellable = true)
    private static void mobarena$light(Level level, CallbackInfoReturnable<Boolean> cir) {
        var kind = GameLevels.kindOf(level).orElse(null);
        if (kind == WorldKind.PREP_OVERWORLD || kind == WorldKind.PREP_NETHER) cir.setReturnValue(true);
    }
}
