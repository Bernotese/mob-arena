package dev.mobarena.arena;

import dev.mobarena.core.TeamProgress;
import dev.mobarena.core.EliminationReason;

public sealed interface BattleEvent {
    record Countdown(int wave, int secondsLeft) implements BattleEvent {}
    record SpawnWave(int wave) implements BattleEvent {}
    record Glow(int wave) implements BattleEvent {}
    record WaveCleared(int wave) implements BattleEvent {}
    record Finished(long ticks) implements BattleEvent {}
    record Eliminated(TeamProgress progress, EliminationReason reason) implements BattleEvent {}
}
