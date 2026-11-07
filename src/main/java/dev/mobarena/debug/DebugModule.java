package dev.mobarena.debug;

import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.mobarena.command.CommandRegistry;
import dev.mobarena.team.TeamId;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import java.util.Locale;
import static net.minecraft.commands.Commands.*;

public final class DebugModule {
    private DebugModule() {}
    @FunctionalInterface private interface Action { void run(CommandContext<CommandSourceStack> c) throws CommandSyntaxException; }
    private static int run(CommandContext<CommandSourceStack> c, Action action) throws CommandSyntaxException {
        try { action.run(c); c.getSource().sendSuccess(() -> Component.literal("Debug action complete."), false); return 1; }
        catch (IllegalArgumentException | IllegalStateException | ArithmeticException e) {
            c.getSource().sendFailure(Component.literal(e.getMessage())); return 0;
        }
    }
    private static String str(CommandContext<CommandSourceStack> c, String name) { return StringArgumentType.getString(c, name); }
    private static TeamId team(CommandContext<CommandSourceStack> c) { return TeamId.valueOf(str(c, "team").toUpperCase(Locale.ROOT)); }
    public static void init() {
        if (!DebugDriver.enabled()) return;
        CommandRegistry.register(root -> root.then(literal("debug")
            .requires(s -> DebugDriver.enabled() && hasPermission(LEVEL_OWNERS).test(s))
            .then(literal("kill").then(argument("player", EntityArgument.player()).then(argument("entity", IdentifierArgument.id())
                .executes(c -> run(c, x -> DebugDriver.kill(EntityArgument.getPlayer(x, "player"), IdentifierArgument.getId(x, "entity").toString(), 1)))
                .then(argument("n", IntegerArgumentType.integer(1)).executes(c -> run(c, x -> DebugDriver.kill(EntityArgument.getPlayer(x, "player"), IdentifierArgument.getId(x, "entity").toString(), IntegerArgumentType.getInteger(x, "n"))))))))
            .then(literal("pool").then(literal("add").then(argument("team", StringArgumentType.word()).then(argument("entity", IdentifierArgument.id())
                .then(argument("n", IntegerArgumentType.integer(1)).executes(c -> run(c, x -> DebugDriver.poolAdd(team(x), IdentifierArgument.getId(x, "entity").toString(), IntegerArgumentType.getInteger(x, "n")))))))))
            .then(literal("waves").then(argument("team", StringArgumentType.word()).then(argument("w1 w2 w3", StringArgumentType.greedyString())
                .executes(c -> run(c, x -> {
                    String[] waves = str(x, "w1 w2 w3").trim().split("\\s+");
                    if (waves.length != 3) throw new IllegalArgumentException("Supply three wave specs; use - for an empty wave");
                    DebugDriver.waves(team(x), waves[0], waves[1], waves[2]);
                })))))
            .then(literal("clearwave").then(argument("team", StringArgumentType.word()).executes(c -> run(c, x -> DebugDriver.clearwave(team(x))))))
            .then(literal("down").then(argument("player", EntityArgument.player()).executes(c -> run(c, x -> DebugDriver.down(EntityArgument.getPlayer(x, "player"))))))
            .then(literal("timer").then(argument("seconds", IntegerArgumentType.integer(0)).executes(c -> run(c, x -> DebugDriver.timer(IntegerArgumentType.getInteger(x, "seconds"))))))
            .then(literal("state").executes(c -> run(c, x -> {
                var state = DebugDriver.state(x.getSource().getServer());
                x.getSource().sendSuccess(() -> Component.literal("MOBARENA_STATE " + state), false);
            })))));
    }
}
