package dev.mobarena.world;

import dev.mobarena.core.*;
import dev.mobarena.team.TeamId;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.level.block.state.properties.StructureMode;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import java.nio.file.*;
import java.util.*;

final class ArenaWorldChecks {
    private ArenaWorldChecks() {}
    static void verify(GameTestHelper h, GameContext ctx, ServerPlayer player, Runnable next) {
        var game = GameManager.get();
        game.endPhaseNow();
        h.assertTrue(game.phase() == Phase.WAVE_CONFIG, "W7 config starts");
        var worlds = ctx.get(TeamWorlds.KEY);
        var world = worlds.get(TeamId.RED);
        var arena = world.arena().asLevel();
        for (var team : worlds.values()) {
            h.assertTrue(team.arena() != null, "W7 arena exists at config start");
            h.assertValueEqual(team.layout().mobSpawns().size(), 16, "W7 sixteen spawn markers");
            h.assertTrue(team.arena().asLevel().getBlockState(team.layout().teamSpawn().below()).is(Blocks.POLISHED_ANDESITE), "W7 spawn pad");
            h.assertTrue(team.arena().asLevel().getBlockState(new BlockPos(31, 70, 0)).is(Blocks.BARRIER), "W7 barrier wall");
            for (var pos : team.layout().mobSpawns()) {
                h.assertTrue(team.layout().bounds().contains(Vec3.atCenterOf(pos)), "W7 spawn inside bounds");
            }
        }
        // A queued portal process must never escape through the destination code.
        for (var block : new Block[]{Blocks.NETHER_PORTAL, Blocks.END_PORTAL}) {
            player.portalProcess = null;
            block.defaultBlockState().entityInside(arena, BlockPos.ZERO, player, InsideBlockEffectApplier.NOOP, false);
            h.assertTrue(player.portalProcess == null, "W5 arena portal stays inactive");
            var portal = (net.minecraft.world.level.block.Portal) block;
            h.assertTrue(portal.getPortalDestination(arena, player, BlockPos.ZERO) == null, "W5 queued arena portal stays blocked");
        }
        var nether = world.nether().asLevel();
        GameLevels.remove(nether);
        try {
            h.assertTrue(((NetherPortalBlock) Blocks.NETHER_PORTAL).getPortalDestination(nether, player, BlockPos.ZERO) == null,
                    "Removed prep level cannot escape to vanilla Nether");
            h.assertTrue(((EndPortalBlock) Blocks.END_PORTAL).getPortalDestination(nether, player, BlockPos.ZERO) == null,
                    "Removed prep level cannot escape to vanilla End");
        } finally { GameLevels.register(TeamId.RED, WorldKind.PREP_NETHER, nether); }
        var dropPos = new BlockPos(5, 64, 5);
        arena.setBlockAndUpdate(dropPos, Blocks.OAK_LOG.defaultBlockState());
        arena.destroyBlock(dropPos, true);
        h.assertTrue(!arena.getEntities(EntityTypes.ITEM, new net.minecraft.world.phys.AABB(dropPos).inflate(3),
                item -> item.getItem().is(net.minecraft.world.item.Items.OAK_LOG)).isEmpty(), "Arena block drop stays normal");
        template(h, arena);
        new ArenaWorldManager().onEnter(Phase.ARENA, ctx);
        world.arena().setTickWhenEmpty(true);
        var rain = ctx.server().getWeatherData();
        boolean raining = rain.isRaining();
        rain.setRaining(true);
        arena.setRainLevel(1);
        h.assertFalse(arena.canHaveWeather(), "W8 arena cannot have weather");
        h.assertFalse(arena.isRaining(), "W8 global rain does not reach arena");
        long clock = arena.getOverworldClockTime();
        h.assertValueEqual(clock, 6000L, "W8 arena starts at noon");
        h.setTime(6000);
        Zombie zombie = EntityTypes.ZOMBIE.create(arena, EntitySpawnReason.COMMAND);
        zombie.setPos(0.5, 64, 0.5);
        zombie.setPersistenceRequired();
        arena.addFreshEntity(zombie);
        var controlLevel = h.getLevel();
        var controlPos = h.absolutePos(new BlockPos(2, 2, 2));
        controlLevel.setBlockAndUpdate(controlPos.below(), Blocks.STONE.defaultBlockState());
        Zombie control = EntityTypes.ZOMBIE.create(controlLevel, EntitySpawnReason.COMMAND);
        control.setPos(Vec3.atBottomCenterOf(controlPos));
        control.setPersistenceRequired();
        controlLevel.addFreshEntity(control);
        h.runBeforeTestEnd(() -> { rain.setRaining(raining); zombie.discard(); control.discard(); });
        // No rain for the control, so sunlight remains observable.
        rain.setRaining(false);
        controlLevel.setRainLevel(0);
        h.runAfterDelay(100, () -> {
            h.assertValueEqual(arena.getOverworldClockTime(), clock, "W8 arena clock remains fixed");
            h.assertFalse(zombie.isOnFire(), "W9 arena zombie does not burn");
            h.assertTrue(control.isOnFire(), "W9 lobby control burns in sunlight");
            zombie.discard(); control.discard(); rain.setRaining(raining);
            next.run();
        });
    }
    private static void template(GameTestHelper h, ServerLevel level) {
        Path file = null;
        try {
            BlockPos source = new BlockPos(80, 64, 0), destination = new BlockPos(96, 64, 0);
            for (int x = 0; x < 5; x++) for (int z = 0; z < 5; z++) {
                level.setBlockAndUpdate(source.offset(x, 0, z), Blocks.STONE.defaultBlockState());
            }
            marker(level, source.offset(2, 1, 2), "mobarena:team_spawn");
            for (int x : new int[]{0,4}) for (int z : new int[]{0,4}) marker(level, source.offset(x, 1, z), "mobarena:mob_spawn");
            var template = new StructureTemplate();
            template.fillFromWorld(level, source, new Vec3i(5, 3, 5), false, List.of());
            file = Files.createTempFile("mobarena-arena-", ".nbt");
            NbtIo.writeCompressed(template.save(new CompoundTag()), file);
            var layout = ArenaTemplate.paste(level, file, destination);
            h.assertValueEqual(layout.mobSpawns().size(), 4, "W10 template has four mob spawns");
            h.assertValueEqual(layout.teamSpawn(), destination.offset(2, 1, 2), "W10 team marker");
            h.assertTrue(level.getBlockState(layout.teamSpawn()).isAir(), "W10 marker is removed");
            for (var pos : layout.mobSpawns()) h.assertTrue(level.getBlockState(pos).isAir(), "W10 mob markers are removed");
            h.assertTrue(level.getBlockState(destination).is(Blocks.STONE), "W10 file structure is pasted");
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
        finally { if (file != null) try { Files.deleteIfExists(file); } catch (java.io.IOException e) { throw new IllegalStateException(e); } }
    }
    private static void marker(ServerLevel level, BlockPos pos, String metadata) {
        level.setBlockAndUpdate(pos, Blocks.STRUCTURE_BLOCK.defaultBlockState());
        var marker = (StructureBlockEntity) level.getBlockEntity(pos);
        marker.setMode(StructureMode.DATA);
        marker.setMetaData(metadata);
    }
}
