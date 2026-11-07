package dev.mobarena.arena;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.animal.dolphin.Dolphin;
import net.minecraft.world.entity.animal.equine.Llama;
import net.minecraft.world.entity.animal.panda.Panda;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Zoglin;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.warden.Warden;
import java.util.Comparator;

/** SPEC 7.3: hostile mobs target the nearest team player, neutral mobs are angry, passive mobs do nothing. */
final class WaveMobAi {
    private WaveMobAi() {}

    static boolean neutral(Mob mob) {
        return mob instanceof NeutralMob || mob instanceof AbstractPiglin || mob instanceof Hoglin || mob instanceof Zoglin
                || mob instanceof Warden || mob instanceof Llama || mob instanceof Panda || mob instanceof Dolphin;
    }
    static boolean hostile(Mob mob) { return mob instanceof Enemy; }

    static ServerPlayer nearest(Mob mob, ArenaBattle battle) {
        return battle.group.onlinePlayers(battle.team).stream().filter(p -> battle.group.alive(p, battle))
                .min(Comparator.comparingDouble(p -> p.distanceToSqr(mob))).orElse(null);
    }
    static boolean validTarget(LivingEntity target, ArenaBattle battle) {
        return target instanceof ServerPlayer player && battle.group.ctx().team(player.getUUID()) == battle.team
                && battle.group.alive(player, battle);
    }

    static void engage(Mob mob, ArenaBattle battle) {
        if (!hostile(mob) && !neutral(mob)) return;
        var target = nearest(mob, battle);
        if (target == null) return;
        int angerTicks = Math.max(600, battle.script.maxWaveTicks());
        if (mob instanceof NeutralMob angry) {
            angry.setPersistentAngerTarget(EntityReference.of(target));
            angry.setTimeToRemainAngry(angerTicks);
        }
        if (mob instanceof AbstractPiglin || mob instanceof Hoglin || mob instanceof Zoglin) {
            var brain = mob.getBrain();
            if (brain.checkMemory(MemoryModuleType.ANGRY_AT, MemoryStatus.REGISTERED))
                brain.setMemoryWithExpiry(MemoryModuleType.ANGRY_AT, target.getUUID(), angerTicks);
            if (brain.checkMemory(MemoryModuleType.ATTACK_TARGET, MemoryStatus.REGISTERED))
                brain.setMemory(MemoryModuleType.ATTACK_TARGET, target);
        }
        if (mob instanceof Warden warden) warden.increaseAngerAt(target, 150, false);
        if (mob instanceof Llama || mob instanceof Panda || mob instanceof Dolphin) mob.setLastHurtByMob(target);
        mob.setTarget(target);
    }

    static boolean waveMob(Entity entity) {
        return WaveTags.isWaveEntity(entity);
    }

    /**
     * Every tick: a wave mob never keeps another wave mob as target. Iron golems and others pick any Enemy,
     * also a mob of the same wave; that would start a fight between wave mobs.
     */
    static void dropWaveTargets(ArenaBattle battle) {
        for (var entity : battle.waveMobs()) {
            if (!(entity instanceof Mob mob)) continue;
            boolean invalid = waveMob(mob.getTarget());
            var brain = mob.getBrain();
            if (brain.checkMemory(MemoryModuleType.ATTACK_TARGET, MemoryStatus.REGISTERED)
                    && waveMob(brain.getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null))) {
                brain.eraseMemory(MemoryModuleType.ATTACK_TARGET);
                invalid = true;
            }
            if (mob instanceof NeutralMob angry && angry.getPersistentAngerTarget() != null
                    && waveMob(angry.getPersistentAngerTarget().getEntity(mob.level(), LivingEntity.class))) {
                angry.stopBeingAngry();
                invalid = true;
            }
            if (!invalid) continue;
            mob.setTarget(null);
            engage(mob, battle);
        }
    }

    /** Every 20 ticks: give a new target to wave mobs without a valid one; keep neutral mobs angry. */
    static void retarget(ArenaBattle battle) {
        for (var entity : battle.waveMobs()) {
            if (!(entity instanceof Mob mob)) continue;
            if (neutral(mob) || !validTarget(mob.getTarget(), battle)) engage(mob, battle);
        }
    }
}
