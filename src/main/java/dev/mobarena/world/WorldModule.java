package dev.mobarena.world;

import dev.mobarena.core.GameContext;
import dev.mobarena.core.GameManager;
import dev.mobarena.core.Key;
import dev.mobarena.core.LobbyManager;
import dev.mobarena.core.Phase;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class WorldModule {
    private static final Key<Set<UUID>> PREP_RETURNS = Key.of("world.prep_returns");
    private WorldModule() {}

    static void rememberPrepReturn(GameContext ctx, ServerPlayer player) {
        if (ctx != null && ctx.team(player.getUUID()) != null) {
            ctx.getOrCreate(PREP_RETURNS, HashSet::new).add(player.getUUID());
        }
    }
    static void registerRejoinRouting(LobbyManager lobby) {
        lobby.registerRejoinHook(Phase.PREP, (ctx, player) -> {
            // T2 consumes its saved return before this hook. Track disconnects to preserve that position.
            var saved = ctx.get(PREP_RETURNS);
            if (saved != null && saved.remove(player.getUUID())) return;
            var worlds = ctx.get(TeamWorlds.KEY);
            var team = ctx.team(player.getUUID());
            if (worlds == null || !worlds.containsKey(team)) return;
            var world = worlds.get(team);
            player.teleport(new TeleportTransition(world.overworld().asLevel(), Vec3.atBottomCenterOf(world.prepSpawn()),
                    Vec3.ZERO, 0, 0, TeleportTransition.DO_NOTHING));
        });
    }
    public static void init(LobbyManager lobby) {
        lobby.registerResetHook(WorldCleanup::clean);
        registerRejoinRouting(lobby);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (GameManager.get().phase() == Phase.PREP) rememberPrepReturn(GameManager.get().context(), handler.player);
        });
        GameManager.get().register(new PrepWorldManager());
        GameManager.get().register(new ArenaWorldManager());
        GameManager.get().register(WorldCleanup.INSTANCE);
        ServerLifecycleEvents.SERVER_STARTING.register(WorldCleanup::removeCrashLeftovers);
        ServerLifecycleEvents.SERVER_STOPPING.register(WorldCleanup::clean);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents.UNLOAD.register(WorldCleanup::onUnload);
    }
}
