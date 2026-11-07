package dev.mobarena.world.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.mobarena.world.StructureGenLock;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.structures.StrongholdStructure;
import org.spongepowered.asm.mixin.Mixin;

// T10e: StrongholdPieces keeps static mutable state; serialize piece generation over all levels.
@Mixin(StrongholdStructure.class)
abstract class StrongholdStructureMixin {
    @WrapMethod(method = "generatePieces")
    private static void mobarena$lock(StructurePiecesBuilder builder, Structure.GenerationContext context, Operation<Void> original) {
        synchronized (StructureGenLock.LOCK) { original.call(builder, context); }
    }
}
