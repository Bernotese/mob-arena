package dev.mobarena.arena;

import dev.mobarena.core.GameManager;
import dev.mobarena.world.GameLevels;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.Level;

/** Fabric events of the arena: kills, children, spawn rules, spawn eggs, deaths. */
final class ArenaRules {
    private ArenaRules() {}

    static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            var battle = ArenaBattles.at(entity.level());
            if (battle != null) battle.countKill(entity);
        });
        // Slime splits and all other conversions: the child keeps the wave tags (SPEC 7.3).
        ServerLivingEntityEvents.MOB_CONVERSION.register((from, to, params) -> {
            var tags = from.entityTags().stream().filter(WaveTags::isWaveTag).toList();
            if (tags.isEmpty()) return;
            var battle = ArenaBattles.at(from.level());
            tags.forEach(to::addTag);
            if (from.isCurrentlyGlowing() || from.hasGlowingTag()) to.setGlowingTag(true);
            to.setPersistenceRequired();
            if (battle != null) WaveSpawner.prepare(to, battle, null);
        });
        ServerEntityEvents.ALLOW_LOAD.register(ArenaRules::allowLoad);
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            // Engage children after they are in the level (ALLOW_LOAD runs before the add).
            if (!(entity instanceof Mob mob)) return;
            var battle = ArenaBattles.at(level);
            if (battle != null && battle.currentTag() != null && mob.entityTags().contains(battle.currentTag())
                    && mob.getTarget() == null) WaveMobAi.engage(mob, battle);
        });
        UseItemCallback.EVENT.register((player, level, hand) -> eggCheck(player, level, hand));
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> eggCheck(player, level, hand));
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> eggCheck(player, level, hand));
        ServerPlayerEvents.ALLOW_DEATH.register((player, source, amount) -> {
            var group = ArenaBattles.get(GameManager.get().context());
            return group == null || group.respawns().allowDeath(player, source);
        });
    }

    static boolean isArena(Level level) { return GameLevels.isArena(level) || ArenaBattles.at(level) != null; }

    private static boolean allowLoad(Entity entity, ServerLevel level, EntitySpawnReason reason, boolean fromDisk) {
        if (fromDisk || !(entity instanceof Mob mob) || !isArena(level)) return true;
        if (reason != null) switch (reason) {
            // No natural spawns in arenas; no player mobs from eggs, dispensers or buckets (T7 Q8).
            case NATURAL, CHUNK_GENERATION, PATROL, EVENT, STRUCTURE, SPAWNER, TRIAL_SPAWNER,
                 SPAWN_ITEM_USE, DISPENSER, BUCKET -> { return false; }
            case COMMAND, LOAD, DIMENSION_TRAVEL -> { return true; }
            default -> { }
        }
        // Summons, reinforcements, jockeys, breeding and conversions during a wave are children of the wave.
        var battle = ArenaBattles.at(level);
        if (battle != null && battle.currentTag() != null && mob.entityTags().stream().noneMatch(WaveTags::isWaveTag))
            WaveSpawner.prepare(mob, battle, battle.currentTag());
        return true;
    }

    private static InteractionResult eggCheck(Player player, Level level, InteractionHand hand) {
        if (level.isClientSide() || !(player.getItemInHand(hand).getItem() instanceof SpawnEggItem) || !isArena(level))
            return InteractionResult.PASS;
        if (player instanceof ServerPlayer serverPlayer)
            serverPlayer.sendOverlayMessage(Component.literal("Spawn eggs do not work in the arena"));
        return InteractionResult.FAIL;
    }
}
