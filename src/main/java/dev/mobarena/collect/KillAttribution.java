package dev.mobarena.collect;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

/** Pure attribution policy. Adapters supply causing entities and owner UUIDs. */
public final class KillAttribution {
    private KillAttribution() {}

    /** rootOwner is the resolved tame owner; owner is the traceable entity owner. */
    public record Actor(boolean player, UUID rootOwner, UUID owner) {}

    public static Optional<UUID> resolvePlayer(UUID causing, Function<UUID, Actor> actors,
                                               Predicate<UUID> eligible) {
        Set<UUID> visited = new HashSet<>();
        UUID current = causing;
        for (int depth = 0; current != null && depth <= 4 && visited.add(current); depth++) {
            Actor actor = actors.apply(current);
            if (actor == null) return Optional.empty();
            if (actor.player()) return eligible.test(current) ? Optional.of(current) : Optional.empty();
            if (actor.rootOwner() != null) {
                Actor root = actors.apply(actor.rootOwner());
                if (root != null && root.player() && eligible.test(actor.rootOwner())) {
                    return Optional.of(actor.rootOwner());
                }
            }
            current = actor.owner();
        }
        return Optional.empty();
    }

    /** Unblocked hits count even when the damage amount is zero. */
    public static void recordHit(UUID victim, UUID causing, boolean blocked, long tick,
            Function<UUID, Actor> actors, Predicate<UUID> eligible,
            LastHitTracker<UUID, UUID> hits) {
        if (!blocked) resolvePlayer(causing, actors, eligible)
                .ifPresent(player -> hits.record(victim, player, tick));
    }

    /** Direct responsibility wins. Consume the victim's ledger entry on every death. */
    public static Optional<UUID> resolveKiller(UUID victim, UUID causing, long now,
            Function<UUID, Actor> actors, Predicate<UUID> eligible,
            LastHitTracker<UUID, UUID> hits) {
        Optional<UUID> direct = resolvePlayer(causing, actors, eligible);
        Optional<UUID> result = direct.isPresent() ? direct : hits.lookup(victim, now).filter(eligible);
        hits.forget(victim);
        return result;
    }
}
