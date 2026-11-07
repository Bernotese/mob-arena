package dev.mobarena.arena;

import dev.mobarena.MobArena;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Spawns one wave mob on the ring (or in water) with tag, persistence, follow range and AI. */
final class WaveSpawner {
    static final double FOLLOW_RANGE = 128;
    private WaveSpawner() {}

    static Optional<EntityType<?>> type(String id) {
        var key = Identifier.tryParse(id);
        return key == null ? Optional.empty() : BuiltInRegistries.ENTITY_TYPE.getOptional(key);
    }
    /** Aquatic mobs (turtle, axolotl, guardians, dolphin, squids, tadpole, nautilus) spawn in water. */
    static boolean isWater(String id) {
        return type(id).map(t -> t.builtInRegistryHolder().is(EntityTypeTags.AQUATIC)).orElse(false);
    }

    static boolean spawn(ArenaBattle battle, SpawnPlanner.SpawnOrder order) {
        var arena = battle.arena().orElseThrow();
        ServerLevel level = arena.level();
        var type = type(order.entityId()).orElse(null);
        if (type == null) { MobArena.LOGGER.warn("Unknown wave mob {}", order.entityId()); return false; }
        Entity entity = type.create(level, EntitySpawnReason.MOB_SUMMONED);
        if (!(entity instanceof Mob mob)) {
            if (entity != null) entity.discard();
            MobArena.LOGGER.warn("Wave entry {} is not a mob", order.entityId());
            return false;
        }
        Vec3 pos = position(level, arena, order, type);
        mob.snapTo(pos.x, pos.y, pos.z, level.getRandom().nextFloat() * 360, 0);
        FINALIZING.set(true);
        try { mob.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(pos)), EntitySpawnReason.MOB_SUMMONED, null); }
        finally { FINALIZING.remove(); }
        removeJockeys(level, mob);
        prepare(mob, battle, battle.currentTag());
        if (!level.tryAddFreshEntityWithPassengers(mob)) {
            MobArena.LOGGER.warn("Cannot add wave mob {}", order.entityId());
            return false;
        }
        WaveMobAi.engage(mob, battle);
        return true;
    }

    /** True while a wave mob runs finalizeSpawn; ArenaRules then refuses JOCKEY spawns. */
    private static final ThreadLocal<Boolean> FINALIZING = ThreadLocal.withInitial(() -> false);
    static boolean finalizing() { return FINALIZING.get(); }

    /**
     * T7 decision: a team fights exactly the collected mobs. finalizeSpawn can add random jockeys:
     * a new chicken under a baby zombie (added to the level at once with reason JOCKEY, refused by
     * ArenaRules), a skeleton on a spider, or a rider on a strider (passengers, not yet in the level).
     * A baby zombie can also mount a chicken nearby. Undo all of it before the mob is added.
     */
    static void removeJockeys(ServerLevel level, Mob mob) {
        var vehicle = mob.getVehicle();
        if (vehicle != null) {
            mob.stopRiding();
            // A refused new chicken is not in the level; a chicken that was already there stays.
            if (level.getEntity(vehicle.getUUID()) != vehicle) vehicle.discard();
            // finalizeSpawn marked it as a jockey, and a jockey chicken despawns when far away.
            else if (vehicle instanceof net.minecraft.world.entity.animal.chicken.Chicken chicken) chicken.setChickenJockey(false);
        }
        var passengers = new ArrayList<Entity>();
        mob.getIndirectPassengers().forEach(passengers::add);
        for (var passenger : passengers) { passenger.ejectPassengers(); passenger.stopRiding(); passenger.discard(); }
    }

    /** Tag, persistence, follow range, glow. Used for spawns and for children. */
    static void prepare(Entity entity, ArenaBattle battle, String tag) {
        if (tag != null) entity.addTag(tag);
        if (entity instanceof Mob mob) {
            mob.setPersistenceRequired();
            var range = mob.getAttribute(Attributes.FOLLOW_RANGE);
            if (range != null && range.getBaseValue() < FOLLOW_RANGE) range.setBaseValue(FOLLOW_RANGE);
        }
        if (battle.glowing()) entity.setGlowingTag(true);
    }

    private static Vec3 position(ServerLevel level, Arena arena, SpawnPlanner.SpawnOrder order, EntityType<?> type) {
        var layout = arena.layout();
        var random = level.getRandom();
        if (order.water()) {
            if (!layout.waterSpawns().isEmpty()) {
                var water = layout.waterSpawns().get(order.pointIndex() % layout.waterSpawns().size());
                return Vec3.atBottomCenterOf(water);
            }
            // No basin: a 1-block water source in the floor under the ring point (SPEC 7.3 default).
            var point = layout.mobSpawns().get(order.pointIndex() % layout.mobSpawns().size());
            level.setBlockAndUpdate(point.below(), Blocks.WATER.defaultBlockState());
            return Vec3.atBottomCenterOf(point.below());
        }
        var points = layout.mobSpawns();
        int first = order.pointIndex() % points.size();
        for (int i = 0; i < points.size(); i++) {
            var point = Vec3.atBottomCenterOf(points.get((first + i) % points.size()));
            for (int attempt = 0; attempt < 4; attempt++) {
                double dx = attempt == 0 ? 0 : (random.nextDouble() - 0.5) * 3;
                double dz = attempt == 0 ? 0 : (random.nextDouble() - 0.5) * 3;
                var candidate = point.add(dx, 0, dz);
                if (level.noCollision(type.getSpawnAABB(candidate.x, candidate.y, candidate.z))
                        && layout.bounds().contains(candidate)) return candidate;
            }
            var above = point.add(0, 3, 0);
            if (level.noCollision(type.getSpawnAABB(above.x, above.y, above.z))) return above;
        }
        return Vec3.atBottomCenterOf(points.get(first));
    }
}
