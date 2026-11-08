package dev.mobarena.result;

import dev.mobarena.core.GameContext;
import dev.mobarena.core.Phase;
import dev.mobarena.core.PhaseListener;
import dev.mobarena.core.ResultHooks;
import dev.mobarena.hud.HudOverrides;
import dev.mobarena.hud.TimeFormat;
import dev.mobarena.hud.Titles;
import dev.mobarena.team.TeamId;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;

/**
 * END phase: title, HUD result, chat summary and fireworks. GameManager returns to LOBBY when the
 * END timer ({@code endScreenSeconds}) ends. T2 clears inventories in {@code LOBBY.onEnter}.
 */
final class EndSequence implements PhaseListener {
    static final int RESULT_TITLE_DELAY_TICKS = 40;
    /** Fireworks every second for the first 5 s. */
    static final int FIREWORK_INTERVAL = 20, FIREWORK_TICKS = 100;
    private static final ResultKeys.GameResult NO_RESULT = new ResultKeys.GameResult(Optional.empty(), "Ended by an admin");

    @Override public void onEnter(Phase phase, GameContext ctx) {
        if (phase != Phase.END) return;
        var result = result(ctx);
        // Boss bar and title use the same text ("Game over" after an admin skip, not "Draw").
        if (result == NO_RESULT) HudOverrides.of(ctx).setResultText(Component.literal("Game over"));
        else HudOverrides.of(ctx).setResult(result.winner().orElse(null));
        for (Component line : summary(ctx, result)) ctx.server().getPlayerList().broadcastSystemMessage(line, false);
    }

    @Override public void onTick(Phase phase, GameContext ctx) {
        if (phase != Phase.END) return;
        long elapsed = ctx.timer().elapsedTicks();
        if (elapsed == RESULT_TITLE_DELAY_TICKS) announce(ctx, result(ctx));
        if (elapsed > FIREWORK_TICKS || (elapsed - 1) % FIREWORK_INTERVAL != 0) return;
        result(ctx).winner().ifPresent(team -> {
            for (Map.Entry<UUID, TeamId> entry : ctx.teams().entrySet()) {
                if (entry.getValue() != team) continue;
                ServerPlayer player = ctx.server().getPlayerList().getPlayer(entry.getKey());
                if (player != null && !player.isRemoved()) launchFirework(player, team);
            }
        });
    }

    private static void announce(GameContext ctx, ResultKeys.GameResult result) {
        Titles.showAll(List.copyOf(ctx.server().getPlayerList().getPlayers()),
                title(result), Component.literal(result.reason()), 10, 70, 20);
    }

    static ResultKeys.GameResult result(GameContext ctx) {
        var result = ctx.get(ResultKeys.RESULT);
        return result == null ? NO_RESULT : result;
    }

    static Component title(ResultKeys.GameResult result) {
        return result.winner()
                .<Component>map(team -> Component.literal("Team " + team + " wins").withStyle(color(team), ChatFormatting.BOLD))
                .orElse(Component.literal(result.reason().equals(NO_RESULT.reason()) ? "Game over" : "Draw")
                        .withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
    }

    static ChatFormatting color(TeamId team) { return team == TeamId.RED ? ChatFormatting.RED : ChatFormatting.BLUE; }

    static List<Component> summary(GameContext ctx, ResultKeys.GameResult result) {
        var lines = new java.util.ArrayList<Component>();
        lines.add(Component.literal("=== Game result: ").append(title(result)).append(" – " + result.reason() + " ==="));
        if (ctx.get(ResultHooks.KEY) instanceof ResultTracker tracker) {
            for (TeamId team : TeamId.values()) {
                MutableComponent line = Component.literal(team.name()).withStyle(color(team))
                        .append(Component.literal(": arena " + describe(tracker.arenaOutcome(team))).withStyle(ChatFormatting.WHITE));
                tracker.suddenDeathOutcome(team).ifPresent(outcome ->
                        line.append(Component.literal(", sudden death " + describe(outcome)).withStyle(ChatFormatting.WHITE)));
                lines.add(line);
            }
        }
        return lines;
    }

    static String describe(Outcome outcome) {
        return switch (outcome) {
            case Outcome.Running running -> "not finished";
            case Outcome.Finished finished -> "all waves cleared in " + TimeFormat.mmss(finished.ticks());
            case Outcome.Eliminated eliminated -> "eliminated: " + eliminated.progress().clearedWaves() + " waves cleared, "
                    + eliminated.progress().killsInCurrentWave() + " kills in the last wave, "
                    + TimeFormat.mmss(eliminated.progress().ticks());
        };
    }

    private static void launchFirework(ServerPlayer player, TeamId team) {
        int color = (team == TeamId.RED ? DyeColor.RED : DyeColor.BLUE).getFireworkColor();
        var explosion = new FireworkExplosion(FireworkExplosion.Shape.LARGE_BALL, IntList.of(color),
                IntList.of(DyeColor.WHITE.getFireworkColor()), true, true);
        var stack = new ItemStack(Items.FIREWORK_ROCKET);
        stack.set(DataComponents.FIREWORKS, new Fireworks(1, List.of(explosion)));
        var level = player.level();
        level.addFreshEntity(new FireworkRocketEntity(level, player.getX(), player.getY() + 2, player.getZ(), stack));
    }
}
