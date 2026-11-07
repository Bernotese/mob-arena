package dev.mobarena.world;

import dev.mobarena.MobArena;
import dev.mobarena.core.*;
import dev.mobarena.team.TeamId;

public final class ArenaWorldManager implements PhaseListener {
    public static void create(GameContext ctx) {
        var teams = ctx.get(TeamWorlds.KEY);
        if (teams == null) throw new IllegalStateException("Prep worlds are missing");
        for (TeamId team : TeamId.values()) {
            var handle = LevelFactory.open(ctx.server(), ctx.seed(), team, WorldKind.ARENA);
            var level = handle.asLevel();
            var layout = ArenaTemplate.load(level, ctx.config().arenaTemplate(), ctx.seed());
            teams.put(team, teams.get(team).withArena(handle, layout));
            ctx.setArenaLevel(team, level);
        }
    }
    @Override public void onEnter(Phase phase, GameContext ctx) {
        if (phase == Phase.WAVE_CONFIG && ctx.server() != null) {
            try { create(ctx); }
            catch (RuntimeException e) {
                MobArena.LOGGER.error("Cannot create arenas", e);
                GameManager.get().stop();
            }
        }
        if ((phase == Phase.ARENA || phase == Phase.SUDDEN_DEATH) && ctx.server() != null) {
            var worlds = ctx.get(TeamWorlds.KEY);
            if (worlds != null) for (var world : worlds.values()) {
                var bounds = world.layout().bounds();
                for (int x = ((int) Math.floor(bounds.minX)) >> 4; x <= ((int) Math.floor(bounds.maxX - 1)) >> 4; x++) {
                    for (int z = ((int) Math.floor(bounds.minZ)) >> 4; z <= ((int) Math.floor(bounds.maxZ - 1)) >> 4; z++) {
                        WorldCleanup.force(world.arena().asLevel(), x, z);
                    }
                }
            }
        }
    }
}
