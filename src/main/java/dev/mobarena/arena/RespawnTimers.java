package dev.mobarena.arena;

import java.util.*;

/** Due ticks of players in the arena respawn timer. Pure logic. */
public final class RespawnTimers {
    private final Map<UUID, Long> due = new LinkedHashMap<>();

    public void put(UUID player, long dueTick) { due.put(Objects.requireNonNull(player), dueTick); }
    public boolean isDown(UUID player) { return due.containsKey(player); }
    public void cancel(UUID player) { due.remove(player); }
    public void clear() { due.clear(); }
    public Set<UUID> down() { return Set.copyOf(due.keySet()); }
    /** Ticks left, or -1 if the player is not down. */
    public long remaining(UUID player, long now) {
        Long tick = due.get(player);
        return tick == null ? -1 : Math.max(0, tick - now);
    }
    /** Removes and returns all players whose timer ended at or before now. */
    public List<UUID> takeDue(long now) {
        var result = new ArrayList<UUID>();
        for (var it = due.entrySet().iterator(); it.hasNext();) {
            var entry = it.next();
            if (entry.getValue() <= now) { result.add(entry.getKey()); it.remove(); }
        }
        return result;
    }
}
