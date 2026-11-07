package dev.mobarena.core;
public interface PhaseListener {
    default void onEnter(Phase phase, GameContext ctx) {}
    default void onExit(Phase phase, GameContext ctx) {}
    default void onTick(Phase phase, GameContext ctx) {}
}
