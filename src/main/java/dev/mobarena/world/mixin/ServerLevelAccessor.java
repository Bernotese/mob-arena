package dev.mobarena.world.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets WorldCleanup close the entity storage of a removed game level without a full save. */
@Mixin(ServerLevel.class)
public interface ServerLevelAccessor {
    @Accessor("entityManager")
    PersistentEntitySectionManager<Entity> mobarena$entityManager();
}
