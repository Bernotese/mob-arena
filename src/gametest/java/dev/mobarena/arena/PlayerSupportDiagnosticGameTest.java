package dev.mobarena.arena;

import dev.mobarena.core.Teleports;
import dev.mobarena.gametest.TestEnd;
import dev.mobarena.team.TeamId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/** Exercise the vanilla movement path, not the private diagnostic hook. */
public class PlayerSupportDiagnosticGameTest {
    private static final String PREFIX = "MOBARENA_STANDING_ON_AIR ";

    private static void set(ServerGamePacketListenerImpl handler, String name, Object value) {
        try {
            var field = ServerGamePacketListenerImpl.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(handler, value);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }
    private static int tick(ServerGamePacketListenerImpl handler) {
        try {
            var field = ServerGamePacketListenerImpl.class.getDeclaredField("tickCount");
            field.setAccessible(true);
            return field.getInt(handler);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }
    private static void move(ServerPlayer player) {
        ArenaGameTest.damageable(player);
        set(player.connection, "awaitingPositionFromClient", null);
        set(player.connection, "clientLoadedTimeoutTimer", 0);
        set(player.connection, "receivedPositionThisTick", false);
        player.connection.resetPosition();
        player.verticalCollisionBelow = false;
        player.connection.handleMovePlayer(new ServerboundMovePlayerPacket.Pos(player.position(), true, false));
    }
    private static List<String> capture(GameTestHelper h, ServerPlayer player) {
        List<String> messages = new ArrayList<>();
        var logger = (org.apache.logging.log4j.core.Logger) org.apache.logging.log4j.LogManager.getRootLogger();
        var appender = new org.apache.logging.log4j.core.appender.AbstractAppender(
                "support-test-" + UUID.randomUUID(), null, null, true, org.apache.logging.log4j.core.config.Property.EMPTY_ARRAY) {
            @Override public void append(org.apache.logging.log4j.core.LogEvent event) {
                String message = event.getMessage().getFormattedMessage();
                if (message.contains(player.getPlainTextName())
                        && (message.startsWith(PREFIX) || message.contains("standing on air - force-sending blocks below")))
                    messages.add(message);
            }
        };
        appender.start(); logger.addAppender(appender);
        TestEnd.run(h, () -> { logger.removeAppender(appender); appender.stop(); });
        return messages;
    }
    private static long diagnostics(List<String> messages) { return messages.stream().filter(m -> m.startsWith(PREFIX)).count(); }
    private static long vanilla(List<String> messages) { return messages.stream().filter(m -> m.contains("standing on air - force-sending blocks below")).count(); }

    @GameTest(environment = "mobarena:player_support_diagnostic", maxTicks = 400)
    public void supportDiagnosticRespectsDebugFlagAndVanillaGate(GameTestHelper h) {
        var f = new ArenaFixture(h, true, true);
        var original = System.getProperty("mobarena.debug");
        TestEnd.run(h, () -> {
            if (original == null) System.clearProperty("mobarena.debug");
            else System.setProperty("mobarena.debug", original);
        });
        var messages = capture(h, f.red);
        h.startSequence()
            .thenWaitUntil(() -> h.assertTrue(f.group().battle(TeamId.RED).running(), "Player is in the arena"))
            .thenExecute(() -> {
                try {
                    Teleports.clearSupport(f.red);
                    h.assertTrue(Teleports.ticksSinceLastTeleport(f.red) == 0, "Mod teleport tick is recorded");
                    System.setProperty("mobarena.debug", "true");
                    set(f.red.connection, "lastSupportedBlocksSend", -1000);
                    move(f.red);
                    h.assertTrue(diagnostics(messages) == 1 && vanilla(messages) == 1, "One diagnostic accompanies one vanilla resend");
                    var line = messages.stream().filter(m -> m.startsWith(PREFIX)).findFirst().orElseThrow();
                    for (var field : List.of("player=" + f.red.getPlainTextName(), "phase=ARENA",
                            "level=" + f.red.level().dimension().identifier(), "position=", "gameMode=", "onGround=",
                            "verticalCollisionBelow=false", "deltaMovement=", "belowState=", "belowCollisionEmpty=false",
                            "mainSupportingBlockPos=", "cornerBlocks=[", "collisionEmpty=false",
                            "battleState=" + f.group().battle(TeamId.RED).state().name(), "down=false",
                            "vehicle=null", "effects=[]", "ticksSinceLastModTeleport=0", "hasClientLoaded=true"))
                        h.assertTrue(line.contains(field), "Diagnostic includes " + field);

                    messages.clear();
                    move(f.red);
                    h.assertTrue(messages.isEmpty(), "Vanilla gate prevents duplicate diagnostics and resends");
                    set(f.red.connection, "lastSupportedBlocksSend", tick(f.red.connection) - 199);
                    move(f.red);
                    h.assertTrue(messages.isEmpty(), "The 199-tick boundary stays silent");
                    set(f.red.connection, "lastSupportedBlocksSend", tick(f.red.connection) - 200);
                    move(f.red);
                    h.assertTrue(diagnostics(messages) == 1 && vanilla(messages) == 1, "The 200-tick boundary logs once");

                    messages.clear();
                    System.clearProperty("mobarena.debug");
                    set(f.red.connection, "lastSupportedBlocksSend", -1000);
                    move(f.red);
                    h.assertTrue(diagnostics(messages) == 0 && vanilla(messages) == 1, "Without debug only vanilla logs the resend");
                } finally {
                    if (original == null) System.clearProperty("mobarena.debug");
                    else System.setProperty("mobarena.debug", original);
                }
            }).thenExecute(f::close).thenSucceed();
    }
}
