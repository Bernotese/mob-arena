package dev.mobarena.result;

import dev.mobarena.MobArena;
import dev.mobarena.core.GameContext;
import dev.mobarena.core.GameManager;
import dev.mobarena.core.Phase;
import dev.mobarena.core.PhaseListener;
import dev.mobarena.core.ResultHooks;
import java.util.Objects;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.Identifier;

/** T8: result tracking, verdicts and the end sequence. */
public final class ResultModule {
    /** Runs after the default END_SERVER_TICK phase, so after GameManager.tick and all battle ticks. */
    static final Identifier RESOLVE_PHASE = Identifier.fromNamespaceAndPath(MobArena.MOD_ID, "resolve_results");
    private static SuddenDeathStarter suddenDeath = (ctx, hooks) ->
            MobArena.LOGGER.warn("No sudden death battle is installed. Use /ma stop to end the game.");

    private ResultModule() {}

    public static void init() {
        GameManager game = GameManager.get();
        game.setResultHooksFactory(ctx -> new ResultTracker(game::phase));
        game.register(new PhaseListener() {
            @Override public void onEnter(Phase phase, GameContext ctx) {
                if (phase != Phase.SUDDEN_DEATH || !(ctx.get(ResultHooks.KEY) instanceof ResultTracker tracker)) return;
                tracker.startSuddenDeath();
                suddenDeath.start(ctx, tracker);
            }
        });
        game.register(new EndSequence());
        ServerTickEvents.END_SERVER_TICK.addPhaseOrdering(Event.DEFAULT_PHASE, RESOLVE_PHASE);
        ServerTickEvents.END_SERVER_TICK.register(RESOLVE_PHASE, server -> resolve(game));
    }

    /** T7b installs the sudden death battle here. */
    public static void setSuddenDeathStarter(SuddenDeathStarter starter) { suddenDeath = Objects.requireNonNull(starter); }

    /** Runs the resolver once and applies the verdict. Package-private for tests. */
    static void resolve(GameManager game) {
        GameContext ctx = game.context();
        if (ctx == null || !(ctx.get(ResultHooks.KEY) instanceof ResultTracker tracker) || !tracker.dirty()) return;
        Verdict verdict = tracker.resolve();
        switch (verdict) {
            case Verdict.Pending pending -> { }
            case Verdict.SuddenDeath sd -> game.enterSuddenDeath();
            case Verdict.Win win -> finish(game, ctx, tracker, verdict);
            case Verdict.Draw draw -> finish(game, ctx, tracker, verdict);
        }
    }

    private static void finish(GameManager game, GameContext ctx, ResultTracker tracker, Verdict verdict) {
        var result = tracker.result(verdict);
        ctx.put(ResultKeys.RESULT, result);
        MobArena.LOGGER.info("Game result: {} ({})", result.winner().map(Enum::name).orElse("draw"), result.reason());
        game.finish();
    }
}
