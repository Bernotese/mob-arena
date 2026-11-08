package dev.mobarena.gametest;

import dev.mobarena.arena.BattleFlow;
import dev.mobarena.core.*;
import dev.mobarena.debug.DebugDriver;
import dev.mobarena.team.TeamId;
import dev.mobarena.world.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.*;

/** Real connections must replace dead entities before phase teleports and arena checks. */
public class DeathScreenGameTest {
    @GameTest(environment = "mobarena:death_prep_arena", maxTicks = 600)
    public void prepDeathThenArenaStartIsNotEliminated(GameTestHelper h) {
        var f = new FullGameFixture(h, true, 1);
        for (var team : TeamId.values()) DebugDriver.poolAdd(team, "cow", 3);
        endDeath(f);
        f.waveConfig();
        lobby(f);
        h.assertTrue(f.live(f.red) != f.red, "Respawn replaces the player");
        f.arena();
        h.startSequence().thenWaitUntil(f::arenaReady).thenIdle(40).thenExecute(f.check(() -> {
            h.assertTrue(f.battle(TeamId.RED).running(), "RED remains running");
            h.assertTrue(f.battle(TeamId.RED).eliminationReason().isEmpty(), "RED has no elimination reason");
            h.assertTrue(DebugDriver.state(f.server).getAsJsonObject("battles").getAsJsonObject("RED")
                    .get("reason").isJsonNull(), "Debug reason stays null");
        })).thenExecute(f::close).thenSucceed();
    }

    @GameTest(environment = "mobarena:death_sd", maxTicks = 600)
    public void deathScreenAtSuddenDeathStart(GameTestHelper h) {
        var f = new FullGameFixture(h, true, 1);
        f.arena();
        h.startSequence().thenWaitUntil(f::arenaReady).thenExecute(f.check(() -> {
            f.deathScreen(f.live(f.red));
            f.game.enterSuddenDeath();
        })).thenWaitUntil(() -> {
            h.assertTrue(f.game.phase() == Phase.SUDDEN_DEATH, "Sudden death starts");
            h.assertTrue(f.battle(TeamId.RED).running() && f.battle(TeamId.BLUE).running(), "Sudden death arenas ready");
        }).thenIdle(40).thenExecute(f.check(() -> {
            survival(f);
            h.assertTrue(f.battle(TeamId.RED).state() != BattleFlow.State.ELIMINATED, "RED survives sudden death entry");
        })).thenExecute(f::close).thenSucceed();
    }

    @GameTest(environment = "mobarena:death_end", maxTicks = 600)
    public void deathScreenAtGameEndReturnsToLobby(GameTestHelper h) {
        var f = new FullGameFixture(h, true, 1);
        f.arena();
        h.startSequence().thenWaitUntil(f::arenaReady).thenExecute(f.check(() -> {
            f.deathScreen(f.live(f.red));
            f.game.finish();
            h.assertTrue(f.game.phase() == Phase.END, "END starts");
            h.assertTrue(f.live(f.red).isAlive() && f.live(f.red).level() == f.ctx.arenaLevel(TeamId.RED),
                    "END respawn uses the own arena");
            f.game.endPhaseNow();
            lobby(f);
            for (var p : f.server.getPlayerList().getPlayers()) {
                h.assertTrue(!p.level().dimension().identifier().getNamespace().equals("mobarena"), "No player remains in game levels");
                h.assertTrue(f.server.getLevel(p.level().dimension()) == p.level(), "Player level remains loaded");
            }
        })).thenExecute(f::close).thenSucceed();
    }

    @GameTest(environment = "mobarena:death_arena_tick", maxTicks = 600)
    public void vanillaDeathInArenaRevivesIntoTimer(GameTestHelper h) {
        var f = new FullGameFixture(h, true);
        f.arena();
        h.startSequence().thenWaitUntil(f::arenaReady).thenExecute(f.check(() -> f.deathScreen(f.red)))
                .thenIdle(2).thenExecute(f.check(() -> timer(f))).thenIdle(61)
                .thenExecute(f.check(() -> survival(f))).thenExecute(f::close).thenSucceed();
    }

    @GameTest(environment = "mobarena:death_rejoin_prep", maxTicks = 500)
    public void deadDisconnectInPrepRejoinsInPrep(GameTestHelper h) {
        var f = new FullGameFixture(h, true, 1);
        endDeath(f); disconnect(f);
        rejoin(f);
        var p = f.live(f.red);
        h.assertTrue(p.isAlive() && p.gameMode() == GameType.SURVIVAL, "Dead joiner returns alive in survival");
        h.assertTrue(p.level() == f.ctx.prepLevel(TeamId.RED), "Join uses team overworld");
        h.assertTrue(p.position().distanceTo(Vec3.atBottomCenterOf(f.ctx.get(TeamWorlds.KEY)
                .get(TeamId.RED).prepSpawn())) < 2, "Join uses prep spawn");
        h.assertTrue(p.level() != GameLevels.level(TeamId.RED, WorldKind.PREP_END), "Join avoids team End");
        f.close(); h.succeed();
    }

