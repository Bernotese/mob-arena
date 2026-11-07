package dev.mobarena.arena;

import dev.mobarena.core.*;
import dev.mobarena.gametest.TestEnd;
import dev.mobarena.gametest.TestPlayers;
import dev.mobarena.team.TeamId;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

/** Starts a real game (T5 arenas) and enters ARENA. Each test runs in its own environment batch. */
final class ArenaFixture implements AutoCloseable {
    final GameManager game = GameManager.get();
    final GameTestHelper h;
    final ServerPlayer red, blue, mate;
    final GameContext ctx;
    final List<String> results = new ArrayList<>();
    final BattleListener recorder = new BattleListener() {
        @Override public void finished(TeamId team, long ticks) { results.add("finished " + team); }
        @Override public void eliminated(TeamId team, TeamProgress progress) {
            results.add("eliminated " + team + " " + progress.clearedWaves() + " " + progress.killsInCurrentWave());
        }
    };

    ArenaFixture(GameTestHelper h, boolean withMate, boolean enterArena) { this(h, withMate, enterArena, false); }

    /** realResults: keep the real ResultTracker (T8 resolver and sudden death), no recording hooks. */
    ArenaFixture(GameTestHelper h, boolean withMate, boolean enterArena, boolean realResults) {
        this.h = h;
        TestEnd.stopStaleGame();
        red = player(h); blue = player(h);
        mate = withMate ? player(h) : null;
        var teams = new HashMap<UUID, TeamId>();
        teams.put(red.getUUID(), TeamId.RED); teams.put(blue.getUUID(), TeamId.BLUE);
        if (mate != null) teams.put(mate.getUUID(), TeamId.RED);
        if (!game.start(h.getLevel().getServer(), teams, teams.keySet())) throw new IllegalStateException("Cannot start game");
        ctx = game.context();
        if (!realResults) ctx.put(ResultHooks.KEY, new ResultHooks() {
            @Override public void teamFinished(TeamId team, long timeTicks) { recorder.finished(team, timeTicks); }
            @Override public void teamEliminated(TeamId team, TeamProgress progress) { recorder.eliminated(team, progress); }
        });
        TestEnd.run(h, this::close);
        if (enterArena) { game.endPhaseNow(); game.endPhaseNow(); }
    }

    /** Chat (system) messages per fixture player UUID. */
    final Map<UUID, List<String>> chat = new HashMap<>();
    private ServerPlayer player(GameTestHelper h) {
        var messages = new ArrayList<String>();
        var player = TestPlayers.recording(h, messages);
        chat.put(player.getUUID(), messages);
        return player;
    }

    ArenaBattles group() { return ArenaBattles.get(ctx); }
    /** Fails the test (no crash) if the game already ended, for example after a test timeout. */
    Arena arena(TeamId team) {
        var arena = Arena.fromWorlds(ctx, team).orElse(null);
        if (arena == null) h.fail("Arena of " + team + " is missing");
        return arena;
    }

    @SafeVarargs
    final ArenaBattle run(TeamId team, int countdown, int cooldown, int maxTicks, int glow, Map<String, Integer>... waves) {
        var list = Arrays.stream(waves).map(w -> (SortedMap<String, Integer>) new TreeMap<>(w)).toList();
        return group().start(team, () -> Arena.fromWorlds(ctx, team), new BattleScript(list, countdown, cooldown, maxTicks, glow),
                recorder, false);
    }

    @Override public void close() {
        if (game.context() == ctx) TestEnd.stopStaleGame();
        for (var p : new ServerPlayer[]{red, blue, mate}) if (p != null && ctx.server().getPlayerList().getPlayer(p.getUUID()) != null)
            ctx.server().getPlayerList().remove(p);
    }
}
