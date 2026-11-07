package dev.mobarena.world;

import dev.mobarena.MobArena;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import net.minecraft.world.phys.AABB;
import java.nio.file.*;
import java.util.*;

public final class ArenaTemplate {
    private ArenaTemplate() {}
    public static ArenaLayout load(ServerLevel level, String name, long seed) {
        if (!name.matches("[a-zA-Z0-9_-]+")) {
            MobArena.LOGGER.error("Invalid arena template name: {}", name);
            return DefaultArenaBuilder.build(level, seed);
        }
        Path path = FabricLoader.getInstance().getConfigDir().resolve("mob-arena/arenas").resolve(name + ".nbt");
        if (!Files.exists(path)) return DefaultArenaBuilder.build(level, seed);
        try { return paste(level, path, new BlockPos(0, 64, 0)); }
        catch (Exception e) {
            MobArena.LOGGER.error("Cannot load arena template {}; using default", path, e);
            return DefaultArenaBuilder.build(level, seed);
        }
    }
    public static ArenaLayout paste(ServerLevel level, Path path, BlockPos origin) throws java.io.IOException {
        var tag = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
        tag = DataFixTypes.STRUCTURE.updateToCurrentVersion(level.getServer().getFixerUpper(), tag, NbtUtils.getDataVersion(tag, 500));
        var template = new StructureTemplate();
        template.load(level.registryAccess().lookupOrThrow(Registries.BLOCK), tag);
        return paste(level, template, origin);
    }
    public static ArenaLayout paste(ServerLevel level, StructureTemplate template, BlockPos origin) {
        var settings = new StructurePlaceSettings().setIgnoreEntities(false);
        List<BlockPos> teams = new ArrayList<>(), mobs = new ArrayList<>(), water = new ArrayList<>();
        var markers = template.filterBlocks(origin, settings, Blocks.STRUCTURE_BLOCK);
        for (var marker : markers) {
            var nbt = marker.nbt();
            if (nbt == null || !"DATA".equals(nbt.getStringOr("mode", ""))) continue;
            switch (nbt.getStringOr("metadata", "")) {
                case "mobarena:team_spawn" -> teams.add(marker.pos());
                case "mobarena:mob_spawn" -> mobs.add(marker.pos());
                case "mobarena:water_spawn" -> water.add(marker.pos());
                default -> { }
            }
        }
        var size = template.getSize();
        if (teams.size() != 1 || mobs.isEmpty() || size.getX() <= 0 || size.getY() <= 0 || size.getZ() <= 0) {
            throw new IllegalArgumentException("Arena needs one team spawn and at least one mob spawn");
        }
        if (!template.placeInWorld(level, origin, origin, settings, level.getRandom(), Block.UPDATE_CLIENTS)) {
            throw new IllegalStateException("Arena template placement failed");
        }
        for (var marker : markers) level.setBlock(marker.pos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        return new ArenaLayout(teams.getFirst(), mobs, water,
                new AABB(origin.getX(), origin.getY(), origin.getZ(),
                        origin.getX() + size.getX(), origin.getY() + size.getY(), origin.getZ() + size.getZ()));
    }
}
