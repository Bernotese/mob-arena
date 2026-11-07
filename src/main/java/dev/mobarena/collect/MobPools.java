package dev.mobarena.collect;
import dev.mobarena.team.TeamId;
import java.util.EnumMap;
public final class MobPools {
    private final EnumMap<TeamId,MobPool> pools = new EnumMap<>(TeamId.class);
    public MobPools() { for (var team : TeamId.values()) pools.put(team,new MobPool()); }
    public MobPool get(TeamId team) { return pools.get(team); }
}
