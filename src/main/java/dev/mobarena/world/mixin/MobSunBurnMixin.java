package dev.mobarena.world.mixin;

import dev.mobarena.world.GameLevels;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// T5: keep arena undead from burning under the noon sky.
@Mixin(Mob.class)
abstract class MobSunBurnMixin {
    @Inject(method = "isSunBurnTick", at = @At("HEAD"), cancellable = true)
    private void mobarena$noSunBurn(CallbackInfoReturnable<Boolean> cir) {
        if (GameLevels.isArena(((Mob) (Object) this).level())) cir.setReturnValue(false);
    }
}
