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
        return create(helper, UUID.randomUUID(), beforeJoin, null, null);
    }
    /** Test player that also records its chat (system) messages into {@code messages}. */
    public static ServerPlayer recording(GameTestHelper helper, java.util.List<String> messages) {
        return create(helper, UUID.randomUUID(), player -> {}, java.util.Objects.requireNonNull(messages), null);
    }
    public static ServerPlayer create(GameTestHelper helper, UUID id, java.util.function.Consumer<ServerPlayer> beforeJoin) {
        return create(helper, id, beforeJoin, null, null);
    }
    public static ServerPlayer recordingPackets(GameTestHelper helper, java.util.List<Object> packets) {
        return create(helper, UUID.randomUUID(), player -> {}, null, packets);
    }
    private static ServerPlayer create(GameTestHelper helper, UUID id, java.util.function.Consumer<ServerPlayer> beforeJoin,
                                       java.util.List<String> messages, java.util.List<Object> packets) {
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
        var channel = new EmbeddedChannel(connection);
        if (packets != null) channel.pipeline().addLast(new io.netty.channel.ChannelOutboundHandlerAdapter() {
            @Override public void write(io.netty.channel.ChannelHandlerContext context, Object packet,
                                        io.netty.channel.ChannelPromise promise) throws Exception {
                packets.add(packet);
                super.write(context, packet, promise);
            }
        });
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        return player;
    }
}
