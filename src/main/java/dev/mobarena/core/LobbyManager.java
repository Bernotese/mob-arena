package dev.mobarena.core;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import dev.mobarena.MobArena;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Lobby and wave holding area share the overworld platform. Server thread only. */
public final class LobbyManager implements PhaseListener {
    private record Spawn(double x, double y, double z, float yaw, float pitch) {}
    private record State(Spawn spawn, Set<UUID> pendingClear) {}
    private record ReturnPosition(ServerLevel level, double x, double y, double z, float yaw, float pitch) {}
    private final Map<UUID, ReturnPosition> returns = new HashMap<>();
    private final Set<UUID> pendingClear = new HashSet<>();
    private final List<Consumer<MinecraftServer>> resetHooks = new ArrayList<>();
    private final Map<Phase, List<BiConsumer<GameContext, ServerPlayer>>> rejoinHooks = new EnumMap<>(Phase.class);
    private final Path path;
    private Spawn spawn;

    public LobbyManager(Path path) {
        this.path = path;
        try {
            if (Files.exists(path)) {
                var json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                Gson gson = new Gson();
                if (json.has("spawn")) {
                    State state = gson.fromJson(json, State.class);
                    spawn = state.spawn;
                    if (state.pendingClear != null) pendingClear.addAll(state.pendingClear);
                } else spawn = gson.fromJson(json, Spawn.class); // Previous lobby file format.
            }
        } catch (IOException | RuntimeException e) { throw new IllegalStateException("Cannot load lobby spawn", e); }
    }

    public void registerResetHook(Consumer<MinecraftServer> hook) { resetHooks.add(Objects.requireNonNull(hook)); }
    public void registerRejoinHook(Phase phase, BiConsumer<GameContext, ServerPlayer> hook) {
        rejoinHooks.computeIfAbsent(phase, ignored -> new ArrayList<>()).add(Objects.requireNonNull(hook));
    }

    public void leave(ServerPlayer player) {
        GameManager game = GameManager.get(); GameContext ctx = game.context();
        if (!player.isDeadOrDying() && ctx != null && ctx.team(player.getUUID()) != null && game.phase() != Phase.WAVE_CONFIG)
            returns.put(player.getUUID(), new ReturnPosition(player.level(), player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot()));
    }

    public void join(ServerPlayer player) {
        GameManager game = GameManager.get(); GameContext ctx = game.context();
        if (pendingClear.contains(player.getUUID())) { resetPlayer(player); pendingClear.remove(player.getUUID()); save(); }
        if (ctx == null || ctx.team(player.getUUID()) == null) { sendToLobby(player, ctx != null); return; }
        if (game.phase() == Phase.WAVE_CONFIG) { sendToLobby(player, false); return; }
        ReturnPosition pos = returns.remove(player.getUUID());
        if (pos != null && player.level().getServer().getLevel(pos.level.dimension()) != null) {
            player.teleportTo(pos.level, pos.x, pos.y, pos.z, Set.of(), pos.yaw, pos.pitch, true);
            player.setGameMode(game.phase() == Phase.PREP ? GameType.SURVIVAL : GameType.SPECTATOR);
            Teleports.clearSupport(player);
            protect(player, player.isSpectator());
        } else {
            ServerLevel level = game.phase() == Phase.PREP ? ctx.prepLevel(ctx.team(player.getUUID())) : ctx.arenaLevel(ctx.team(player.getUUID()));
            if (level == null) sendToLobby(player, true);
            else {
                var data = level.getRespawnData();
                player.teleportTo(level, data.pos().getX() + 0.5, data.pos().getY(), data.pos().getZ() + 0.5, Set.of(), data.yaw(), data.pitch(), true);
                player.setGameMode(game.phase() == Phase.PREP ? GameType.SURVIVAL : GameType.SPECTATOR);
                Teleports.clearSupport(player);
                protect(player, player.isSpectator());
            }
        }
        for (var hook : List.copyOf(rejoinHooks.getOrDefault(game.phase(), List.of()))) {
            try { hook.accept(ctx, player); }
            catch (RuntimeException e) { MobArena.LOGGER.error("Player rejoin hook failed", e); }
        }
    }

    public void shutdown(MinecraftServer server) {
        GameContext ctx = GameManager.get().context();
        if (ctx != null) resetGamePlayers(ctx);
        runResetHooks(server);
        GameManager.get().shutdown(); returns.clear();
    }

    private void runResetHooks(MinecraftServer server) {
        for (var hook : List.copyOf(resetHooks)) {
            try { hook.accept(server); }
            catch (RuntimeException e) { MobArena.LOGGER.error("Game cleanup failed", e); }
        }
    }

    private void resetPlayer(ServerPlayer player) {
        player.closeContainer(); player.getInventory().clearContent(); player.removeAllEffects();
        player.setExperiencePoints(0); player.setExperienceLevels(0); player.totalExperience = 0;
        player.setRespawnPosition(null, false); sendToLobby(player, false);
    }

    private void resetGamePlayers(GameContext ctx) {
        pendingClear.addAll(ctx.teams().keySet());
        for (ServerPlayer player : ctx.server().getPlayerList().getPlayers()) {
            resetPlayer(player); pendingClear.remove(player.getUUID());
        }
        save();
    }

