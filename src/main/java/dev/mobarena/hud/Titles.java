package dev.mobarena.hud;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;

public final class Titles {
    private Titles() {}
    public static void show(ServerPlayer player, Component title, Component subtitle,
                            int fadeIn, int stay, int fadeOut) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut));
        player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle == null ? Component.empty() : subtitle));
        player.connection.send(new ClientboundSetTitleTextPacket(title));
    }
    public static void showAll(Iterable<ServerPlayer> players, Component title, Component subtitle,
                               int fadeIn, int stay, int fadeOut) {
        for (ServerPlayer player : players) show(player, title, subtitle, fadeIn, stay, fadeOut);
    }
}
