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
// T5: isolate team portals and stop portals outside prep worlds.
@Mixin(EndPortalBlock.class)
abstract class EndPortalBlockMixin {
    @Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
    private void mobarena$guard(BlockState state, Level level, BlockPos pos, Entity entity,
                               InsideBlockEffectApplier effects, boolean moving, CallbackInfo ci) {
        if (!GameLevels.isPrepLevel(level)) ci.cancel();
    }
    @WrapOperation(method = "getPortalDestination", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;dimension()Lnet/minecraft/resources/ResourceKey;"))
    private ResourceKey<Level> mobarena$key(ServerLevel level, Operation<ResourceKey<Level>> original) {
        return PortalKeys.vanillaKeyOf(level);
    }
    @WrapOperation(method = "getPortalDestination", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/MinecraftServer;getLevel(Lnet/minecraft/resources/ResourceKey;)Lnet/minecraft/server/level/ServerLevel;"))
    private ServerLevel mobarena$destination(MinecraftServer server, ResourceKey<Level> key,
            Operation<ServerLevel> original, @Local(argsOnly = true) ServerLevel source) {
        return GameLevels.isPrepLevel(source) ? PortalKeys.teamLevel(source, key) : original.call(server, key);
    }
    @WrapOperation(method = "getPortalDestination", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/storage/LevelData$RespawnData;pos()Lnet/minecraft/core/BlockPos;"))
    private BlockPos mobarena$exit(net.minecraft.world.level.storage.LevelData.RespawnData data,
            Operation<BlockPos> original, @Local(argsOnly = true) ServerLevel source) {
        return GameLevels.kindOf(source).orElse(null) == WorldKind.PREP_END
                ? PortalKeys.prepSpawn(source) : original.call(data);
    }
    @WrapOperation(method = "getPortalDestination", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;adjustSpawnLocation(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/BlockPos;"))
    private BlockPos mobarena$itemExit(Entity entity, ServerLevel destination, BlockPos pos,
            Operation<BlockPos> original, @Local(argsOnly = true) ServerLevel source) {
        // Entity.adjustSpawnLocation ignores pos and reads the global spawn again.
        return GameLevels.kindOf(source).orElse(null) == WorldKind.PREP_END
                ? PortalKeys.prepSpawn(source) : original.call(entity, destination, pos);
    }
}
