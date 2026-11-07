package dev.mobarena.world;

import dev.mobarena.core.Key;
import dev.mobarena.team.TeamId;
import net.minecraft.core.BlockPos;
import xyz.nucleoid.fantasy.RuntimeLevelHandle;
import java.util.Map;
import java.util.stream.Stream;

/** Arena fields are absent until WAVE_CONFIG. Replace this record when the arena is ready. */
public record TeamWorlds(RuntimeLevelHandle overworld, RuntimeLevelHandle nether,
                         RuntimeLevelHandle end, RuntimeLevelHandle arena,
                         BlockPos prepSpawn, ArenaLayout layout) {
    public static final Key<Map<TeamId, TeamWorlds>> KEY = Key.of("world.teams");
    public TeamWorlds { prepSpawn = prepSpawn.immutable(); }
    public Stream<RuntimeLevelHandle> handles() {
        return Stream.of(overworld, nether, end, arena).filter(java.util.Objects::nonNull);
    }
    public TeamWorlds withArena(RuntimeLevelHandle handle, ArenaLayout layout) {
        return new TeamWorlds(overworld, nether, end, handle, prepSpawn, layout);
    }
}
