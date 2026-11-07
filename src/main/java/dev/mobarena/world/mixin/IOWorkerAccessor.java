package dev.mobarena.world.mixin;

import net.minecraft.util.thread.PriorityConsecutiveExecutor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.IOWorker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.SequencedMap;

/** Lets WorldCleanup drop queued writes of a removed game level before its worker closes. */
@Mixin(IOWorker.class)
public interface IOWorkerAccessor {
    @Accessor("consecutiveExecutor")
    PriorityConsecutiveExecutor mobarena$executor();
    @Accessor("pendingWrites")
    SequencedMap<ChunkPos, ?> mobarena$pendingWrites();
}
