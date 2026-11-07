package dev.mobarena.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import dev.mobarena.config.ConfigStore;
import dev.mobarena.core.*;
import dev.mobarena.team.*;
import java.util.*;
import java.util.function.BiFunction;
import static net.minecraft.commands.Commands.*;

public final class CoreCommands {
    private static BiFunction<GameContext, TeamId, String> progress = (ctx, team) -> "No arena progress.";
    private CoreCommands() {}
    public static void setProgressProvider(BiFunction<GameContext, TeamId, String> provider) { progress = Objects.requireNonNull(provider); }
    private static boolean admin(CommandSourceStack source) { return hasPermission(LEVEL_GAMEMASTERS).test(source); }
    private static boolean lobby(CommandSourceStack source) {
        if (GameManager.get().phase() == Phase.LOBBY) return true;
        source.sendFailure(Component.literal("Change teams only in the lobby.")); return false;
    }
    private static int reply(CommandSourceStack source, String text) {
        source.sendSuccess(() -> Component.literal(text), false); return 1;
    }
    public static void register(ConfigStore config, LobbyManager lobby) {
        CommandRegistry.register(root -> {
            root.then(literal("start").requires(CoreCommands::admin).executes(c -> {
                var server = c.getSource().getServer();
                boolean started = GameManager.get().start(server, TeamManager.get().assignments(),
                    new HashSet<>(server.getPlayerList().getPlayers().stream().map(ServerPlayer::getUUID).toList()));
                if (!started) { c.getSource().sendFailure(Component.literal("Start requires the lobby and an online player in each team.")); return 0; }
                return reply(c.getSource(), "Game started.");
            }));
            root.then(literal("stop").requires(CoreCommands::admin).executes(c -> { GameManager.get().stop(); return reply(c.getSource(), "Game stopped."); }));
            root.then(literal("skip").requires(CoreCommands::admin).executes(c -> { GameManager.get().endPhaseNow(); return reply(c.getSource(), "Phase ended."); }));
            root.then(literal("status").executes(c -> {
                GameManager game = GameManager.get(); GameContext ctx = game.context();
                TeamId team = c.getSource().getPlayer() == null ? null : (ctx == null ? TeamManager.get().team(c.getSource().getPlayer().getUUID()) : ctx.team(c.getSource().getPlayer().getUUID()));
                String timer = ctx == null || ctx.timer().remainingTicks() < 0 ? "No phase time limit." : "Remaining: " + ((ctx.timer().remainingTicks() + 19) / 20) + " seconds.";
                return reply(c.getSource(), "Phase: " + game.phase() + ". " + timer + (team == null ? "" : " Team: " + team + ". " + (ctx == null ? "" : progress.apply(ctx, team))));
            }));
            root.then(literal("setlobby").requires(CoreCommands::admin).executes(c -> {
                try { lobby.setSpawn(c.getSource().getPlayerOrException()); return reply(c.getSource(), "Lobby spawn saved."); }
                catch (IllegalArgumentException | IllegalStateException e) { c.getSource().sendFailure(Component.literal(e.getMessage())); return 0; }
            }));
            root.then(literal("team")
                .then(literal("list").executes(c -> {
                    for (TeamId team : TeamId.values()) {
                        List<String> names = TeamManager.get().assignments().entrySet().stream().filter(e -> e.getValue() == team).map(e -> {
                            var player = c.getSource().getServer().getPlayerList().getPlayer(e.getKey());
                            return player == null ? e.getKey().toString() : player.getName().getString();
                        }).sorted().toList();
                        reply(c.getSource(), team + ": " + String.join(", ", names));
                    }
                    return 1;
                }))
                .then(literal("random").requires(CoreCommands::admin).executes(c -> {
                    if (!lobby(c.getSource())) return 0;
                    TeamManager.get().randomize(c.getSource().getServer()); return reply(c.getSource(), "Teams assigned.");
                }))
                .then(literal("remove").requires(CoreCommands::admin).then(argument("player", EntityArgument.player()).executes(c -> {
                    if (!lobby(c.getSource())) return 0;
                    TeamManager.get().remove(EntityArgument.getPlayer(c, "player")); return reply(c.getSource(), "Player removed from team.");
                })))
                .then(literal("assign").requires(CoreCommands::admin).then(argument("player", EntityArgument.player())
                    .then(literal("red").executes(c -> assign(c, TeamId.RED)))
                    .then(literal("blue").executes(c -> assign(c, TeamId.BLUE))))));
            root.then(literal("config").requires(CoreCommands::admin)
                .then(literal("get").then(argument("key", StringArgumentType.word()).executes(c -> {
                    try { return reply(c.getSource(), StringArgumentType.getString(c, "key") + " = " + config.snapshot().get(StringArgumentType.getString(c, "key"))); }
                    catch (IllegalArgumentException e) { c.getSource().sendFailure(Component.literal(e.getMessage())); return 0; }
                })))
                .then(literal("set").then(argument("key", StringArgumentType.word()).then(argument("value", StringArgumentType.greedyString()).executes(c -> {
                    try { config.set(StringArgumentType.getString(c, "key"), StringArgumentType.getString(c, "value")); return reply(c.getSource(), "Config saved. Changes apply at the next phase."); }
                    catch (Exception e) { c.getSource().sendFailure(Component.literal("Cannot save config: " + e.getMessage())); return 0; }
                }))))
                .then(literal("reset").executes(c -> {
                    try { config.reset(); return reply(c.getSource(), "Default config saved."); }
                    catch (Exception e) { c.getSource().sendFailure(Component.literal("Cannot save config: " + e.getMessage())); return 0; }
                })));
        });
    }
    private static int assign(CommandContext<CommandSourceStack> c, TeamId team) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (!lobby(c.getSource())) return 0;
        TeamManager.get().assign(EntityArgument.getPlayer(c, "player"), team); return reply(c.getSource(), "Player assigned to " + team + ".");
    }
}
