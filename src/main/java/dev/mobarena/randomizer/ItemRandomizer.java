package dev.mobarena.randomizer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Predicate;

/** Deterministic permutation logic, independent of Minecraft. */
public final class ItemRandomizer {
    private ItemRandomizer() { }

    public static <T> Map<T, T> create(long seed, String team, List<T> items, Predicate<T> allowed) {
        List<T> sources = items.stream().filter(allowed).distinct().toList();
        List<T> targets = new ArrayList<>(sources);
        // Hash the full team key, without depending on JVM object identity.
        long teamSeed = seed;
        for (int i = 0; i < team.length(); i++) {
            teamSeed = (teamSeed ^ team.charAt(i)) * 0x100000001b3L;
        }
        Collections.shuffle(targets, new Random(teamSeed));
        Map<T, T> result = new LinkedHashMap<>();
        for (int i = 0; i < sources.size(); i++) {
            result.put(sources.get(i), targets.get(i));
        }
        return Collections.unmodifiableMap(result);
    }

    public static int capCount(int count, int maximum) {
        return Math.min(count, maximum);
    }
}
