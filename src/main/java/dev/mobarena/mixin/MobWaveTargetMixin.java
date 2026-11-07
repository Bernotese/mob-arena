package dev.mobarena.mixin;

import dev.mobarena.arena.WaveTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// T7b: a wave mob never takes another wave mob as target (goal AI). Brain AI is reset each tick in WaveMobAi.
@Mixin(Mob.class)
abstract class MobWaveTargetMixin {
    @Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
    private void mobarena$noWaveTarget(LivingEntity target, CallbackInfo ci) {
        if (target != null && WaveTags.isWaveEntity(target) && WaveTags.isWaveEntity((Mob) (Object) this)) ci.cancel();
    }
}
