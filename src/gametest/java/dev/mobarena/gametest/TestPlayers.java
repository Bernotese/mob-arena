package dev.mobarena.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import java.util.UUID;

/** Online test player with real game-mode behavior and an in-memory connection. */
public final class TestPlayers {
    private TestPlayers() {}
    public static ServerPlayer create(GameTestHelper helper) {
        UUID id = UUID.randomUUID();
        GameProfile profile = new GameProfile(id, "test_" + id.toString().substring(0, 8));
        var cookie = CommonListenerCookie.createInitial(profile, false);
        var server = helper.getLevel().getServer();
        ServerPlayer player = new ServerPlayer(server, helper.getLevel(), profile, cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        return player;
    }
}
