package dev.mobarena.arena;

import java.util.*;

/** Reservations count toward the alive cap until the next tick reports actual alive mobs. */
public final class SpawnQueue {
    public static final int SPAWN_PER_TICK = 10;
    private final Deque<SpawnPlanner.SpawnOrder> pending;
    private final int maxAliveMobs;

    public SpawnQueue(List<SpawnPlanner.SpawnOrder> orders, int maxAliveMobs) {
        if (maxAliveMobs <= 0) throw new IllegalArgumentException("Invalid alive cap");
        this.pending = new ArrayDeque<>(orders);
        this.maxAliveMobs = maxAliveMobs;
    }
    public List<SpawnPlanner.SpawnOrder> tick(int alive) {
        if (alive < 0) throw new IllegalArgumentException("Negative alive count");
        int count = Math.min(SPAWN_PER_TICK, Math.max(0, maxAliveMobs - alive));
        var batch = new ArrayList<SpawnPlanner.SpawnOrder>();
        while (count-- > 0 && !pending.isEmpty()) batch.add(pending.removeFirst());
        return List.copyOf(batch);
    }
    public boolean spawningDone() { return pending.isEmpty(); }
    public int remaining() { return pending.size(); }
}
