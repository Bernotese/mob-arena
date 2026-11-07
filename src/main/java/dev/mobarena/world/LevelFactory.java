package dev.mobarena.world;

import dev.mobarena.team.TeamId;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.gamerules.GameRules;
import xyz.nucleoid.fantasy.*;
import xyz.nucleoid.fantasy.util.VoidChunkGenerator;
import java.util.Locale;

public final class LevelFactory {
    private static long nextLevelId;
    private LevelFactory() {}
    public static Identifier id(TeamId team, WorldKind kind) {
        String suffix = switch (kind) {
            case PREP_OVERWORLD -> "overworld";
            case PREP_NETHER -> "nether";
            case PREP_END -> "end";
            case ARENA -> "arena";
        };
        return Identifier.fromNamespaceAndPath("mobarena", team.name().toLowerCase(Locale.ROOT) + "_" + suffix);
    }
    public static RuntimeLevelConfig config(MinecraftServer server, long seed, WorldKind kind) {
        var config = new RuntimeLevelConfig().setSeed(seed)
                .setMirrorOverworldGameRules(false).setMirrorOverworldDifficulty(true)
                .setGameRule(GameRules.KEEP_INVENTORY, true).setShouldTickTime(true);
        var dims = server.getWorldGenSettings().dimensions();
        return switch (kind) {
            case PREP_OVERWORLD -> config.setDimensionType(BuiltinDimensionTypes.OVERWORLD)
                    .setGenerator(dims.get(LevelStem.OVERWORLD).orElseThrow().generator());
            case PREP_NETHER -> config.setDimensionType(BuiltinDimensionTypes.NETHER)
                    .setGenerator(dims.get(LevelStem.NETHER).orElseThrow().generator());
            case PREP_END -> config.setDimensionType(BuiltinDimensionTypes.END)
                    .setGenerator(dims.get(LevelStem.END).orElseThrow().generator());
            case ARENA -> config.setDimensionType(BuiltinDimensionTypes.OVERWORLD)
                    .setGenerator(new VoidChunkGenerator(server)).setGameRule(GameRules.MOB_GRIEFING, true)
                    .setGameRule(GameRules.SPAWN_MOBS, false).setGameRule(GameRules.ADVANCE_WEATHER, false)
                    .setShouldTickTime(false).setClockTime(WorldClocks.OVERWORLD, 6000);
        };
    }
    public static RuntimeLevelHandle open(MinecraftServer server, long seed, TeamId team, WorldKind kind) {
        var handle = Fantasy.get(server).openTemporaryLevel(Identifier.fromNamespaceAndPath("mobarena",
                id(team, kind).getPath() + "_" + Long.toUnsignedString(++nextLevelId)), config(server, seed, kind));
        try { GameLevels.register(team, kind, handle.asLevel()); }
        catch (RuntimeException e) { handle.delete(); throw e; }
        WorldCleanup.track(handle);
        return handle;
    }
}
