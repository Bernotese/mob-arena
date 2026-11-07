package dev.mobarena.collect;

import java.util.*;

public final class LastHitTracker<V, P> {
    public static final long TEN_SECONDS = 200;
    private record Hit<P>(P player, long tick) {}
    private final long windowTicks;
    private final Map<V, Hit<P>> hits = new HashMap<>();

    public LastHitTracker(long windowTicks) {
        if (windowTicks < 0) throw new IllegalArgumentException("Negative window");
        this.windowTicks = windowTicks;
    }

    public void record(V victim, P player, long tick) {
        Objects.requireNonNull(victim);
        Objects.requireNonNull(player);
        hits.compute(victim, (key, old) -> old == null || tick >= old.tick()
                ? new Hit<>(player, tick) : old);
    }

    private boolean active(Hit<P> hit, long now) {
        return now >= hit.tick() && now - hit.tick() >= 0 && now - hit.tick() <= windowTicks;
    }

    public Optional<P> lookup(V victim, long now) {
        Hit<P> hit = hits.get(victim);
        return hit != null && active(hit, now) ? Optional.of(hit.player()) : Optional.empty();
    }

    public void forget(V victim) { hits.remove(victim); }
    public void prune(long now) {
        hits.values().removeIf(hit -> now >= hit.tick() && !active(hit, now));
    }
    public void clear() { hits.clear(); }
    public int size() { return hits.size(); }
}
