package dev.mobarena.command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import java.util.*;
import java.util.function.Consumer;
/** Modules register subcommands before the server command dispatcher is built. */
public final class CommandRegistry {
    private static final List<Consumer<LiteralArgumentBuilder<CommandSourceStack>>> MODULES = new ArrayList<>();
    private CommandRegistry() {}
    public static void register(Consumer<LiteralArgumentBuilder<CommandSourceStack>> commands) { MODULES.add(commands); }
    public static void initialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> {
            var root = Commands.literal("mobarena");
            MODULES.forEach(module -> module.accept(root));
            var node = dispatcher.register(root);
            dispatcher.register(Commands.literal("ma").redirect(node));
        });
    }
}
