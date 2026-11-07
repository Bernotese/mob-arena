package dev.mobarena.backpack;

import java.util.Objects;
import dev.mobarena.core.*;
import dev.mobarena.command.CommandRegistry;
import dev.mobarena.team.TeamId;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
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
        TeamId team = access.activeTeam(player);
        if (team == null) return false;
        TeamBackpack.get(player.level().getServer()).open(player, team);
        return true;
    }

    public static void useGameAccess() {
        setAccess(player -> {
            GameManager game = GameManager.get();
            if (game.context() == null || (game.phase() != Phase.PREP && game.phase() != Phase.WAVE_CONFIG && game.phase() != Phase.ARENA && game.phase() != Phase.SUDDEN_DEATH)) return null;
            return game.context().team(player.getUUID());
        });
    }

    public static void init() {
        useGameAccess();
        GameManager.get().register(new PhaseListener() {
            @Override public void onEnter(Phase phase, GameContext ctx) {
                if (phase == Phase.PREP || phase == Phase.LOBBY) TeamBackpack.get(ctx.server()).clear(ctx.server());
                if (phase == Phase.END) TeamBackpack.get(ctx.server()).closeViewers(ctx.server());
                if (phase == Phase.PREP) ctx.server().getPlayerList().getPlayers().forEach(GuardedItems::restore);
            }
        });
        CommandRegistry.register(root -> root.then(Commands.literal("backpack").executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            if (open(player)) return 1;
            ctx.getSource().sendFailure(Component.literal("The team backpack is not available.")); return 0;
        })));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (access.activeTeam(handler.player) == null) TeamBackpack.removeBackpacks(handler.player);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(TeamBackpack::release);
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
