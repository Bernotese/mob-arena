package dev.mobarena.world.mixin;

import net.minecraft.world.level.storage.SavedDataStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.concurrent.CompletableFuture;

@Mixin(SavedDataStorage.class)
public interface SavedDataStorageAccessor {
    @Accessor("pendingWriteFuture")
    CompletableFuture<?> mobarena$pendingWrites();
}
