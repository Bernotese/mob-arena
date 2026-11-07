package dev.mobarena.arena;

import dev.mobarena.core.GameContext;
import dev.mobarena.team.TeamId;
import dev.mobarena.world.ArenaLayout;
import dev.mobarena.world.TeamWorlds;
import net.minecraft.server.level.ServerLevel;
import java.util.Objects;
import java.util.Optional;

/** A ready arena: the T5 level and layout of one team. */
public record Arena(ServerLevel level, ArenaLayout layout) {
    public Arena {
        Objects.requireNonNull(level); Objects.requireNonNull(layout);
        if (layout.mobSpawns().isEmpty()) throw new IllegalArgumentException("Arena needs mob spawns");
    }
    /** Empty until T5 has made the arena level and its layout (ready signal). */
    public static Optional<Arena> fromWorlds(GameContext ctx, TeamId team) {
        var worlds = ctx.get(TeamWorlds.KEY);
        var world = worlds == null ? null : worlds.get(team);
        if (world == null || world.arena() == null || world.layout() == null) return Optional.empty();
        return Optional.of(new Arena(world.arena().asLevel(), world.layout()));
    }
}
