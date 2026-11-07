package dev.mobarena.core;
/** Ticks since ARENA or SUDDEN_DEATH start. Sudden death kills include all kills. */
public record TeamProgress(int clearedWaves, int killsInCurrentWave, long ticks) {}