    public void prepare(MinecraftServer server) {
        if (spawn == null) {
            BlockPos pos = server.overworld().getRespawnData().pos();
            spawn = new Spawn(pos.getX() + 0.5, Math.max(pos.getY(), 80) + 3, pos.getZ() + 0.5, 0, 0);
            buildPlatform(server.overworld()); save();
        } else if (server.overworld().getBlockState(BlockPos.containing(spawn.x, spawn.y - 1, spawn.z)).isAir()) {
            buildPlatform(server.overworld());
        }
    }

    private void buildPlatform(ServerLevel level) {
        BlockPos center = BlockPos.containing(spawn.x, spawn.y - 1, spawn.z);
        for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++) {
            level.setBlockAndUpdate(center.offset(x, 0, z), Blocks.STONE.defaultBlockState());
            for (int y = 1; y <= 3; y++) level.setBlockAndUpdate(center.offset(x, y, z),
                (Math.abs(x) == 5 || Math.abs(z) == 5 ? Blocks.BARRIER : Blocks.AIR).defaultBlockState());
        }
    }

    public void setSpawn(ServerPlayer player) {
        if (player.level() != player.level().getServer().overworld()) throw new IllegalArgumentException("Set the lobby in the default overworld");
        spawn = new Spawn(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot()); save();
    }

    private void save() {
        try { Files.createDirectories(path.toAbsolutePath().getParent()); Files.writeString(path, new Gson().toJson(new State(spawn, pendingClear))); }
        catch (IOException e) { throw new IllegalStateException("Cannot save lobby spawn", e); }
    }

    public void sendToLobby(ServerPlayer player, boolean spectator) {
        prepare(player.level().getServer());
        var target = lobbyPosition(player);
        player.teleportTo(player.level().getServer().overworld(), target.x, target.y, target.z, Set.of(), spawn.yaw, spawn.pitch, true);
        player.setGameMode(spectator ? GameType.SPECTATOR : GameType.ADVENTURE);
        Teleports.clearSupport(player);
        protect(player, true);
    }

    /** Spread only across full floor faces with empty standing space; skip carpet and pressure plates. */
    private net.minecraft.world.phys.Vec3 lobbyPosition(ServerPlayer player) {
        var level = player.level().getServer().overworld();
        var center = new net.minecraft.world.phys.Vec3(spawn.x, spawn.y, spawn.z);
        for (int radius = 0; radius <= 3; radius++) {
            for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
                if (Math.max(Math.abs(x), Math.abs(z)) != radius) continue;
                var target = center.add(x, 0, z);
                var below = BlockPos.containing(target).below();
                if (!level.getBlockState(below).isFaceSturdy(level, below, net.minecraft.core.Direction.UP)) continue;
                var box = player.getBoundingBox().move(target.subtract(player.position()));
                if (!level.noCollision(player, box)) continue;
                boolean occupied = level.getServer().getPlayerList().getPlayers().stream().anyMatch(other ->
                        other != player && other.level() == level && other.position().distanceToSqr(target) < 0.81);
                if (!occupied) return target;
            }
        }
        return center;
    }

    private void protect(ServerPlayer player, boolean protect) {
        if (player.getAbilities().invulnerable != protect) { player.getAbilities().invulnerable = protect; player.onUpdateAbilities(); }
        if (protect) { player.setHealth(player.getMaxHealth()); player.getFoodData().setFoodLevel(20); player.getFoodData().setSaturation(5); player.clearFire(); }
    }

    public boolean protectedPlayer(ServerPlayer player) {
        GameManager game = GameManager.get();
        if (game.phase() == Phase.LOBBY || game.phase() == Phase.WAVE_CONFIG) return true;
        return game.context() == null || game.context().team(player.getUUID()) == null;
    }

    public boolean needsRescue(ServerPlayer player) {
        if (!protectedPlayer(player)) return false;
        if ((player.isCreative() || player.isSpectator()) && player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) return false;
        if (player.getY() < player.level().getMinY()) return true;
        if (spawn == null || player.level() != player.level().getServer().overworld()) return false;
        double dx = player.getX() - spawn.x, dz = player.getZ() - spawn.z;
        return dx * dx + dz * dz <= 64 && player.getY() < spawn.y - 10;
    }

    public void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) if (protectedPlayer(player)) {
            protect(player, true);
            if (needsRescue(player)) sendToLobby(player, player.isSpectator());
        }
    }

    @Override public void onEnter(Phase phase, GameContext ctx) {
        if (ctx.server() == null) return;
        if (phase == Phase.ARENA) returns.clear();
        if (phase == Phase.LOBBY) {
            returns.clear(); resetGamePlayers(ctx); runResetHooks(ctx.server()); return;
        }
        for (ServerPlayer player : ctx.server().getPlayerList().getPlayers()) {
            if (ctx.team(player.getUUID()) != null) {
                if (phase == Phase.WAVE_CONFIG) { player.closeContainer(); sendToLobby(player, false); }
                else if (phase == Phase.PREP) {
                    player.closeContainer(); player.getInventory().clearContent(); player.setGameMode(GameType.SURVIVAL);
                    protect(player, false); player.setHealth(player.getMaxHealth()); player.getFoodData().setFoodLevel(20);
                } else if (phase == Phase.ARENA || phase == Phase.SUDDEN_DEATH) { player.setGameMode(GameType.SURVIVAL); protect(player, false); }
            } else sendToLobby(player, true);
        }
    }
}
