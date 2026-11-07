package dev.mobarena.collect;
import dev.mobarena.core.Key;
import java.util.UUID;
public final class CollectKeys {
    private CollectKeys() {}
    public static final Key<MobPools> POOLS = Key.of("collect.pools");
    public static final Key<LastHitTracker<UUID,UUID>> HITS = Key.of("collect.hits");
}
