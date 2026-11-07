package dev.mobarena.gametest;

import dev.mobarena.arena.*;
import dev.mobarena.backpack.BackpackModule;
import dev.mobarena.config.*;
import dev.mobarena.core.*;
import dev.mobarena.debug.DebugDriver;
import dev.mobarena.team.*;
import dev.mobarena.wave.WaveKeys;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import java.io.IOException;
import java.util.*;

/** Real module wiring. Every caller must use a separate test environment. */
final class FullGameFixture implements AutoCloseable {
    private static FullGameFixture active;
    final GameTestHelper h;
    final MinecraftServer server;
    final GameManager game = GameManager.get();
    final List<ServerPlayer> players = new ArrayList<>();
    final ServerPlayer red, redMate, blue, blueMate;
    final ConfigStore original;
    GameContext ctx;

    FullGameFixture(GameTestHelper h, boolean start) {
        this.h = h; server = h.getLevel().getServer();
        if (active != null) active.close();
        game.stop();
        try { original = new ConfigStore(FabricLoader.getInstance().getConfigDir().resolve("mob-arena.json")); }
        catch (IOException e) { throw new IllegalStateException(e); }
        h.runBeforeTestEnd(this::close);
        var config = new MobArenaConfig();
        config.prepTimeSeconds = 60; config.waveConfigTimeSeconds = 30;
        config.waveCountdownSeconds = 3; config.waveCooldownSeconds = 3;
        config.waveMaxTimeSeconds = 60;
        // T10 asks for 30; the current ConfigBounds minimum is 60.
        config.suddenDeathMaxTimeSeconds = 60;
        config.arenaRespawnSeconds = 3; config.mobGlowAfterSeconds = 10;
        config.endScreenSeconds = 5; config.maxAliveMobs = 20;
        game.setConfigSource(config::copy);
        BackpackModule.useGameAccess();
        red = player(); redMate = player(); blue = player(); blueMate = player();
        TeamManager.get().assign(red, TeamId.RED); TeamManager.get().assign(redMate, TeamId.RED);
        TeamManager.get().assign(blue, TeamId.BLUE); TeamManager.get().assign(blueMate, TeamId.BLUE);
        active = this;
        if (start) {
            var teams = Map.of(red.getUUID(), TeamId.RED, redMate.getUUID(), TeamId.RED,
                    blue.getUUID(), TeamId.BLUE, blueMate.getUUID(), TeamId.BLUE);
            h.assertTrue(game.start(server, teams, teams.keySet()), "Full game starts with two players per team");
            ctx = game.context();
            h.assertTrue(game.phase() == Phase.PREP && ctx != null, "Real PREP listeners complete");
        }
    }
    ServerPlayer player() { var p = TestPlayers.create(h); players.add(p); return p; }
    void command(String command, ServerPlayer player) {
        try { h.assertTrue(server.getCommands().getDispatcher().execute(command, player.createCommandSourceStack()) == 1,
                "Command succeeds: " + command); }
        catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) { throw new IllegalStateException(e); }
    }
    void admin(String command) {
        try { h.assertTrue(server.getCommands().getDispatcher().execute(command, server.createCommandSourceStack()) == 1,
                "Admin command succeeds: " + command); }
        catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) { throw new IllegalStateException(e); }
    }
    void waveConfig() {
        DebugDriver.timer(0);
        game.endPhaseNow();
        h.assertTrue(game.phase() == Phase.WAVE_CONFIG && ctx.get(WaveKeys.SETUPS) != null, "Real wave setup starts");
    }
    void arena() {
        for (var team : TeamId.values()) DebugDriver.poolAdd(team, "cow", 3);
        waveConfig();
        for (var team : TeamId.values()) DebugDriver.waves(team, "cow:1", "cow:1", "cow:1");
        admin("ma skip");
    }
    ArenaBattle battle(TeamId team) { return ArenaBattles.get(ctx).battle(team); }
    void arenaReady() {
        h.assertTrue(game.phase() == Phase.ARENA, "Game remains in ARENA");
        for (var p : List.of(red, redMate, blue, blueMate)) {
            var battle = battle(ctx.team(p.getUUID()));
            h.assertTrue(battle != null && battle.running() && p.level() == ctx.arenaLevel(ctx.team(p.getUUID())),
                    "Team player enters a running arena");
        }
    }
    static void acceptLoad(ServerPlayer p) {
        p.hasChangedDimension();
        p.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
    }
    void down(ServerPlayer p) { acceptLoad(p); DebugDriver.down(p); }
    void state(Phase expected) {
        var state = DebugDriver.state(server);
        h.assertTrue(state.get("phase").getAsString().equals(expected.name()), "State reports " + expected);
    }
    Runnable check(Runnable action) {
        return () -> {
            try { action.run(); }
            catch (RuntimeException | Error e) { close(); throw e; }
        };
    }
    @Override public void close() {
        if (active != this) return;
        active = null;
        if (ctx != null && game.context() == ctx) game.stop();
        for (var old : players) {
            var p = server.getPlayerList().getPlayer(old.getUUID());
            if (p != null) { p.closeContainer(); TeamManager.get().remove(p); server.getPlayerList().remove(p); p.discard(); }
        }
        game.setConfigSource(original::snapshot);
        BackpackModule.useGameAccess();
    }
}
