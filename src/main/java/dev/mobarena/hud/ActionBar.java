package dev.mobarena.hud;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

public final class ActionBar {
    private ActionBar() {}
    public static void send(ServerPlayer player, Component text) { player.sendOverlayMessage(text); }
}
