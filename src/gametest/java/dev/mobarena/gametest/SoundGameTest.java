package dev.mobarena.gametest;

import dev.mobarena.arena.BattleFlow;
import dev.mobarena.core.Phase;
import dev.mobarena.debug.DebugDriver;
import dev.mobarena.hud.GameSound;
import dev.mobarena.team.TeamId;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import java.util.*;

/** Capture actual sound packets through the real game flow. */
public class SoundGameTest {
    private static void collect(Object packet, List<ClientboundSoundPacket> sounds) {
        if (packet instanceof ClientboundSoundPacket sound) {
            // Cow deaths and other world sounds use their own categories.
            if (sound.getSource() == SoundSource.MASTER) sounds.add(sound);
        }
        else if (packet instanceof ClientboundBundlePacket bundle)
            for (Packet<?> child : bundle.subPackets()) collect(child, sounds);
    }
    private static List<ClientboundSoundPacket> drain(FullGameFixture fixture, ServerPlayer player) {
        var packets = fixture.packets.get(player.getUUID());
        var sounds = new ArrayList<ClientboundSoundPacket>();
        for (var packet : packets) collect(packet, sounds);
        packets.clear();
        return sounds;
    }
    private static void flatten(Packet<?> packet, List<Packet<?>> packets) {
        if (packet instanceof ClientboundBundlePacket bundle)
            for (var child : bundle.subPackets()) flatten(child, packets);
        else packets.add(packet);
    }
    private static void afterTeleport(GameTestHelper h, List<Packet<?>> sent, GameSound cue) {
        var packets = new ArrayList<Packet<?>>();
        sent.forEach(packet -> flatten(packet, packets));
        int teleport = -1, sound = -1;
        for (int i = 0; i < packets.size(); i++) {
            var packet = packets.get(i);
            if (packet instanceof ClientboundRespawnPacket || packet instanceof ClientboundPlayerPositionPacket) teleport = i;
            if (sound < 0 && packet instanceof ClientboundSoundPacket p && matches(p, cue)) sound = i;
        }
        h.assertTrue(teleport >= 0 && sound > teleport, cue + " follows the player's last teleport packet");
    }
    private static boolean matches(ClientboundSoundPacket packet, GameSound sound) {
        return packet.getSound().value() == sound.sound().value()
                && packet.getPitch() == sound.pitch() && packet.getVolume() == sound.volume();
    }
    private static void exactly(GameTestHelper h, List<ClientboundSoundPacket> packets, GameSound... expected) {
        h.assertTrue(packets.size() == expected.length, "Expected " + Arrays.toString(expected) + ", got " + packets.size() + " sounds");
        for (int i = 0; i < expected.length; i++)
            h.assertTrue(matches(packets.get(i), expected[i]), "Sound " + i + " is " + expected[i]);
    }
    private static void nearReceiver(GameTestHelper h, ClientboundSoundPacket packet, ServerPlayer player) {
        h.assertTrue(packet.getSource() == SoundSource.MASTER, "Cue uses Master volume");
        h.assertTrue(Math.abs(packet.getX() - player.getX()) < .13
                && Math.abs(packet.getY() - player.getEyeY()) < .13
                && Math.abs(packet.getZ() - player.getZ()) < .13, "Cue plays at its receiver's position");
    }