    @GameTest(environment = "mobarena:death_rejoin_arena", maxTicks = 600)
    public void deadDisconnectRejoinsInArena(GameTestHelper h) {
        var f = new FullGameFixture(h, true);
        endDeath(f); disconnect(f); f.arena();
        h.startSequence().thenWaitUntil(f::arenaReady).thenExecute(f.check(() -> {
            rejoin(f); timer(f);
        })).thenIdle(61).thenExecute(f.check(() -> {
            survival(f);
            h.assertTrue(f.battle(TeamId.RED).state() != BattleFlow.State.ELIMINATED, "RED remains active");
        })).thenExecute(f::close).thenSucceed();
    }

    @GameTest(environment = "mobarena:death_rejoin_lobby", maxTicks = 500)
    public void deadOfflineAtGameEndRejoinsInLobby(GameTestHelper h) {
        var f = new FullGameFixture(h, true);
        endDeath(f); disconnect(f);
        f.game.stop();
        var joined = TestPlayers.create(h, f.red.getUUID(), p -> {
            p.setHealth(0);
            p.getInventory().setItem(1, new ItemStack(Items.DIAMOND));
        });
        f.players.add(joined);
        lobby(f);
        h.assertTrue(f.live(f.red).getInventory().isEmpty(), "Pending cleanup clears inventory");
        f.close(); h.succeed();
    }

    @GameTest(environment = "mobarena:death_revive_noop", maxTicks = 500)
    public void reviveIsNoOpForLivingPlayers(GameTestHelper h) {
        var f = new FullGameFixture(h, true, 1);
        h.assertTrue(DeadPlayers.revive(f.red) == f.red, "Living player stays unchanged");
        FullGameFixture.acceptLoad(f.red);
        var source = f.red.damageSources().fellOutOfWorld();
        f.red.hurtServer(f.red.level(), source, Float.MAX_VALUE);
        h.assertTrue(f.red.isDeadOrDying() && !f.red.isRemoved(), "Dead player remains before tickDeath removal");
        var revived = DeadPlayers.revive(f.red);
        h.assertTrue(revived != f.red && revived.isAlive(), "Dead player gets a new entity");
        h.assertTrue(f.red.connection.player == revived && f.red.isRemoved(), "Connection uses the new entity");
        f.close(); h.succeed();
    }

    private static void endDeath(FullGameFixture f) {
        var end = GameLevels.level(TeamId.RED, WorldKind.PREP_END);
        f.red.teleportTo(end, 0, 80, 0, java.util.Set.of(), 0, 0, true);
        f.deathScreen(f.red);
    }
    private static void disconnect(FullGameFixture f) {
        ServerPlayConnectionEvents.DISCONNECT.invoker().onPlayDisconnect(f.red.connection, f.server);
        f.server.getPlayerList().remove(f.red);
    }
    private static void rejoin(FullGameFixture f) {
        f.players.add(TestPlayers.create(f.h, f.red.getUUID(), p -> p.setHealth(0)));
    }
    private static void lobby(FullGameFixture f) {
        var p = f.live(f.red);
        f.h.assertTrue(p.isAlive() && !p.isRemoved() && p.gameMode() == GameType.ADVENTURE, "Lobby player is alive in adventure");
        f.h.assertTrue(p.level() == f.server.overworld(), "Player reaches lobby overworld");
        var json = com.google.gson.JsonParser.parseString(readLobby()).getAsJsonObject().getAsJsonObject("spawn");
        var spawn = new Vec3(json.get("x").getAsDouble(), json.get("y").getAsDouble(), json.get("z").getAsDouble());
        f.h.assertTrue(p.position().distanceTo(spawn) < 2, "Player reaches lobby platform");
    }
    private static String readLobby() {
        try { return java.nio.file.Files.readString(net.fabricmc.loader.api.FabricLoader.getInstance()
                .getConfigDir().resolve("mob-arena-lobby.json")); }
        catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }
    private static void timer(FullGameFixture f) {
        var p = f.live(f.red);
        f.h.assertTrue(p.isAlive() && p.isSpectator() && p.level() == f.ctx.arenaLevel(TeamId.RED), "Revived player enters arena timer");
        f.h.assertTrue(f.battle(TeamId.RED).running(), "Teammate keeps RED running");
    }
    private static void survival(FullGameFixture f) {
        var p = f.live(f.red);
        f.h.assertTrue(p.isAlive() && p.gameMode() == GameType.SURVIVAL && p.level() == f.ctx.arenaLevel(TeamId.RED),
                "Revived player reaches survival in own arena");
        var spawn = Vec3.atBottomCenterOf(f.ctx.get(TeamWorlds.KEY).get(TeamId.RED).layout().teamSpawn());
        f.h.assertTrue(p.position().distanceTo(spawn) < 2, "Player reaches team spawn");
    }
}
