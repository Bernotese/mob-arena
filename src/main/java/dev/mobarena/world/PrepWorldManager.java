package dev.mobarena.world;

import dev.mobarena.MobArena;
import dev.mobarena.core.*;
import dev.mobarena.team.TeamId;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import java.util.*;

public final class PrepWorldManager implements PhaseListener {
    public static Map<TeamId, TeamWorlds> create(GameContext ctx) {
        // A shutdown can clear the context without phase callbacks. Retire its handles first.
        WorldCleanup.clean(ctx.server());
        Map<TeamId, TeamWorlds> teams = new EnumMap<>(TeamId.class);
        ctx.put(TeamWorlds.KEY, teams);
        try {
            for (TeamId team : TeamId.values()) {
                var overworld = LevelFactory.open(ctx.server(), ctx.seed(), team, WorldKind.PREP_OVERWORLD);
                var nether = LevelFactory.open(ctx.server(), ctx.seed(), team, WorldKind.PREP_NETHER);
                var end = LevelFactory.open(ctx.server(), ctx.seed(), team, WorldKind.PREP_END);
                long started = System.nanoTime();
                var spawn = TeamSpawnFinder.find(overworld.asLevel());
                MobArena.LOGGER.info("Found {} prep spawn in {} ms", team, (System.nanoTime() - started) / 1_000_000);
                overworld.asLevel().getChunk(spawn.getX() >> 4, spawn.getZ() >> 4);
                teams.put(team, new TeamWorlds(overworld, nether, end, null, spawn, null));
                ctx.setPrepLevel(team, overworld.asLevel());
            }
            return teams;
        } catch (RuntimeException e) {
            WorldCleanup.clean(ctx.server());
            ctx.put(TeamWorlds.KEY, null);
            for (TeamId team : TeamId.values()) ctx.setPrepLevel(team, null);
            throw e;
        }
    }
    @Override public void onEnter(Phase phase, GameContext ctx) {
        if (phase != Phase.PREP || ctx.server() == null) return;
        try {
            var worlds = create(ctx);
            dev.mobarena.randomizer.RandomizerModule.hook().startGame(ctx.seed(),
                    java.util.Arrays.stream(TeamId.values()).map(TeamId::name).toList(),
                    level -> GameLevels.isPrepLevel(level) ? GameLevels.teamOf(level).map(TeamId::name)
                            : java.util.Optional.empty(), ctx.config().randomizerEnabled());
            for (var player : ctx.server().getPlayerList().getPlayers()) {
                var team = ctx.team(player.getUUID());
                if (team == null) continue;
                var world = worlds.get(team);
                player.teleport(new TeleportTransition(world.overworld().asLevel(), Vec3.atBottomCenterOf(world.prepSpawn()),
                        Vec3.ZERO, 0, 0, TeleportTransition.DO_NOTHING));
            }
        } catch (RuntimeException e) {
            MobArena.LOGGER.error("Cannot create prep worlds", e);
            GameManager.get().stop();
        }
    }
}
