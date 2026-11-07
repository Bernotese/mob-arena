package dev.mobarena.team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.*;
import net.minecraft.network.chat.Component;
import java.util.*;
public final class TeamManager {
    private static final TeamManager INSTANCE = new TeamManager();
    private final Map<UUID, TeamId> assignments = new HashMap<>();
    private final Map<UUID, String> names = new HashMap<>();
    public static TeamManager get() { return INSTANCE; }
    public void initialize(MinecraftServer server) {
        assignments.clear(); names.clear();
        var scoreboard = server.getScoreboard();
        for (PlayerTeam team : List.copyOf(scoreboard.getPlayerTeams()))
            if (team.getName().startsWith("mobarena.")) scoreboard.removePlayerTeam(team);
    }
    public TeamId team(UUID player) { return assignments.get(player); }
    public Map<UUID, TeamId> assignments() { return Map.copyOf(assignments); }
    public void assign(ServerPlayer player, TeamId team) {
        assignments.put(player.getUUID(), team); names.put(player.getUUID(), player.getScoreboardName()); sync(player);
    }
    public void remove(ServerPlayer player) {
        assignments.remove(player.getUUID()); names.remove(player.getUUID());
        var scoreboard = player.level().getScoreboard();
        var existing = scoreboard.getPlayersTeam(player.getScoreboardName());
        if (existing != null && existing.getName().startsWith("mobarena.")) scoreboard.removePlayerFromTeam(player.getScoreboardName());
    }
    public boolean remove(MinecraftServer server, String name) {
        UUID id = names.entrySet().stream().filter(e -> e.getValue().equalsIgnoreCase(name)).map(Map.Entry::getKey).findFirst().orElse(null);
        if (id == null) return false;
        String scoreName = names.remove(id); assignments.remove(id);
        var scoreboard = server.getScoreboard(); var existing = scoreboard.getPlayersTeam(scoreName);
        if (existing != null && existing.getName().startsWith("mobarena.")) scoreboard.removePlayerFromTeam(scoreName);
        return true;
    }
    public void sync(ServerPlayer player) {
        TeamId team = team(player.getUUID());
        if (team == null) return;
        names.put(player.getUUID(), player.getScoreboardName());
        var scoreboard = player.level().getScoreboard();
        String name = "mobarena." + team.name().toLowerCase(Locale.ROOT);
        PlayerTeam vanilla = scoreboard.getPlayerTeam(name);
        if (vanilla == null) vanilla = scoreboard.addPlayerTeam(name);
        vanilla.setDisplayName(Component.literal(team.name()));
        vanilla.setColor(Optional.of(team == TeamId.RED ? TeamColor.RED : TeamColor.BLUE));
        vanilla.setAllowFriendlyFire(false);
        scoreboard.addPlayerToTeam(player.getScoreboardName(), vanilla);
    }
    public void randomize(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) remove(player);
        assignments.clear();
        Map<UUID, TeamId> shuffled = TeamShuffle.shuffle(server.getPlayerList().getPlayers().stream().map(ServerPlayer::getUUID).toList(), new Random());
        assignments.putAll(shuffled);
        server.getPlayerList().getPlayers().forEach(this::sync);
    }
    public void clear() { assignments.clear(); names.clear(); }
}
