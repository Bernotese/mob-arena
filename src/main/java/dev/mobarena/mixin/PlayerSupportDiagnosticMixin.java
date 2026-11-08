package dev.mobarena.mixin;

import dev.mobarena.MobArena;
import dev.mobarena.arena.ArenaBattles;
import dev.mobarena.core.GameManager;
import dev.mobarena.core.Teleports;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observe vanilla's support-block resend without changing its movement handling. */
@Mixin(ServerGamePacketListenerImpl.class)
abstract class PlayerSupportDiagnosticMixin {
    @Shadow public ServerPlayer player;
    @Shadow private int tickCount;
    @Shadow private int lastSupportedBlocksSend;
    @Shadow public abstract boolean hasClientLoaded();

    @Inject(method = "forceSendPlayerSupportBlocks", at = @At("HEAD"))
    private void mobarena$logUnsupportedPlayer(CallbackInfo ci) {
        if (!Boolean.getBoolean("mobarena.debug") || tickCount < lastSupportedBlocksSend + 200) return;
        var level = player.level();
        var below = BlockPos.containing(player.getX(), player.getY() - 1.0E-6, player.getZ());
        // Do not load a chunk merely to collect diagnostics.
        var state = level.hasChunkAt(below) ? level.getBlockState(below) : null;
        var box = player.getBoundingBox();
        double supportY = box.minY - 9.999999747378752E-6;
        var corners = java.util.List.of(BlockPos.containing(box.minX, supportY, box.minZ),
                BlockPos.containing(box.maxX, supportY, box.minZ),
                BlockPos.containing(box.minX, supportY, box.maxZ),
                BlockPos.containing(box.maxX, supportY, box.maxZ)).stream().map(this::mobarena$describeBlock).toList();
        var ctx = GameManager.get().context();
        var team = ctx == null ? null : ctx.team(player.getUUID());
        var group = ArenaBattles.get(ctx);
        var battle = group == null || team == null ? null : group.battle(team);
        MobArena.LOGGER.info("MOBARENA_STANDING_ON_AIR player={} phase={} level={} position={} gameMode={} "
                        + "onGround={} verticalCollisionBelow={} deltaMovement={} below={} belowState={} "
                        + "belowCollisionEmpty={} mainSupportingBlockPos={} cornerBlocks={} battleState={} down={} "
                        + "vehicle={} effects={} ticksSinceLastModTeleport={} hasClientLoaded={}",
                player.getPlainTextName(), GameManager.get().phase(), level.dimension().identifier(), player.position(),
                player.gameMode(), player.onGround(), player.verticalCollisionBelow, player.getDeltaMovement(), below,
                state == null ? "unloaded" : state, state == null ? "unknown" : state.getCollisionShape(level, below).isEmpty(),
                player.mainSupportingBlockPos, corners, battle == null ? "none" : battle.state().name(),
                group != null && group.isDown(player.getUUID()), player.getVehicle(), player.getActiveEffects(), Teleports.ticksSinceLastTeleport(player), hasClientLoaded());
    }

    @Unique
    private String mobarena$describeBlock(BlockPos pos) {
        var level = player.level();
        if (!level.hasChunkAt(pos)) return pos + ":unloaded";
        var state = level.getBlockState(pos);
        return pos + ":" + state + ",collisionEmpty=" + state.getCollisionShape(level, pos).isEmpty();
    }
}
