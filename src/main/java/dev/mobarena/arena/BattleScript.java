package dev.mobarena.arena;

import java.util.*;

public record BattleScript(List<SortedMap<String, Integer>> waves, int countdownTicks,
                           int cooldownTicks, int maxWaveTicks, int glowAfterTicks, int prepTicks) {
    public BattleScript(List<SortedMap<String, Integer>> waves, int countdownTicks, int cooldownTicks,
                        int maxWaveTicks, int glowAfterTicks) {
        this(waves, countdownTicks, cooldownTicks, maxWaveTicks, glowAfterTicks, 0);
    }
    public BattleScript {
        if (waves.isEmpty() || countdownTicks < 0 || cooldownTicks < 0
                || prepTicks < 0 || maxWaveTicks <= 0 || glowAfterTicks < 0) throw new IllegalArgumentException("Invalid script");
        waves = waves.stream().map(w -> {
            if (w.values().stream().anyMatch(n -> n == null || n < 0))
                throw new IllegalArgumentException("Negative count");
            return Collections.unmodifiableSortedMap(new TreeMap<>(w));
        }).toList();
    }
}
