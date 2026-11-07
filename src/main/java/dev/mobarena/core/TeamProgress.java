package dev.mobarena.core;
/** Ticks since the first wave countdown after the arena is ready. Sudden death kills include all kills. */
public record TeamProgress(int clearedWaves, int killsInCurrentWave, long ticks) {}
