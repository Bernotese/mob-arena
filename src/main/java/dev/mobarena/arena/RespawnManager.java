package dev.mobarena.arena;

import dev.mobarena.hud.ActionBar;
import dev.mobarena.hud.Titles;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import java.util.*;

/** SPEC 7.5: a dead team player is a spectator for arenaRespawnSeconds, then respawns at the team spawn. */
final class RespawnManager {
    private final ArenaBattles group;
    private final RespawnTimers timers = new RespawnTimers();
    private int respawnTicks;

    RespawnManager(ArenaBattles group, int respawnTicks) { this.group = group; this.respawnTicks = respawnTicks; }
    void setRespawnTicks(int ticks) { respawnTicks = ticks; }
    boolean isDown(UUID id) { return timers.isDown(id); }
    Set<UUID> down() { return timers.down(); }
    void clear() { timers.clear(); }
    void cancelTeam(dev.mobarena.team.TeamId team) {
        for (UUID id : timers.down()) if (group.ctx().team(id) == team) timers.cancel(id);
    }

    /** ServerPlayerEvents.ALLOW_DEATH: returns false if the arena handles the death. */
    boolean allowDeath(ServerPlayer player, DamageSource source) {
        var team = group.ctx().team(player.getUUID());
        var battle = team == null ? null : group.battle(team);
        if (battle == null || battle.arena().isEmpty() || player.level() != battle.arena().get().level()) return true;
        var state = battle.state();
        if (state == BattleFlow.State.WAITING_ARENA || state == BattleFlow.State.ELIMINATED) return true;
        // A totem in a hand saves the player the vanilla way (Fabric fires ALLOW_DEATH before the totem check).
        if (player.getMainHandItem().is(Items.TOTEM_OF_UNDYING) || player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) return true;
        Component message = player.getCombatTracker().getDeathMessage();
        if (player.level().getGameRules().get(net.minecraft.world.level.gamerules.GameRules.SHOW_DEATH_MESSAGES))
            group.ctx().server().getPlayerList().broadcastSystemMessage(message, false);
        down(player, battle, true);
        return false;
    }

    /** Put a player into the respawn timer at the team spawn as a spectator. */
    void down(ServerPlayer player, ArenaBattle battle, boolean diedHere) {
        player.stopRiding();
        player.setHealth(player.getMaxHealth());
        player.removeAllEffects();
        player.clearFire();
        player.resetFallDistance();
        player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        player.getFoodData().setFoodLevel(20);
        player.setGameMode(GameType.SPECTATOR);
        if (!diedHere || player.level() != battle.arena().get().level()) {
            var spawn = net.minecraft.world.phys.Vec3.atBottomCenterOf(battle.arena().get().layout().teamSpawn());
            player.teleportTo(battle.arena().get().level(), spawn.x, spawn.y + 2, spawn.z, Set.of(), player.getYRot(), 0, true);
            dev.mobarena.core.Teleports.clearSupport(player);
        }
        timers.put(player.getUUID(), group.ctx().server().getTickCount() + respawnTicks);
    }

    void tick(long now) {
        for (UUID id : timers.takeDue(now)) {
            var player = group.ctx().server().getPlayerList().getPlayer(id);
            var battle = player == null ? null : group.battle(group.ctx().team(id));
            if (player == null || battle == null || battle.arena().isEmpty()) continue;
            if (battle.state() == BattleFlow.State.ELIMINATED) continue;
            battle.placeAtSpawn(player);
            Titles.show(player, Component.literal("Respawned"), Component.empty(), 0, 20, 5);
        }
        if (now % 20 == 0) for (UUID id : timers.down()) {
            var player = group.ctx().server().getPlayerList().getPlayer(id);
            if (player != null) ActionBar.send(player, Component.literal("Respawn in " + (timers.remaining(id, now) + 19) / 20 + " s"));
        }
    }
}
