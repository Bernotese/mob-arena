package dev.mobarena.gametest;

import dev.mobarena.arena.*;
import dev.mobarena.debug.DebugDriver;
import dev.mobarena.hud.*;
import dev.mobarena.team.TeamId;
import dev.mobarena.world.TeamWorlds;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Real arena preparation, packet ordering and phase integration. */
public class ArenaPrepGameTest {
    private record Sent(long tick, Packet<?> packet) {}
    private static void flatten(long tick, Packet<?> packet, List<Sent> result) {
        if (packet instanceof ClientboundBundlePacket bundle)
            for (var child : bundle.subPackets()) flatten(tick, child, result);
        else result.add(new Sent(tick, packet));
    }
    private static boolean cue(Packet<?> packet, GameSound sound) {
        return packet instanceof ClientboundSoundPacket p && p.getSource() == SoundSource.MASTER
                && p.getSound().value() == sound.sound().value()
                && p.getPitch() == sound.pitch() && p.getVolume() == sound.volume();
    }
    private static void preparing(FullGameFixture f) {
        for (var team : TeamId.values())
            f.h.assertTrue(f.battle(team).state() == BattleFlow.State.PREP, "Both battles enter PREP");
    }
    private static void spawn(FullGameFixture f, ServerPlayer player) {
        var p = f.live(player);
        var team = f.ctx.team(p.getUUID());
        var spawn = Vec3.atBottomCenterOf(f.ctx.get(TeamWorlds.KEY).get(team).layout().teamSpawn());
        f.h.assertTrue(p.gameMode() == GameType.SURVIVAL && p.level() == f.ctx.arenaLevel(team)
                && p.position().distanceTo(spawn) < 1.5, "Player stands at own spawn in survival");
    }

    @GameTest(environment = "mobarena:arena_prep", maxTicks = 500)
    public void preparationDelaysCountdownUntilAfterArenaTeleport(GameTestHelper h) {
        var f = new FullGameFixture(h, true, 1, 2);
        var sent = new ArrayList<Sent>();
        int[] cursor = {0};
        Runnable capture = () -> {
            var packets = f.packets.get(f.red.getUUID());
            while (cursor[0] < packets.size())
                flatten(f.server.getTickCount(), packets.get(cursor[0]++), sent);
        };
        h.onEachTick(capture);
        f.arena();
        h.startSequence().thenWaitUntil(() -> preparing(f)).thenExecute(f.check(() -> {
            capture.run();
            spawn(f, f.red); spawn(f, f.blue);
            for (var team : TeamId.values()) {
                h.assertTrue(f.battle(team).waveMobs().isEmpty(), "Preparation has no wave mobs");
                h.assertTrue(HudOverrides.of(f.ctx).get(team).text().getString().startsWith("Get ready"),
                        "Preparation boss bar says Get ready");
            }
            h.assertTrue(sent.stream().noneMatch(p -> cue(p.packet(), GameSound.COUNTDOWN)), "Prep has no countdown sound");
            h.assertTrue(sent.stream().noneMatch(p -> p.packet() instanceof ClientboundSetTitleTextPacket title
                    && title.text().getString().equals("3")), "Prep has no countdown title");
        })).thenWaitUntil(() -> h.assertTrue(f.battle(TeamId.RED).waveMobs().size() == 1, "First wave spawns"))
            .thenExecute(f.check(() -> {
                capture.run();
                var cues = sent.stream().filter(p -> cue(p.packet(), GameSound.COUNTDOWN) || cue(p.packet(), GameSound.GO)).toList();
                h.assertTrue(cues.size() == 4, "Three countdown cues and one Go cue");
                for (int i = 0; i < 3; i++) h.assertTrue(cue(cues.get(i).packet(), GameSound.COUNTDOWN), "Countdown order stays unchanged");
                h.assertTrue(cue(cues.get(3).packet(), GameSound.GO), "Go follows countdown");
                long teleport = sent.stream().filter(p -> p.packet() instanceof ClientboundPlayerPositionPacket
                        || p.packet() instanceof ClientboundRespawnPacket).mapToLong(Sent::tick).max().orElseThrow();
                h.assertTrue(cues.getFirst().tick() - teleport >= 40, "First countdown sound follows teleport by 40 ticks");
                h.assertTrue(sent.stream().anyMatch(p -> p.tick() >= cues.getFirst().tick()
                        && p.packet() instanceof ClientboundSetTitleTextPacket title && title.text().getString().equals("3")),
                        "First countdown title appears after preparation");
            })).thenExecute(f::close).thenSucceed();
    }

