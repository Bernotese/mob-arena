package dev.mobarena.world;

import dev.mobarena.core.GameManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

public final class PortalKeys {
    private PortalKeys() {}
    public static ResourceKey<Level> vanillaKeyOf(ServerLevel level) {
        return GameLevels.kindOf(level).map(WorldKind::vanillaKey).orElse(level.dimension());
    }
    public static ServerLevel teamLevel(ServerLevel source, ResourceKey<Level> wanted) {
        return GameLevels.teamOf(source).map(team -> GameLevels.level(team, PortalLink.kindFor(wanted))).orElse(null);
    }
    public static BlockPos prepSpawn(ServerLevel source) {
        var ctx = GameManager.get().context();
        var worlds = ctx == null ? null : ctx.get(TeamWorlds.KEY);
        var team = GameLevels.teamOf(source).orElse(null);
        if (worlds == null || !worlds.containsKey(team)) return source.getRespawnData().pos();
        return worlds.get(team).prepSpawn();
    }
}
