package dev.mobarena.collect;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class KillAttributionTest {
    private final UUID player = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();
    private final UUID victim = UUID.randomUUID();
    private final Map<UUID, KillAttribution.Actor> actors = new HashMap<>(Map.of(
            player, new KillAttribution.Actor(true, null, null),
            other, new KillAttribution.Actor(true, null, null)));
    private final LastHitTracker<UUID, UUID> hits = new LastHitTracker<>(200);

    private Optional<UUID> kill(UUID causing, long now) {
        return KillAttribution.resolveKiller(victim, causing, now, actors::get, id -> id.equals(player), hits);
    }
    @Test void directProjectileAndPetAlwaysCountAndDirectWins() {
        hits.record(victim, other, 0);
        assertEquals(Optional.of(player), kill(player, 999));
        assertEquals(0, hits.size());
        UUID projectile = UUID.randomUUID();
        actors.put(projectile, new KillAttribution.Actor(false, null, player));
        assertEquals(Optional.of(player), kill(projectile, 999));
        UUID pet = UUID.randomUUID();
        actors.put(pet, new KillAttribution.Actor(false, player, null));
        assertEquals(Optional.of(player), kill(pet, 999));
        hits.record(victim, other, 100);
        assertEquals(Optional.of(player), KillAttribution.resolveKiller(victim, player, 100,
                actors::get, id -> true, hits));
    }
    @Test void environmentalAndOtherMobDeathsUseInclusiveLedgerAndConsumeIt() {
        for (UUID cause : new UUID[]{null, UUID.randomUUID()}) {
            hits.record(victim, player, 100);
            assertEquals(Optional.of(player), kill(cause, 300));
            assertEquals(0, hits.size());
            hits.record(victim, player, 100);
            assertTrue(kill(cause, 301).isEmpty());
            assertEquals(0, hits.size());
        }
    }
    @Test void excludesSpectatorsAndPlayersWithoutTeamsIncludingLedger() {
        assertTrue(kill(other, 0).isEmpty());
        UUID pet = UUID.randomUUID();
        actors.put(pet, new KillAttribution.Actor(false, other, null));
        assertTrue(kill(pet, 0).isEmpty());
        hits.record(victim, other, 100);
        assertTrue(kill(null, 100).isEmpty());
        assertEquals(0, hits.size());
    }
    @Test void onlyUnblockedEligibleHitsUpdateLedger() {
        KillAttribution.recordHit(victim, player, true, 100, actors::get, id -> true, hits);
        assertEquals(0, hits.size());
        KillAttribution.recordHit(victim, player, false, 100, actors::get, id -> id.equals(player), hits);
        assertEquals(Optional.of(player), hits.lookup(victim, 100));
        KillAttribution.recordHit(victim, other, false, 101, actors::get, id -> id.equals(player), hits);
        KillAttribution.recordHit(victim, other, true, 102, actors::get, id -> true, hits);
        assertEquals(Optional.of(player), hits.lookup(victim, 102));
    }
    @Test void ownerChainsAreBoundedAndCyclesStop() {
        UUID start = player;
        for (int i = 0; i < 4; i++) {
            UUID next = UUID.randomUUID();
            actors.put(next, new KillAttribution.Actor(false, null, start));
            start = next;
        }
        assertEquals(Optional.of(player), kill(start, 0));
        UUID tooDeep = UUID.randomUUID();
        actors.put(tooDeep, new KillAttribution.Actor(false, null, start));
        assertTrue(kill(tooDeep, 0).isEmpty());
        UUID cycle = UUID.randomUUID();
        actors.put(cycle, new KillAttribution.Actor(false, null, cycle));
        assertTrue(kill(cycle, 0).isEmpty());
    }
}