    @GameTest(environment = "mobarena:arena_prep_death", maxTicks = 400)
    public void preparationEndRevivesPlayersBeforeAllDownCheck(GameTestHelper h) {
        var f = new FullGameFixture(h, true, 1, 2);
        f.arena();
        h.startSequence().thenWaitUntil(() -> preparing(f)).thenExecute(f.check(() -> {
            f.down(f.red);
            h.assertTrue(f.red.isSpectator(), "Prep death enters the respawn timer");
        })).thenIdle(20).thenExecute(f.check(() -> {
            h.assertTrue(f.battle(TeamId.RED).state() == BattleFlow.State.PREP, "No elimination during prep");
            h.assertTrue(f.red.isSpectator(), "Three-second timer exceeds the remaining prep time");
        })).thenWaitUntil(() -> h.assertTrue(f.battle(TeamId.RED).state() == BattleFlow.State.COUNTDOWN, "Prep ends in countdown"))
            .thenExecute(f.check(() -> { spawn(f, f.red); ArenaPrepChecks.assertNotDown(h, f.ctx, f.red.getUUID()); })).thenIdle(2).thenExecute(f.check(() -> {
                h.assertTrue(f.battle(TeamId.RED).state() == BattleFlow.State.COUNTDOWN
                        && f.battle(TeamId.RED).eliminationReason().isEmpty(), "Timer is cancelled before all-down checks");
            })).thenExecute(f::close).thenSucceed();
    }

    @GameTest(environment = "mobarena:arena_prep_wave_time", maxTicks = 400)
    public void preparationDoesNotUseWaveTime(GameTestHelper h) {
        var f = new FullGameFixture(h, true, 1, 2);
        long[] spawned = {0};
        f.arena();
        h.startSequence().thenWaitUntil(() -> h.assertTrue(f.battle(TeamId.RED).state() == BattleFlow.State.FIGHT, "First fight starts"))
            .thenExecute(f.check(() -> spawned[0] = f.server.getTickCount() - 1))
            .thenIdle(25).thenExecute(f.check(() -> {
                var line = HudOverrides.of(f.ctx).get(TeamId.RED);
                long max = f.ctx.config().waveMaxTimeSeconds() * 20L;
                long elapsed = f.server.getTickCount() - spawned[0];
                // HUD refreshes each 20 ticks. Its value can be at most 20 ticks old.
                float latest = (float) (max - elapsed) / max;
                float oldest = (float) (max - elapsed + 20) / max;
                h.assertTrue(line.progress() >= latest && line.progress() <= oldest,
                        "Wave timer counts from spawn and excludes preparation");
                h.assertTrue(line.text().getString().contains("mobs left"), "Check the fight HUD, not the countdown HUD");
            })).thenExecute(f::close).thenSucceed();
    }

    @GameTest(environment = "mobarena:arena_prep_debug", maxTicks = 400)
    public void debugTimerEndsOnlyArenaPreparation(GameTestHelper h) {
        var f = new FullGameFixture(h, true, 1, 2);
        long[] changed = {0};
        f.arena();
        h.startSequence().thenWaitUntil(() -> preparing(f)).thenExecute(f.check(() -> {
            var state = DebugDriver.state(f.server).getAsJsonObject("battles").getAsJsonObject("RED");
            h.assertTrue(state.get("state").getAsString().equals("PREP") && state.get("prepLeft").getAsInt() > 0,
                    "Debug state reports PREP and ticks left");
            changed[0] = f.server.getTickCount();
            f.admin("ma debug timer 0");
        })).thenWaitUntil(() -> h.assertTrue(f.battle(TeamId.RED).state() == BattleFlow.State.COUNTDOWN
                && f.battle(TeamId.BLUE).state() == BattleFlow.State.COUNTDOWN, "Debug timer starts countdown"))
            .thenExecute(f.check(() -> {
                h.assertTrue(f.server.getTickCount() - changed[0] <= 1, "Countdown starts on the next tick");
                h.assertTrue(DebugDriver.state(f.server).getAsJsonObject("battles").getAsJsonObject("RED")
                        .get("prepLeft").getAsInt() == 0, "Debug prep time clears");
            })).thenExecute(f::close).thenSucceed();
    }

