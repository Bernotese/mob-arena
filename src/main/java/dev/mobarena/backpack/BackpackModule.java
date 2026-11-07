package dev.mobarena.backpack;

import java.util.Objects;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;

public final class BackpackModule {
    private static BackpackAccess access = player -> null;
    private BackpackModule() { }

    public static void setAccess(BackpackAccess value) { access = Objects.requireNonNull(value); }

    public static boolean open(ServerPlayer player) {
        String team = access.activeTeam(player);
        if (team == null) return false;
        TeamBackpack.get(player.level().getServer()).open(player, team);
        return true;
    }

    public static void init() {
        GuardedItems.register(BackpackItem.TAG, BackpackItem::create, player -> access.activeTeam(player) != null);
        ServerTickEvents.END_SERVER_TICK.register(server -> server.getPlayerList().getPlayers().forEach(GuardedItems::restore));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> GuardedItems.restore(newPlayer));
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer player) player.closeContainer();
        });
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (!GuardedItems.hasTag(player.getItemInHand(hand), BackpackItem.TAG)) return InteractionResult.PASS;
            if (player instanceof ServerPlayer serverPlayer) open(serverPlayer);
            return InteractionResult.SUCCESS;
        });
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (!GuardedItems.hasTag(player.getItemInHand(hand), BackpackItem.TAG)) return InteractionResult.PASS;
            if (player instanceof ServerPlayer serverPlayer) open(serverPlayer);
            return InteractionResult.SUCCESS;
        });
        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (!GuardedItems.hasTag(player.getItemInHand(hand), BackpackItem.TAG)) return InteractionResult.PASS;
            if (player instanceof ServerPlayer serverPlayer) open(serverPlayer);
            return InteractionResult.SUCCESS;
        });
    }
}
