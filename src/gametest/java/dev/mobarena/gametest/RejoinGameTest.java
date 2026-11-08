package dev.mobarena.gametest;

import dev.mobarena.core.Phase;
import dev.mobarena.team.TeamId;
import dev.mobarena.world.GameLevels;
import dev.mobarena.world.WorldKind;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameType;
import java.util.*;

/** Join routing must run after vanilla registers the player in its original level. */
public class RejoinGameTest {
    static List<ServerLevel> registrations(MinecraftServer server, UUID id) {
        var levels = new ArrayList<ServerLevel>();
        for (var level : server.getAllLevels()) {
            if (level.getEntity(id) != null || level.players().stream().anyMatch(p -> p.getUUID().equals(id)))
                levels.add(level);
        }
        return levels;
    }

    static void assertSingle(FullGameFixture f, ServerLevel expected) {
        var live = f.live(f.red);
        var levels = registrations(f.server, f.red.getUUID());
        f.h.assertTrue(levels.equals(List.of(expected)), "Player is registered in exactly the expected level: "
                + levels.stream().map(level -> level.dimension().identifier().toString()).toList());
        f.h.assertTrue(live != null && expected.getEntity(f.red.getUUID()) == live && !live.isRemoved(),
                "The level and PlayerList contain the same live player entity");
    }

    @GameTest(environment = "mobarena:rejoin_arena_lobby", maxTicks = 1200)
    public void aliveDisconnectInPrepRejoinsInArenaThenLobby(GameTestHelper h) {
        arenaRejoin(h, false);
    }

    @GameTest(environment = "mobarena:rejoin_dead_arena_lobby", maxTicks = 1200)
    public void deadDisconnectInPrepRejoinsInArenaThenLobby(GameTestHelper h) {
        arenaRejoin(h, true);
    }

    private static void arenaRejoin(GameTestHelper h, boolean dead) {
        var f = new FullGameFixture(h, true);
        ServerLevel[] oldArena = {null};
        try {
            if (dead) {
                var end = GameLevels.level(TeamId.RED, WorldKind.PREP_END);
                f.red.teleportTo(end, 0.5, 80, 0.5, Set.of(), 0, 0, true);
                f.deathScreen(f.red);
            }
            disconnect(f);
            f.arena();
            h.startSequence().thenWaitUntil(f::arenaReady).thenExecute(f.check(() -> {
                var joined = TestPlayers.create(h, f.red.getUUID(), p -> { if (dead) p.setHealth(0); });
                f.players.add(joined);
                var arena = f.ctx.arenaLevel(TeamId.RED);
                assertSingle(f, arena);
                if (dead) {
                    h.assertTrue(joined.isAlive() && joined.isSpectator(), "Dead joiner enters the arena respawn timer alive");
                    dev.mobarena.arena.ArenaPrepChecks.assertDown(h, f.ctx, joined.getUUID());
                }
                f.game.enterSuddenDeath();
            })).thenWaitUntil(() -> {
                h.assertTrue(f.game.phase() == Phase.SUDDEN_DEATH, "Sudden death starts");
                h.assertTrue(f.battle(TeamId.RED).running() && f.battle(TeamId.BLUE).running(), "Sudden death arenas are ready");
                h.assertTrue(f.live(f.red).gameMode() == GameType.SURVIVAL, "Sudden death resets the rejoined player");
            }).thenExecute(f.check(() -> {
                var arena = f.ctx.arenaLevel(TeamId.RED);
                oldArena[0] = arena;
                assertSingle(f, arena);
                f.game.finish();
                h.assertTrue(f.game.phase() == Phase.END, "END starts");
                assertSingle(f, arena);
                f.game.endPhaseNow();
                h.assertTrue(f.game.phase() == Phase.LOBBY, "Game returns to LOBBY");
                assertSingle(f, f.server.overworld());
                h.assertTrue(f.live(f.red).gameMode() == GameType.ADVENTURE, "Rejoined player reaches the lobby in adventure mode");
            })).thenWaitUntil(() -> h.assertTrue(f.server.getLevel(oldArena[0].dimension()) == null,
                    "The old arena is unloaded")).thenExecute(f.check(() -> {
                assertSingle(f, f.server.overworld());
                h.assertTrue(registrations(f.server, f.red.getUUID()).stream().noneMatch(level ->
                        level.dimension().identifier().getNamespace().equals("mobarena")), "No game level retains the player UUID");
                h.assertTrue(oldArena[0].getEntity(f.red.getUUID()) == null && oldArena[0].players().stream().noneMatch(p ->
                        p.getUUID().equals(f.red.getUUID())), "The deleted arena retains no player registration");
            })).thenExecute(f::close).thenSucceed();
        } catch (RuntimeException | Error e) { f.close(); throw e; }
    }

    @GameTest(environment = "mobarena:rejoin_wave_config", maxTicks = 500)
    public void disconnectInTeamEndRejoinsInWaveConfig(GameTestHelper h) {
        var f = new FullGameFixture(h, true, 1);
        try {
            var end = GameLevels.level(TeamId.RED, WorldKind.PREP_END);
            f.red.teleportTo(end, 0.5, 80, 0.5, Set.of(), 0, 0, true);
            disconnect(f);
            f.waveConfig();
            var joined = TestPlayers.create(h, f.red.getUUID(), p -> {
                p.setServerLevel(end);
                p.setPos(0.5, 80, 0.5);
            });
            f.players.add(joined);
            assertSingle(f, f.server.overworld());
            h.assertTrue(joined.isAlive() && joined.gameMode() == GameType.ADVENTURE, "End joiner reaches the holding lobby alive");
            f.close();
            h.succeed();
        } catch (RuntimeException | Error e) { f.close(); throw e; }
    }

    private static void disconnect(FullGameFixture f) {
        ServerPlayConnectionEvents.DISCONNECT.invoker().onPlayDisconnect(f.red.connection, f.server);
        f.server.getPlayerList().remove(f.red);
    }
}
