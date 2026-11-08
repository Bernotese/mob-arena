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
    /** redMate and blueMate are null in a 1v1 fixture. */
    final ServerPlayer red, redMate, blue, blueMate;
    final ConfigStore original;
    GameContext ctx;

    FullGameFixture(GameTestHelper h, boolean start) { this(h, start, 2); }

    FullGameFixture(GameTestHelper h, boolean start, int perTeam) {
        if (perTeam < 1 || perTeam > 2) throw new IllegalArgumentException("1 or 2 players per team");
        this.h = h; server = h.getLevel().getServer();
        if (active != null) active.close();
        game.stop();
        try { original = new ConfigStore(FabricLoader.getInstance().getConfigDir().resolve("mob-arena.json")); }
        catch (IOException e) { throw new IllegalStateException(e); }
        TestEnd.run(h, this::close);
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
        red = player(); redMate = perTeam == 2 ? player() : null;
        blue = player(); blueMate = perTeam == 2 ? player() : null;
        var teams = new HashMap<UUID, TeamId>();
        for (var p : teamPlayers()) {
            TeamId team = p == red || p == redMate ? TeamId.RED : TeamId.BLUE;
            TeamManager.get().assign(p, team);
            teams.put(p.getUUID(), team);
        }
        active = this;
        if (start) {
            h.assertTrue(game.start(server, teams, teams.keySet()), "Full game starts with " + perTeam + " players per team");
            ctx = game.context();
            h.assertTrue(game.phase() == Phase.PREP && ctx != null, "Real PREP listeners complete");
        }
    }
    /** The assigned team players (no late joiners). */
    List<ServerPlayer> teamPlayers() {
        return java.util.stream.Stream.of(red, redMate, blue, blueMate).filter(Objects::nonNull).toList();
    }
    final Map<UUID, List<net.minecraft.network.protocol.Packet<?>>> packets = new HashMap<>();
    ServerPlayer player() {
        var sent = new ArrayList<net.minecraft.network.protocol.Packet<?>>();
        var p = TestPlayers.recording(h, null, sent);
        packets.put(p.getUUID(), sent);
        players.add(p);
        return p;
    }
    ServerPlayer live(ServerPlayer player) { return server.getPlayerList().getPlayer(player.getUUID()); }
    void deathScreen(ServerPlayer player) {
        acceptLoad(player);
        var source = player.damageSources().fellOutOfWorld();
        // Bypass ALLOW_DEATH in arenas; its cancelled death would create a respawn timer.
        if (game.phase() == Phase.ARENA || game.phase() == Phase.SUDDEN_DEATH) {
            player.setHealth(0); player.die(source);
        } else player.hurtServer(player.level(), source, Float.MAX_VALUE);
        h.assertTrue(player.isDeadOrDying(), "Player reaches the death screen");
        player.remove(net.minecraft.world.entity.Entity.RemovalReason.KILLED);
    }
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
        if (game.phase() == Phase.PREP) {
            for (var team : TeamId.values()) DebugDriver.poolAdd(team, "cow", 3);
            waveConfig();
        }
        for (var team : TeamId.values()) DebugDriver.waves(team, "cow:1", "cow:1", "cow:1");
        admin("ma skip");
    }
    ArenaBattle battle(TeamId team) { return ArenaBattles.get(ctx).battle(team); }
    void arenaReady() {
        h.assertTrue(game.phase() == Phase.ARENA, "Game remains in ARENA");
        for (var old : teamPlayers()) {
            var p = live(old);
            if (p == null) continue;
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
