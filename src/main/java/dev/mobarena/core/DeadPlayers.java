package dev.mobarena.core;

import dev.mobarena.MobArena;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.server.level.ServerPlayer;
import java.util.List;

/** Server thread only. Respawn through the connection to replace its player reference. */
public final class DeadPlayers {
    private DeadPlayers() {}

    /** Does nothing before the player is registered in the PlayerList. */
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

    /** JOIN runs before PlayerList registration. Reset the fresh entity before join routing. */
    public static void prepareJoin(ServerPlayer player) {
        if (!player.isDeadOrDying()) return;
        player.setHealth(player.getMaxHealth());
        player.deathTime = 0;
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(5);
        player.clearFire();
        player.removeAllEffects();
        player.resetFallDistance();
        player.setAirSupply(player.getMaxAirSupply());
        player.setExperiencePoints(0);
        player.setExperienceLevels(0);
        player.totalExperience = 0;
    }

    public static void reviveTeamPlayers(GameContext ctx) {
        for (var player : List.copyOf(ctx.server().getPlayerList().getPlayers())) {
            if (ctx.team(player.getUUID()) != null && player.isDeadOrDying()) revive(player);
        }
    }
}
