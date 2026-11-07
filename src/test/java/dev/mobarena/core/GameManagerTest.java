package dev.mobarena.core;
import dev.mobarena.config.MobArenaConfig;
import dev.mobarena.team.TeamId;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class GameManagerTest {
    private final UUID red = UUID.randomUUID(), blue = UUID.randomUUID();
    private final Map<UUID, TeamId> teams = Map.of(red, TeamId.RED, blue, TeamId.BLUE);
    private GameManager game() {
        GameManager game = new GameManager();
        game.setConfigSource(() -> { var c = new MobArenaConfig(); c.prepTimeSeconds = 60; c.waveConfigTimeSeconds = 10; c.endScreenSeconds = 5; return c; });
        return game;
    }
    @Test void ticksAdvanceTimedPhasesAndStopClearsState() {
        GameManager game = game();
        assertFalse(game.start(null, teams, Set.of(red)));
        assertTrue(game.start(null, teams, teams.keySet()));
        assertEquals(Phase.PREP, game.phase());
        for (int i = 0; i < 1199; i++) game.tick();
        assertEquals(Phase.PREP, game.phase()); game.tick();
        assertEquals(Phase.WAVE_CONFIG, game.phase());
        for (int i = 0; i < 200; i++) game.tick();
        assertEquals(Phase.ARENA, game.phase());
        for (int i = 0; i < 30; i++) game.tick();
        assertEquals(Phase.ARENA, game.phase());
        GameContext ctx = game.context(); Key<String> key = Key.of("test"); ctx.put(key, "value");
        game.stop(); assertEquals(Phase.LOBBY, game.phase()); assertNull(game.context()); assertNull(ctx.get(key));
    }
    @Test void nestedEnterAndExitTransitionsRunAfterNotification() {
        GameManager game = game(); List<String> events = new ArrayList<>();
        game.register(new PhaseListener() {
            public void onEnter(Phase p, GameContext ctx) { events.add("first:" + p); if (p == Phase.PREP) game.endPhaseNow(); }
            public void onExit(Phase p, GameContext ctx) { if (p == Phase.WAVE_CONFIG) game.stop(); }
        });
        game.register(new PhaseListener() { public void onEnter(Phase p, GameContext ctx) { events.add("second:" + p); } });
        game.start(null, teams, teams.keySet());
        assertEquals(List.of("first:PREP", "second:PREP", "first:WAVE_CONFIG", "second:WAVE_CONFIG"), events);
        game.endPhaseNow(); assertEquals(Phase.LOBBY, game.phase());
    }
    @Test void listenerExceptionsDoNotBlockOtherListenersOrCleanup() {
        GameManager game = game(); List<String> events = new ArrayList<>();
        game.register(new PhaseListener() {
            public void onEnter(Phase p, GameContext ctx) { throw new IllegalStateException("enter test"); }
            public void onExit(Phase p, GameContext ctx) { throw new IllegalStateException("exit test"); }
            public void onTick(Phase p, GameContext ctx) { throw new IllegalStateException("tick test"); }
        });
        game.register(new PhaseListener() {
            public void onEnter(Phase p, GameContext ctx) { events.add("enter"); assertNotNull(ctx.get(ResultHooks.KEY)); }
            public void onExit(Phase p, GameContext ctx) { events.add("exit"); }
            public void onTick(Phase p, GameContext ctx) { events.add("tick"); }
        });
        assertDoesNotThrow(() -> game.start(null, teams, teams.keySet()));
        assertDoesNotThrow(game::tick); assertDoesNotThrow(game::stop);
        assertEquals(List.of("exit", "enter", "tick", "exit", "enter"), events);
        assertNull(game.context());
    }
    @Test void snapshotStaysFixedUntilNextPhaseAndAttachmentsClose() {
        GameManager game = game(); MobArenaConfig c = new MobArenaConfig(); game.setConfigSource(() -> c);
        game.start(null, teams, teams.keySet()); GameContext ctx = game.context();
        assertSame(ctx.config(), ctx.config()); c.prepTimeSeconds = 80;
        assertEquals(2700, ctx.config().prepTimeSeconds());
        game.endPhaseNow(); assertEquals(80, ctx.config().prepTimeSeconds());
        int[] closed = {0}; ctx.put(Key.<AutoCloseable>of("close"), () -> closed[0]++);
        game.stop(); assertEquals(1, closed[0]);
    }
    @Test void invalidNextConfigDoesNotCallExitOrChangePhase() {
        GameManager game = game(); game.start(null, teams, teams.keySet());
        List<Phase> exits = new ArrayList<>();
        game.register(new PhaseListener() { public void onExit(Phase p, GameContext ctx) { exits.add(p); } });
        game.setConfigSource(() -> { var c = new MobArenaConfig(); c.prepTimeSeconds = 0; return c; });
        assertThrows(IllegalArgumentException.class, game::endPhaseNow);
        assertEquals(Phase.PREP, game.phase()); assertTrue(exits.isEmpty());
        game.shutdown(); assertNull(game.context()); assertEquals(Phase.LOBBY, game.phase()); assertTrue(exits.isEmpty());
    }
    @Test void nestedSuddenDeathFinishAndStopAreQueued() {
        GameManager game = game(); List<Phase> entered = new ArrayList<>();
        game.register(new PhaseListener() {
            public void onEnter(Phase p, GameContext ctx) {
                entered.add(p);
                if (p == Phase.ARENA) game.enterSuddenDeath();
                if (p == Phase.SUDDEN_DEATH) game.finish();
                if (p == Phase.END) game.stop();
            }
        });
        game.start(null, teams, teams.keySet()); game.endPhaseNow(); game.endPhaseNow();
        assertEquals(List.of(Phase.PREP, Phase.WAVE_CONFIG, Phase.ARENA, Phase.SUDDEN_DEATH, Phase.END, Phase.LOBBY), entered);
        assertNull(game.context());
    }

    @Test void queuedStopHasPriorityOverLaterPhaseRequests() {
        GameManager game = game();
        game.register(new PhaseListener() { public void onEnter(Phase p, GameContext ctx) { if (p == Phase.PREP) game.stop(); } });
        game.register(new PhaseListener() { public void onEnter(Phase p, GameContext ctx) { if (p == Phase.PREP) game.endPhaseNow(); } });
        game.start(null, teams, teams.keySet()); assertEquals(Phase.LOBBY, game.phase()); assertNull(game.context());
    }

}
