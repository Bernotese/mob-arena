package dev.mobarena.collect;

import java.util.*;

public final class MobPool {
    private final SortedMap<String, Integer> counts = new TreeMap<>();

    public void add(String id) {
        counts.merge(Objects.requireNonNull(id), 1, Math::addExact);
    }

    public int count(String id) { return counts.getOrDefault(id, 0); }
    public int total() { return counts.values().stream().reduce(0, Math::addExact); }
    public SortedMap<String, Integer> snapshot() {
        return Collections.unmodifiableSortedMap(new TreeMap<>(counts));
    }
}
