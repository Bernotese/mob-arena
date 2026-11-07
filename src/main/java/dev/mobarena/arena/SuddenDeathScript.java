package dev.mobarena.arena;

import java.util.*;

/** SPEC 8.1: sudden death fights all 3 enemy waves as one wave. */
final class SuddenDeathScript {
    private SuddenDeathScript() {}

    /** Sums the counts of the same mob id over all waves. Null or empty input gives an empty wave. */
    static SortedMap<String, Integer> merge(List<? extends Map<String, Integer>> waves) {
        SortedMap<String, Integer> all = new TreeMap<>();
        if (waves != null) for (var wave : waves) wave.forEach((id, n) -> all.merge(id, n, Integer::sum));
        return all;
    }
}
