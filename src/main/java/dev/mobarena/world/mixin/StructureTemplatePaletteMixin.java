package dev.mobarena.world.mixin;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// T10e bug 1: templates are shared by all levels; same-seed RED/BLUE worldgen reads one palette from
// several threads. The vanilla HashMap cache throws ConcurrentModificationException.
@Mixin(StructureTemplate.Palette.class)
abstract class StructureTemplatePaletteMixin {
    @Shadow @Final @Mutable private Map<Block, List<StructureTemplate.StructureBlockInfo>> cache;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void mobarena$concurrentCache(CallbackInfo ci) { cache = new ConcurrentHashMap<>(); }
}
