package dev.mobarena.arena;

import dev.mobarena.core.*;
import dev.mobarena.world.RespawnRouter;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import java.util.Optional;

public final class ArenaModule {
    private ArenaModule() {}
    public static void init(LobbyManager lobby) {
        GameManager.get().register(new ArenaPhaseListener());
        ArenaRules.register();
        dev.mobarena.result.ResultModule.setSuddenDeathStarter(
                (ctx, hooks) -> ArenaPhaseListener.startSuddenDeath(ctx, BattleListener.of(hooks)));
        for (Phase phase : new Phase[]{Phase.ARENA, Phase.SUDDEN_DEATH}) {
            lobby.registerRejoinHook(phase, (ctx, player) -> {
                var group = ArenaBattles.get(ctx);
                if (group != null) group.rejoin(player);
            });
        }
        for (Phase phase : new Phase[]{Phase.ARENA, Phase.SUDDEN_DEATH, Phase.END}) {
            // Fallback if a vanilla death still happens: respawn in the own arena, then the timer starts.
            RespawnRouter.register(phase, player -> {
                var ctx = GameManager.get().context();
                var team = ctx == null ? null : ctx.team(player.getUUID());
                if (team == null) return Optional.empty();
                // END entry runs after the previous battle group has closed.
                var group = ArenaBattles.get(ctx);
                var battle = group == null ? null : group.battle(team);
                var target = phase == Phase.END ? Arena.fromWorlds(ctx, team)
                        : battle == null ? Optional.<Arena>empty() : battle.arena();
                if (target.isEmpty()) return Optional.empty();
                var arena = target.get();
                return Optional.of(new TeleportTransition(arena.level(), Vec3.atBottomCenterOf(arena.layout().teamSpawn()),
                        Vec3.ZERO, 0, 0, TeleportTransition.DO_NOTHING));
            });
        }
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            var group = ArenaBattles.get(GameManager.get().context());
            if (!alive && group != null) group.rejoin(newPlayer);
        });
    }
}
