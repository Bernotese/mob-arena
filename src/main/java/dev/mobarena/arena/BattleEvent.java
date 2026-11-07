package dev.mobarena.arena;

import dev.mobarena.core.TeamProgress;

public sealed interface BattleEvent {
    record Countdown(int wave, int secondsLeft) implements BattleEvent {}
    record SpawnWave(int wave) implements BattleEvent {}
    record Glow(int wave) implements BattleEvent {}
    record WaveCleared(int wave) implements BattleEvent {}
    record Finished(long ticks) implements BattleEvent {}
    record Eliminated(TeamProgress progress, Reason reason) implements BattleEvent {}
    enum Reason { TIME, ALL_DOWN, NO_PLAYERS }
}
