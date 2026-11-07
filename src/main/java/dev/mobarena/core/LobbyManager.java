package dev.mobarena.core;
import com.google.gson.Gson;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;
/** Lobby and wave holding area share the overworld platform. */
public final class LobbyManager implements PhaseListener {
    private record Spawn(double x, double y, double z, float yaw, float pitch) {}
    private record ReturnPosition(ServerLevel level, double x, double y, double z, float yaw, float pitch) {}
    private final Map<UUID, ReturnPosition> returns = new HashMap<>();
    private final List<java.util.function.Consumer<MinecraftServer>> resetHooks = new ArrayList<>();
    public void registerResetHook(java.util.function.Consumer<MinecraftServer> hook) { resetHooks.add(hook); }
    public void leave(ServerPlayer player) {
        GameContext ctx = GameManager.get().context();
        if (ctx != null && ctx.team(player.getUUID()) != null) returns.put(player.getUUID(),
            new ReturnPosition(player.level(), player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot()));
    }
    public void join(ServerPlayer player) {
        GameManager game = GameManager.get(); GameContext ctx = game.context();
        if (ctx == null || ctx.team(player.getUUID()) == null) { sendToLobby(player, ctx != null); return; }
        if (game.phase() == Phase.WAVE_CONFIG) { sendToLobby(player, false); return; }
        ReturnPosition pos = returns.remove(player.getUUID());
        if (pos != null && player.level().getServer().getLevel(pos.level.dimension()) != null) {
            player.teleportTo(pos.level, pos.x, pos.y, pos.z, Set.of(), pos.yaw, pos.pitch, true);
            player.setGameMode(game.phase() == Phase.PREP ? GameType.SURVIVAL : GameType.SPECTATOR);
            protect(player, false);
        } else if (game.phase() == Phase.PREP && ctx.prepLevel(ctx.team(player.getUUID())) != null) {
            ServerLevel level = ctx.prepLevel(ctx.team(player.getUUID())); var data = level.getRespawnData();
            player.teleportTo(level, data.pos().getX() + 0.5, data.pos().getY(), data.pos().getZ() + 0.5, Set.of(), data.yaw(), data.pitch(), true);
            player.setGameMode(GameType.SURVIVAL); protect(player, false);
        } else sendToLobby(player, true);
    }
    public void shutdown(MinecraftServer server) {
        GameManager.get().shutdown(); returns.clear(); runResetHooks(server);
    }
    private void runResetHooks(MinecraftServer server) {
        for (var hook : List.copyOf(resetHooks)) {
            try { hook.accept(server); }
            catch (RuntimeException e) { dev.mobarena.MobArena.LOGGER.error("Game cleanup failed", e); }
        }
    }
    private final Path path;
    private Spawn spawn;
    public LobbyManager(Path path) {
        this.path = path;
        try { if (Files.exists(path)) spawn = new Gson().fromJson(Files.readString(path), Spawn.class); }
        catch (IOException | RuntimeException e) { throw new IllegalStateException("Cannot load lobby spawn", e); }
    }
    public void prepare(MinecraftServer server) {
        if (spawn == null) {
            BlockPos pos = server.overworld().getRespawnData().pos();
            spawn = new Spawn(pos.getX() + 0.5, Math.max(pos.getY(), 80) + 3, pos.getZ() + 0.5, 0, 0);
            buildPlatform(server.overworld()); save();
        }
    }
    private void buildPlatform(ServerLevel level) {
        BlockPos center = BlockPos.containing(spawn.x, spawn.y - 1, spawn.z);
        for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++) {
            level.setBlockAndUpdate(center.offset(x, 0, z), Blocks.STONE.defaultBlockState());
            for (int y = 1; y <= 3; y++) level.setBlockAndUpdate(center.offset(x, y, z), Blocks.AIR.defaultBlockState());
            if (Math.abs(x) == 5 || Math.abs(z) == 5) level.setBlockAndUpdate(center.offset(x, 1, z), Blocks.BARRIER.defaultBlockState());
        }
    }
    public void setSpawn(ServerPlayer player) {
        if (player.level() != player.level().getServer().overworld()) throw new IllegalArgumentException("Set the lobby in the default overworld");
        spawn = new Spawn(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot()); save();
    }
    private void save() {
        try { Files.createDirectories(path.getParent()); Files.writeString(path, new Gson().toJson(spawn)); }
        catch (IOException e) { throw new IllegalStateException("Cannot save lobby spawn", e); }
    }
    public void sendToLobby(ServerPlayer player, boolean spectator) {
        prepare(player.level().getServer());
        player.teleportTo(player.level().getServer().overworld(), spawn.x, spawn.y, spawn.z, Set.of(), spawn.yaw, spawn.pitch, true);
        player.setGameMode(spectator ? GameType.SPECTATOR : GameType.ADVENTURE);
        protect(player, true);
    }
    private void protect(ServerPlayer player, boolean protect) {
        player.getAbilities().invulnerable = protect; player.onUpdateAbilities();
        if (protect) { player.setHealth(player.getMaxHealth()); player.getFoodData().setFoodLevel(20); player.getFoodData().setSaturation(5); player.clearFire(); }
    }
    public boolean protectedPlayer(ServerPlayer player) {
        GameManager game = GameManager.get();
        if (game.phase() == Phase.LOBBY || game.phase() == Phase.WAVE_CONFIG) return true;
        return game.context() == null || game.context().team(player.getUUID()) == null;
    }
    public void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) if (protectedPlayer(player)) {
            protect(player, true);
            if (player.getY() < server.overworld().getMinY() || (player.level() == server.overworld() && player.getY() < spawn.y - 10)) sendToLobby(player, player.isSpectator());
        }
    }
    @Override public void onEnter(Phase phase, GameContext ctx) {
        if (ctx.server() == null) return;
        if (phase == Phase.LOBBY) {
            returns.clear();
            runResetHooks(ctx.server());
        }
        for (ServerPlayer player : ctx.server().getPlayerList().getPlayers()) {
            if (phase == Phase.LOBBY) {
                player.closeContainer(); player.getInventory().clearContent(); player.removeAllEffects();
                player.setExperiencePoints(0); player.setExperienceLevels(0); player.totalExperience = 0;
                player.setRespawnPosition(null, false); sendToLobby(player, false);
            }
            else if (ctx.team(player.getUUID()) != null) {
                if (phase == Phase.WAVE_CONFIG) { player.closeContainer(); sendToLobby(player, false); }
                else if (phase == Phase.PREP) {
                    player.closeContainer(); player.getInventory().clearContent(); player.setGameMode(GameType.SURVIVAL);
                    protect(player, false); player.setHealth(player.getMaxHealth()); player.getFoodData().setFoodLevel(20);
                } else if (phase == Phase.ARENA || phase == Phase.SUDDEN_DEATH) { player.setGameMode(GameType.SURVIVAL); protect(player, false); }
            } else sendToLobby(player, true);
        }
    }
}
