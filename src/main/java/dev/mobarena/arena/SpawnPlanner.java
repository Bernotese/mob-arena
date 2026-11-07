package dev.mobarena.arena;

import java.util.*;
import java.util.function.Predicate;

public final class SpawnPlanner {
    private SpawnPlanner() {}
    public record SpawnOrder(String entityId, int pointIndex, boolean water) {}

    public static List<SpawnOrder> plan(SortedMap<String, Integer> wave, int ringSize,
                                        int waterSize, Predicate<String> isWater, long seed) {
        if (ringSize <= 0 || waterSize < 0) throw new IllegalArgumentException("Invalid points");
        var mobs = new ArrayList<String>();
        wave.forEach((id, count) -> {
            Objects.requireNonNull(id);
            if (count == null || count < 0) throw new IllegalArgumentException("Negative count");
            for (int i = 0; i < count; i++) mobs.add(id);
        });
        var random = new Random(seed);
        Collections.shuffle(mobs, random);
        var ring = new ArrayList<Integer>();
        for (int i = 0; i < ringSize; i++) ring.add(i);
        Collections.shuffle(ring, random);
        int land = 0, water = 0;
        var result = new ArrayList<SpawnOrder>();
        for (String id : mobs) {
            boolean wet = isWater.test(id);
            int point = wet && waterSize > 0 ? water++ % waterSize : ring.get(land++ % ringSize);
            result.add(new SpawnOrder(id, point, wet));
        }
        return List.copyOf(result);
    }
}