    @GameTest(environment = "mobarena:sound_flow", maxTicks = 1500)
    public void countdownWavesAndResultSendPrivateSounds(GameTestHelper h) {
        var f = new FullGameFixture(h, true, 1);
        var lobby = f.player();
        var red = new ArrayList<ClientboundSoundPacket>();
        var blue = new ArrayList<ClientboundSoundPacket>();
        var redPackets = new ArrayList<Packet<?>>();
        var bluePackets = new ArrayList<Packet<?>>();
        f.arena();
        h.onEachTick(() -> {
            redPackets.addAll(f.packets.get(f.red.getUUID()));
            bluePackets.addAll(f.packets.get(f.blue.getUUID()));
            red.addAll(drain(f, f.red));
            blue.addAll(drain(f, f.blue));
        });
        var sequence = h.startSequence()
            .thenWaitUntil(f::arenaReady)
            .thenExecute(f.check(() -> {
                redPackets.addAll(f.packets.get(f.red.getUUID()));
                bluePackets.addAll(f.packets.get(f.blue.getUUID()));
                afterTeleport(h, redPackets, GameSound.COUNTDOWN);
                afterTeleport(h, bluePackets, GameSound.COUNTDOWN);
                // Both arenas can use the same template coordinates; separate receiver positions.
                f.blue.teleportTo(f.blue.level(), f.blue.getX() + 3, f.blue.getY(), f.blue.getZ(), Set.of(), 0, 0, true);
                FullGameFixture.acceptLoad(f.blue);
            }))
            .thenWaitUntil(() -> h.assertTrue(f.battle(TeamId.RED).waveMobs().size() == 1, "First cow spawns"))
            .thenExecute(f.check(() -> {
                // The test tick can precede the packet capture callback.
                red.addAll(drain(f, f.red)); blue.addAll(drain(f, f.blue));
                exactly(h, red, GameSound.COUNTDOWN, GameSound.COUNTDOWN, GameSound.COUNTDOWN, GameSound.GO);
                exactly(h, blue, GameSound.COUNTDOWN, GameSound.COUNTDOWN, GameSound.COUNTDOWN, GameSound.GO);
                nearReceiver(h, red.getLast(), f.red);
                nearReceiver(h, blue.getLast(), f.blue);
                h.assertTrue(Math.abs(red.getLast().getX() - blue.getLast().getX()) > 1
                        || Math.abs(red.getLast().getZ() - blue.getLast().getZ()) > 1,
                        "Teams receive cues at different arena positions");
                exactly(h, drain(f, lobby));
                bluePackets.clear();
                f.down(f.blue);
            }))
            .thenWaitUntil(() -> h.assertTrue(f.battle(TeamId.BLUE).state() == BattleFlow.State.ELIMINATED, "BLUE is eliminated"))
            .thenExecute(f.check(() -> {
                bluePackets.addAll(f.packets.get(f.blue.getUUID()));
                blue.addAll(drain(f, f.blue));
                afterTeleport(h, bluePackets, GameSound.ELIMINATED);
                nearReceiver(h, blue.stream().filter(p -> matches(p, GameSound.ELIMINATED)).findFirst().orElseThrow(), f.blue);
                h.assertTrue(blue.stream().filter(p -> matches(p, GameSound.ELIMINATED)).count() == 1, "One elimination cue");
            }));
        for (int wave = 1; wave <= 3; wave++) {
            final int current = wave;
            sequence.thenWaitUntil(() -> h.assertTrue(f.battle(TeamId.RED).state() == BattleFlow.State.FIGHT
                    && f.battle(TeamId.RED).currentTag().endsWith("." + current)
                    && f.battle(TeamId.RED).waveMobs().size() == 1, "Wave " + current + " spawns"))
                .thenExecute(f.check(() -> { red.clear(); drain(f, f.red); DebugDriver.clearwave(TeamId.RED); }));
            if (wave < 3) sequence
                .thenWaitUntil(() -> h.assertTrue(f.battle(TeamId.RED).state() == BattleFlow.State.COOLDOWN, "Wave clears"))
                .thenExecute(f.check(() -> {
                    red.addAll(drain(f, f.red));
                    exactly(h, red, GameSound.WAVE_CLEARED);
                }));
        }
        sequence.thenWaitUntil(() -> h.assertTrue(f.game.phase() == Phase.END, "Game ends"))
            .thenExecute(f.check(() -> {
                red.addAll(drain(f, f.red)); blue.addAll(drain(f, f.blue));
                exactly(h, red, GameSound.FINISHED);
                h.assertTrue(blue.stream().noneMatch(p -> matches(p, GameSound.LOSE)), "Result sound waits for the title");
                exactly(h, drain(f, lobby));
            }))
            .thenWaitUntil(() -> h.assertTrue(f.ctx.timer().elapsedTicks() >= 40, "Result title delay expires"))
            .thenExecute(f.check(() -> {
                red.addAll(drain(f, f.red)); blue.addAll(drain(f, f.blue));
                exactly(h, red, GameSound.FINISHED, GameSound.WIN);
                h.assertTrue(blue.stream().filter(p -> matches(p, GameSound.LOSE)).count() == 1, "Loser receives one defeat cue");
                exactly(h, drain(f, lobby), GameSound.DRAW);
            }))
            .thenExecute(f::close).thenSucceed();
    }

