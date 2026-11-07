package dev.mobarena.world.mixin;

import dev.mobarena.world.GameLevels;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// T5: isolate arena weather from the server's global rain state.
@Mixin(Level.class)
abstract class LevelWeatherMixin {
    @Inject(method = "canHaveWeather", at = @At("HEAD"), cancellable = true)
    private void mobarena$noWeather(CallbackInfoReturnable<Boolean> cir) {
        if (GameLevels.isArena((Level) (Object) this)) cir.setReturnValue(false);
    }
}
