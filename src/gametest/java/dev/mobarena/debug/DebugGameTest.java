package dev.mobarena.debug;

import dev.mobarena.core.GameManager;
import dev.mobarena.gametest.TestPlayers;
import dev.mobarena.team.*;
import dev.mobarena.wave.WaveKeys;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import java.util.*;

public class DebugGameTest {
    @GameTest(environment = "mobarena-gametest:debug_driver")
    public void poolWavesAndState(GameTestHelper h) throws Exception {
        var game = GameManager.get(); game.stop();
        var red = TestPlayers.create(h); var blue = TestPlayers.create(h);
        try {
            TeamManager.get().assign(red, TeamId.RED); TeamManager.get().assign(blue, TeamId.BLUE);
            h.assertTrue(game.start(h.getLevel().getServer(), Map.of(red.getUUID(), TeamId.RED, blue.getUUID(), TeamId.BLUE),
                Set.of(red.getUUID(), blue.getUUID())), "Game starts");
            DebugDriver.poolAdd(TeamId.RED, "zombie", 3); DebugDriver.poolAdd(TeamId.RED, "cow", 1);
            game.endPhaseNow();
            DebugDriver.waves(TeamId.RED, "zombie:3,cow:1", "-", "-");
            var dispatcher = h.getLevel().getServer().getCommands().getDispatcher();
            var source = h.getLevel().getServer().createCommandSourceStack();
            h.assertTrue(dispatcher.execute("ma debug waves red zombie:3,cow:1 - -", source) == 1, "Command accepts wave specs");
            h.assertTrue(!dispatcher.parse("ma debug state", red.createCommandSourceStack()).getReader().getRemaining().isEmpty(), "Players without level four cannot use debug");
            DebugDriver.timer(12);
            h.assertTrue(game.context().timer().remainingTicks() == 240, "Timer uses server ticks");
            String property = System.getProperty("mobarena.debug");
            try {
                System.setProperty("mobarena.debug", "false");
                try { DebugDriver.timer(1); throw new AssertionError("Expected disabled driver"); }
                catch (IllegalStateException expected) { }
                h.assertTrue(!dispatcher.parse("ma debug state", source).getReader().getRemaining().isEmpty(), "Disabled commands are unavailable");
            } finally { System.setProperty("mobarena.debug", property); }

            h.assertTrue(dispatcher.parse("ma debug kill " + red.getName().getString() + " minecraft:zombie 2", source).getReader().getRemaining().isEmpty(), "Kill accepts entity identifier");
            var state = DebugDriver.state(h.getLevel().getServer());
            h.assertTrue(state.get("phase").getAsString().equals("WAVE_CONFIG"), "State includes phase");
            h.assertTrue(state.getAsJsonObject("pools").get("RED").getAsInt() == 4, "State includes pool total");
            h.assertTrue(state.getAsJsonObject("plans").getAsJsonArray("RED").get(0).getAsJsonObject().get("minecraft:zombie").getAsInt() == 3, "State includes plan");
            h.assertTrue(state.get("outcomes").isJsonNull() && state.get("verdict").isJsonNull(), "Result placeholders are null");
            h.assertTrue(game.context().get(WaveKeys.SETUPS).get(TeamId.RED).waveTotal(1) == 4, "Driver sets setup");
            try { DebugDriver.waves(TeamId.RED, "zombie:4", "-", "-"); throw new AssertionError("Expected insufficient pool"); }
            catch (IllegalArgumentException expected) { }
            h.assertTrue(game.context().get(WaveKeys.SETUPS).get(TeamId.RED).waveTotal(1) == 4, "Invalid plan leaves setup unchanged");
        } finally {
            game.stop();
            for (var p : List.of(red, blue)) { TeamManager.get().remove(p); h.getLevel().getServer().getPlayerList().remove(p); }
        }
        h.succeed();
    }
}
