package dev.mobarena.core;

import dev.mobarena.MobArena;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.server.level.ServerPlayer;
import java.util.List;

/** Server thread only. Respawn through the connection to replace its player reference. */
public final class DeadPlayers {
    private DeadPlayers() {}

    public static ServerPlayer revive(ServerPlayer player) {
        if (player.isDeadOrDying() && player.connection != null
                && player.level().getServer().getPlayerList().getPlayer(player.getUUID()) == player) {
            try {
                player.connection.handleClientCommand(new ServerboundClientCommandPacket(
                        ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
            } catch (Exception e) {
                MobArena.LOGGER.error("Cannot revive player {}", player.getUUID(), e);
            }
        }
        return player.connection == null ? player : player.connection.player;
    }

    public static void reviveTeamPlayers(GameContext ctx) {
        for (var player : List.copyOf(ctx.server().getPlayerList().getPlayers())) {
            if (ctx.team(player.getUUID()) != null && player.isDeadOrDying()) revive(player);
        }
    }
}
