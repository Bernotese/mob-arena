package dev.mobarena.world.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.mobarena.world.StructureGenLock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.structures.EndCityStructure;
import org.spongepowered.asm.mixin.Mixin;

// T10e: EndCityPieces.TOWER_BRIDGE_GENERATOR has a static shipCreated flag; serialize piece generation.
@Mixin(EndCityStructure.class)
abstract class EndCityStructureMixin {
    @WrapMethod(method = "generatePieces")
    private void mobarena$lock(StructurePiecesBuilder builder, BlockPos pos, Rotation rotation, Structure.GenerationContext context,
                               Operation<Void> original) {
        synchronized (StructureGenLock.LOCK) { original.call(builder, pos, rotation, context); }
    }
}
