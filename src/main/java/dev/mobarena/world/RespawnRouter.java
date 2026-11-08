package dev.mobarena.world;

import dev.mobarena.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import java.util.function.Function;

public final class RespawnRouter {
    private static final Map<Phase, Function<ServerPlayer, Optional<TeleportTransition>>> HOOKS = new EnumMap<>(Phase.class);
    private RespawnRouter() {}
    public static void register(Phase phase, Function<ServerPlayer, Optional<TeleportTransition>> hook) {
        HOOKS.put(phase, Objects.requireNonNull(hook));
    }
    public static Optional<TeleportTransition> route(ServerPlayer player) {
        var game = GameManager.get();
        var ctx = game.context();
        if (ctx == null || ctx.server() != player.level().getServer()) return Optional.empty();
        var team = ctx.team(player.getUUID());
        if (team == null) return Optional.empty();
        var hook = HOOKS.get(game.phase());
        if (hook != null) return hook.apply(player);
        if (game.phase() != Phase.PREP) return Optional.empty();
        var worlds = ctx.get(TeamWorlds.KEY);
        if (worlds == null || !worlds.containsKey(team)) return Optional.empty();
        var world = worlds.get(team);
        return Optional.of(new TeleportTransition(world.overworld().asLevel(),
                Vec3.atBottomCenterOf(world.prepSpawn()), Vec3.ZERO, 0, 0, TeleportTransition.DO_NOTHING));
    }
    public static boolean accepts(ServerPlayer player, TeleportTransition result) {
        var game = GameManager.get();
        var ctx = game.context();
        return game.phase() == Phase.PREP && result != null && ctx != null && ctx.team(player.getUUID()) != null
                && GameLevels.kindOf(result.newLevel()).orElse(null) == WorldKind.PREP_OVERWORLD
                && GameLevels.teamOf(result.newLevel()).orElse(null) == ctx.team(player.getUUID());
    }
}