    @GameTest(environment = "mobarena:arena_prep_rejoin", maxTicks = 400)
    public void rejoinDuringPreparationUsesSpawnWithoutTimer(GameTestHelper h) {
        var f = new FullGameFixture(h, true, 2, 2);
        Vec3[] away = {null};
        f.arena();
        h.startSequence().thenWaitUntil(() -> preparing(f)).thenExecute(f.check(() -> {
            f.down(f.red);
            h.assertTrue(f.red.isSpectator(), "Player enters the prep respawn timer before leaving");
            ServerPlayConnectionEvents.DISCONNECT.invoker().onPlayDisconnect(f.red.connection, f.server);
            f.server.getPlayerList().remove(f.red);
            f.players.add(TestPlayers.create(h, f.red.getUUID(), player -> {}));
            spawn(f, f.red);
            ArenaPrepChecks.assertNotDown(h, f.ctx, f.red.getUUID());
            int count = 0;
            for (var level : f.server.getAllLevels())
                count += level.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(ServerPlayer.class),
                        player -> player.getUUID().equals(f.red.getUUID())).size();
            h.assertTrue(count == 1, "Rejoined UUID has exactly one player entity");
            h.assertTrue(f.server.getPlayerList().getPlayers().stream().filter(p -> p.getUUID().equals(f.red.getUUID())).count() == 1,
                    "PlayerList contains one rejoined UUID");
            var player = f.live(f.red);
            away[0] = player.position().add(3, 0, 0);
            player.teleportTo(player.level(), away[0].x, away[0].y, away[0].z, Set.of(), 0, 0, true);
        })).thenIdle(65).thenExecute(f.check(() -> {
            ArenaPrepChecks.assertNotDown(h, f.ctx, f.red.getUUID());
            h.assertTrue(f.live(f.red).position().distanceTo(away[0]) < 0.1,
                    "The old timer does not move the rejoined player");
        })).thenExecute(f::close).thenSucceed();
    }

    @GameTest(environment = "mobarena:arena_prep_rejoin_space", maxTicks = 400)
    public void rejoinDuringPreparationAvoidsPlayersHolesAndObstructions(GameTestHelper h) {
        var f = new FullGameFixture(h, true, 2, 5);
        f.arena();
        h.startSequence().thenWaitUntil(() -> preparing(f)).thenExecute(f.check(() -> {
            var level = f.ctx.arenaLevel(TeamId.RED);
            var spawnBlock = f.ctx.get(TeamWorlds.KEY).get(TeamId.RED).layout().teamSpawn();
            var center = Vec3.atBottomCenterOf(spawnBlock);
            f.redMate.teleportTo(level, center.x, center.y, center.z, Set.of(), 0, 0, true);
            // The first candidate has no floor; the next has no standing space.
            level.setBlockAndUpdate(spawnBlock.offset(-1, -1, -1), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(spawnBlock.offset(-1, 0, 0), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            ServerPlayConnectionEvents.DISCONNECT.invoker().onPlayDisconnect(f.red.connection, f.server);
            f.server.getPlayerList().remove(f.red);
            var joined = TestPlayers.create(h, f.red.getUUID(), player -> {});
            f.players.add(joined);
            RejoinGameTest.assertSingle(f, level);
            h.assertTrue(joined.gameMode() == GameType.SURVIVAL, "Rejoin during preparation returns in survival");
            h.assertTrue(Math.abs(joined.getX() - center.x) <= 3 && Math.abs(joined.getZ() - center.z) <= 3
                    && Math.abs(joined.getY() - center.y) < 0.001, "Rejoin stays near the team spawn at floor height");
            h.assertTrue(level.players().stream().filter(other -> other != joined).noneMatch(other ->
                    other.position().distanceToSqr(joined.position()) < 1.0
                            || other.getBoundingBox().intersects(joined.getBoundingBox())), "Rejoin does not overlap another player");
            h.assertTrue(joined.verticalCollisionBelow && level.noCollision(joined, joined.getBoundingBox()),
                    "Rejoin selects a supported position with standing space");
            h.assertTrue(!joined.blockPosition().equals(spawnBlock.offset(-1, 0, -1))
                    && !joined.blockPosition().equals(spawnBlock.offset(-1, 0, 0)), "Rejoin skips the hole and the obstruction");
            ArenaPrepChecks.assertNotDown(h, f.ctx, joined.getUUID());
        })).thenExecute(f::close).thenSucceed();
    }

    @GameTest(environment = "mobarena:arena_prep_sd", maxTicks = 400)
    public void suddenDeathStartsWithoutArenaPreparation(GameTestHelper h) {
        var f = new FullGameFixture(h, true, 1, 2);
        f.arena();
        h.startSequence().thenWaitUntil(() -> preparing(f)).thenExecute(f.check(() -> {
            f.game.enterSuddenDeath();
            for (var team : TeamId.values()) h.assertTrue(f.battle(team).state() == BattleFlow.State.WAITING_ARENA
                    || f.battle(team).state() == BattleFlow.State.COUNTDOWN, "Sudden death has no preparation");
        })).thenWaitUntil(() -> h.assertTrue(f.battle(TeamId.RED).state() == BattleFlow.State.COUNTDOWN
                && f.battle(TeamId.BLUE).state() == BattleFlow.State.COUNTDOWN, "Sudden death countdown starts immediately"))
            .thenExecute(f.check(() -> {
                for (var team : TeamId.values()) h.assertTrue(f.battle(team).prepLeftTicks() == 0, "Sudden death prep stays zero");
            })).thenExecute(f::close).thenSucceed();
    }
}
