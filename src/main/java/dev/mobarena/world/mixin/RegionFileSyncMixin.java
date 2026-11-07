package dev.mobarena.world.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import dev.mobarena.world.WorldCleanup;
import net.minecraft.world.level.chunk.storage.RegionFile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import java.nio.channels.FileChannel;

/** Skips fsync when WorldCleanup closes a removed game level whose folder is deleted next. */
@Mixin(RegionFile.class)
public abstract class RegionFileSyncMixin {
    @WrapWithCondition(method = "close", require = 2, at = @At(value = "INVOKE", target = "Ljava/nio/channels/FileChannel;force(Z)V"))
    private boolean mobarena$syncUnlessDeleted(FileChannel channel, boolean metaData) {
        return !WorldCleanup.closingDeletedLevel();
    }
}
