package dev.mobarena.randomizer.mixin;

import java.util.Set;
import net.minecraft.world.level.storage.loot.LootContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// Fabric fires for nested tables; its event does not expose the parent table stack.
@Mixin(LootContext.class)
public interface LootContextAccessor {
    @Accessor("visitedElements")
    Set<LootContext.VisitedEntry<?>> mobarena$visitedElements();
}
