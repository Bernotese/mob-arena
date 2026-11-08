package dev.mobarena.hud;

import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

/** Private cues at each receiver's position; no nearby-player broadcast. */
public final class Sounds {
    private Sounds() {}
    public static void play(ServerPlayer player, GameSound sound) {
        player.connection.send(new ClientboundSoundPacket(sound.sound(), SoundSource.MASTER,
                player.getX(), player.getEyeY(), player.getZ(), sound.volume(), sound.pitch(), player.getRandom().nextLong()));
    }
    public static void playAll(Iterable<ServerPlayer> players, GameSound sound) {
        for (var player : players) play(player, sound);
    }
}
