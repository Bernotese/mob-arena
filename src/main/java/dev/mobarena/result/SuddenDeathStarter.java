package dev.mobarena.result;

import dev.mobarena.core.GameContext;
import dev.mobarena.core.ResultHooks;

/**
 * Hook for the sudden death battle (T7b). Called once in {@code SUDDEN_DEATH.onEnter}, after both teams
 * were reset to running. The battle reports to {@code hooks} like in ARENA.
 */
@FunctionalInterface
public interface SuddenDeathStarter {
    void start(GameContext ctx, ResultHooks hooks);
}
