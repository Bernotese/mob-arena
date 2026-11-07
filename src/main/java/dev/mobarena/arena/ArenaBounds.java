package dev.mobarena.arena;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;
import java.util.Comparator;

/** SPEC 7.3: wave mobs that leave the arena bounds (or fall towards the void) go back to a spawn point. */
final class ArenaBounds {
    private ArenaBounds() {}

    static void check(ArenaBattle battle) {
        var arena = battle.arena().orElse(null);
        if (arena == null) return;
        var bounds = arena.layout().bounds();
        String prefix = WaveTags.teamPrefix(battle.team);
        for (var entity : arena.level().getEntities(EntityTypeTest.forClass(Entity.class),
                e -> e.isAlive() && !e.isPassenger() && e.entityTags().stream().anyMatch(t -> t.startsWith(prefix)))) {
            if (bounds.contains(entity.position())) continue;
            BlockPos target = nearest(entity, WaveSpawner.isWater(net.minecraft.world.entity.EntityType.getKey(entity.getType()).toString())
                    && !arena.layout().waterSpawns().isEmpty() ? arena.layout().waterSpawns() : arena.layout().mobSpawns());
            Vec3 pos = Vec3.atBottomCenterOf(target);
            entity.teleportTo(pos.x, pos.y, pos.z);
            entity.setDeltaMovement(Vec3.ZERO);
            entity.resetFallDistance();
        }
    }

    private static BlockPos nearest(Entity entity, java.util.List<BlockPos> points) {
        return points.stream().min(Comparator.comparingDouble(p -> entity.distanceToSqr(Vec3.atBottomCenterOf(p)))).orElseThrow();
    }
}
