package dev.mobarena.world.mixin;

import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Structure templates are shared by all levels, and Palette.blocks(Block) fills a plain HashMap.
 * RED and BLUE have the same seed, so their worldgen threads place the same structures at the same
 * time; that throws ConcurrentModificationException. Use a concurrent map for this cache.
 */
@Mixin(targets = "net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate$Palette")
public abstract class StructurePaletteMixin {
    @Shadow @Final @Mutable private Map<Block, List<?>> cache;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void mobarena$concurrentCache(CallbackInfo ci) {
        cache = new ConcurrentHashMap<>();
    }
}