    @GameTest(environment = "mobarena:sound_stop", maxTicks = 400)
    public void adminStopHasNoResultSound(GameTestHelper h) {
        var f = new FullGameFixture(h, true, 1);
        var lobby = f.player();
        f.arena();
        h.startSequence().thenWaitUntil(f::arenaReady)
            .thenExecute(f.check(() -> {
                for (var player : f.players) drain(f, player);
                f.game.endPhaseNow();
                h.assertTrue(f.game.phase() == Phase.END, "Admin skip enters END without a verdict");
                for (var player : List.of(f.red, f.blue, lobby)) exactly(h, drain(f, player));
            }))
            .thenWaitUntil(() -> h.assertTrue(f.ctx.timer().elapsedTicks() >= 40, "Admin title delay expires"))
            .thenExecute(f.check(() -> {
                for (var player : List.of(f.red, f.blue, lobby)) exactly(h, drain(f, player));
                f.game.stop();
                for (var player : List.of(f.red, f.blue, lobby)) exactly(h, drain(f, player));
            })).thenExecute(f::close).thenSucceed();
    }
    @GameTest(environment = "mobarena:sound_sudden_death", maxTicks = 400)
    public void emptySuddenDeathWavesStillPlayGoAndFinish(GameTestHelper h) {
        var f = new FullGameFixture(h, true, 1);
        var lobby = f.player();
        f.arena();
        var red = new ArrayList<ClientboundSoundPacket>();
        var blue = new ArrayList<ClientboundSoundPacket>();
        boolean[] capture = {false};
        h.onEachTick(() -> {
            if (capture[0]) { red.addAll(drain(f, f.red)); blue.addAll(drain(f, f.blue)); }
        });
        h.startSequence().thenWaitUntil(f::arenaReady)
            .thenExecute(f.check(() -> f.down(f.blue)))
            .thenWaitUntil(() -> h.assertTrue(f.battle(TeamId.BLUE).state() == BattleFlow.State.ELIMINATED,
                    "BLUE watches the other arena before sudden death"))
            .thenExecute(f.check(() -> {
                var plans = new EnumMap<TeamId, dev.mobarena.wave.WavePlan>(TeamId.class);
                for (var team : TeamId.values()) plans.put(team, new dev.mobarena.wave.WavePlan(team,
                        List.of(new TreeMap<>(), new TreeMap<>(), new TreeMap<>())));
                f.ctx.put(dev.mobarena.wave.WaveKeys.PLANS, plans);
                for (var player : f.players) drain(f, player);
                capture[0] = true;
                f.game.enterSuddenDeath();
                for (var player : List.of(f.red, f.blue)) {
                    afterTeleport(h, f.packets.get(player.getUUID()), GameSound.SUDDEN_DEATH);
                    var sounds = new ArrayList<ClientboundSoundPacket>();
                    f.packets.get(player.getUUID()).forEach(packet -> collect(packet, sounds));
                    nearReceiver(h, sounds.stream().filter(p -> matches(p, GameSound.SUDDEN_DEATH)).findFirst().orElseThrow(), player);
                }
            }))
            .thenWaitUntil(() -> h.assertTrue(f.game.phase() == Phase.END, "Empty sudden death ends in a draw"))
            .thenExecute(f.check(() -> exactly(h, drain(f, lobby))))
            .thenWaitUntil(() -> h.assertTrue(f.ctx.timer().elapsedTicks() >= 40, "Draw title delay expires"))
            .thenExecute(f.check(() -> {
                red.addAll(drain(f, f.red)); blue.addAll(drain(f, f.blue));
                for (var packets : List.of(red, blue)) exactly(h, packets, GameSound.SUDDEN_DEATH,
                        GameSound.COUNTDOWN, GameSound.COUNTDOWN, GameSound.COUNTDOWN,
                        GameSound.GO, GameSound.FINISHED, GameSound.DRAW);
                exactly(h, drain(f, lobby), GameSound.DRAW);
            })).thenExecute(f::close).thenSucceed();
    }

}
