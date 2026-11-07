package dev.mobarena.wave;

import java.util.*;

public final class WaveSetup {
    public static final int WAVES = 3;
    private final SortedMap<String, Integer> pool = new TreeMap<>();
    private final List<SortedMap<String, Integer>> waves = List.of(new TreeMap<>(), new TreeMap<>(), new TreeMap<>());
    private boolean finished;
    private long version;

    public WaveSetup(Map<String, Integer> pool) {
        pool.forEach((id, count) -> {
            Objects.requireNonNull(id);
            if (count > 0) this.pool.put(id, count);
        });
    }

    private SortedMap<String, Integer> mutableWave(int w) {
        if (w < 1 || w > WAVES) throw new IllegalArgumentException("Wave must be 1..3");
        return waves.get(w - 1);
    }
    static SortedMap<String, Integer> copy(Map<String, Integer> map) {
        return Collections.unmodifiableSortedMap(new TreeMap<>(map));
    }
    public SortedMap<String, Integer> pool() { return Collections.unmodifiableSortedMap(pool); }
    public SortedMap<String, Integer> wave(int w) { return Collections.unmodifiableSortedMap(mutableWave(w)); }
    public int poolCount(String id) { return pool.getOrDefault(id, 0); }
    public int waveCount(int w, String id) { return mutableWave(w).getOrDefault(id, 0); }
    private static int total(Map<String, Integer> map) {
        return map.values().stream().reduce(0, Math::addExact);
    }
    public int poolTotal() { return total(pool); }
    public int waveTotal(int w) { return total(mutableWave(w)); }
    public int toWave(String id, int w, int amount) {
        checkMutable();
        return move(pool, mutableWave(w), id, amount);
    }
    public int toPool(int w, String id, int amount) {
        checkMutable();
        return move(mutableWave(w), pool, id, amount);
    }
    private void checkMutable() {
        if (finished) throw new IllegalStateException("Wave setup is finished");
    }
    private int move(Map<String, Integer> from, Map<String, Integer> to, String id, int amount) {
        if (amount <= 0) return 0;
        int available = from.getOrDefault(id, 0);
        int moved = Math.min(available, amount);
        if (moved == 0) return 0;
        to.merge(id, moved, Math::addExact);
        if (available == moved) from.remove(id);
        else from.put(id, available - moved);
        version++;
        return moved;
    }
    public void finish() {
        if (finished) return;
        if (!pool.isEmpty()) {
            pool.forEach((id, count) -> waves.get(2).merge(id, count, Math::addExact));
            pool.clear();
            version++;
        }
        finished = true;
    }
    public boolean finished() { return finished; }
    public long version() { return version; }
    public List<SortedMap<String, Integer>> snapshot() {
        return waves.stream().map(WaveSetup::copy).toList();
    }
}
