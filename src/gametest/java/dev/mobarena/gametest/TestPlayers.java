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
    public static ServerPlayer create(GameTestHelper helper) { return create(helper, player -> {}); }
    public static ServerPlayer create(GameTestHelper helper, java.util.function.Consumer<ServerPlayer> beforeJoin) {
        return create(helper, UUID.randomUUID(), beforeJoin, null);
    }
    /** Test player that also records its chat (system) messages into {@code messages}. */
    public static ServerPlayer recording(GameTestHelper helper, java.util.List<String> messages) {
        return create(helper, UUID.randomUUID(), player -> {}, java.util.Objects.requireNonNull(messages));
    }
    public static ServerPlayer create(GameTestHelper helper, UUID id, java.util.function.Consumer<ServerPlayer> beforeJoin) {
        return create(helper, id, beforeJoin, null);
    }
    private static ServerPlayer create(GameTestHelper helper, UUID id, java.util.function.Consumer<ServerPlayer> beforeJoin,
                                       java.util.List<String> messages) {
        GameProfile profile = new GameProfile(id, "test_" + id.toString().substring(0, 8));
        var cookie = CommonListenerCookie.createInitial(profile, false);
        var server = helper.getLevel().getServer();
        ServerPlayer player = messages == null ? new ServerPlayer(server, helper.getLevel(), profile, cookie.clientInformation())
                : new ServerPlayer(server, helper.getLevel(), profile, cookie.clientInformation()) {
                    @Override public void sendSystemMessage(net.minecraft.network.chat.Component message, boolean overlay) {
                        if (!overlay) messages.add(message.getString());
                        super.sendSystemMessage(message, overlay);
                    }
                };
        beforeJoin.accept(player);
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        return player;
    }
}
