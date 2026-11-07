package dev.mobarena.arena;

import java.util.*;

/** Reservations count toward the alive cap until an explicit fresh alive report. */
public final class SpawnQueue {
    public static final int SPAWN_PER_TICK = 10;
    private final Deque<SpawnPlanner.SpawnOrder> pending;
    private final int maxAliveMobs;
    private int reportedAlive;
    private int spawnedSinceReport;

    public SpawnQueue(List<SpawnPlanner.SpawnOrder> orders, int maxAliveMobs) {
        if (maxAliveMobs <= 0) throw new IllegalArgumentException("Invalid alive cap");
        this.pending = new ArrayDeque<>(orders);
        this.maxAliveMobs = maxAliveMobs;
    }
    /** Submit a fresh scan that includes all prior spawn reservations. */
    public void report(int alive) {
        if (alive < 0) throw new IllegalArgumentException("Negative alive count");
        reportedAlive = alive;
        spawnedSinceReport = 0;
    }

    public List<SpawnPlanner.SpawnOrder> tick() {
        long capacity = (long) maxAliveMobs - reportedAlive - spawnedSinceReport;
        int count = (int) Math.min(SPAWN_PER_TICK, Math.max(0L, capacity));
        var batch = new ArrayList<SpawnPlanner.SpawnOrder>();
        while (count-- > 0 && !pending.isEmpty()) batch.add(pending.removeFirst());
        spawnedSinceReport += batch.size();
        return List.copyOf(batch);
    }

    /** Reuse an alive count without clearing reservations; use report for a fresh scan. */
    public List<SpawnPlanner.SpawnOrder> tick(int alive) {
        if (alive < 0) throw new IllegalArgumentException("Negative alive count");
        reportedAlive = alive;
        return tick();
    }
    public boolean spawningDone() { return pending.isEmpty(); }
    public int remaining() { return pending.size(); }
}
