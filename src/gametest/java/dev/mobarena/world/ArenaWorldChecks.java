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
        zombie.setPos(0.5, 100, 0.5); // Above the arena roof, under open sky.
        zombie.setPersistenceRequired();
        zombie.setNoAi(true);
        zombie.setNoGravity(true);
        arena.addFreshEntity(zombie);
        var controlLevel = h.getLevel();
        var controlPos = h.absolutePos(new BlockPos(2, 2, 2));
        h.runBeforeTestEnd(() -> { rain.setRaining(raining); zombie.discard(); });
        rain.setRaining(false);
        controlLevel.setRainLevel(0);
        h.runAfterDelay(100, () -> {
            h.assertValueEqual(arena.getOverworldClockTime(), clock, "W8 arena clock remains fixed");
            h.assertTrue(zombie.tickCount > 80, "W9 arena zombie ticks");
            var attribute = net.minecraft.world.attribute.EnvironmentAttributes.MONSTERS_BURN;
            h.assertTrue(arena.environmentAttributes().getValue(attribute, zombie.position()),
                    "W9 arena retains the overworld daylight attribute");
            h.assertTrue(controlLevel.environmentAttributes().getValue(attribute, Vec3.atBottomCenterOf(controlPos)),
                    "W9 lobby has the daylight attribute");
            h.assertTrue(zombie.getLightLevelDependentMagicValue() > 0.5F, "W9 arena zombie has full daylight");
            h.assertTrue(arena.canSeeSky(BlockPos.containing(zombie.getX(), zombie.getEyeY(), zombie.getZ())),
                    "W9 arena zombie sees the sky");
            h.assertFalse(zombie.isInWaterOrRain(), "W9 arena zombie is dry");
            var prepWorld = ctx.get(TeamWorlds.KEY).get(TeamId.RED);
            verifySunBurnMixin(h, zombie, controlLevel, controlPos, prepWorld.overworld().asLevel(), prepWorld.prepSpawn());
            h.assertFalse(zombie.isOnFire(), "W9 arena zombie does not burn");
            zombie.discard(); rain.setRaining(raining);
            next.run();
        });
    }
    private static void verifySunBurnMixin(GameTestHelper h, Zombie zombie, ServerLevel lobby, BlockPos position,
                                           ServerLevel prep, BlockPos prepPosition) {
        try {
            var sunBurn = Mob.class.getDeclaredMethod("isSunBurnTick");
            sunBurn.setAccessible(true);
            for (int attempt = 0; attempt < 1000; attempt++) {
                h.assertFalse((boolean) sunBurn.invoke(zombie), "W9 arena blocks every sunlight check");
            }
            // Test the injected callback directly, without vanilla's random sunlight chance.
            var callback = Arrays.stream(Mob.class.getDeclaredMethods())
                    .filter(method -> method.getName().contains("mobarena$noSunBurn")).findFirst().orElseThrow();
            callback.setAccessible(true);
            var arenaResult = new org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean>(
                    "isSunBurnTick", true, true);
            callback.invoke(zombie, arenaResult);
            h.assertTrue(arenaResult.isCancelled(), "W9 arena callback cancels vanilla");
            h.assertFalse(arenaResult.getReturnValue(), "W9 arena callback returns false");
            var control = (Zombie) zombie.teleport(new net.minecraft.world.level.portal.TeleportTransition(lobby,
                    Vec3.atBottomCenterOf(position), Vec3.ZERO, 0, 0,
                    net.minecraft.world.level.portal.TeleportTransition.DO_NOTHING));
            var lobbyResult = new org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean>(
                    "isSunBurnTick", true, true);
            h.assertTrue(control != null && control.level() == lobby, "W9 same zombie moves to lobby");
            try {
                h.assertValueEqual(control.getUUID(), zombie.getUUID(), "W9 control preserves zombie identity");
                h.assertTrue(control.getLightLevelDependentMagicValue() > 0.5F, "W9 lobby control has full daylight");
                callback.invoke(control, lobbyResult);
                h.assertFalse(lobbyResult.isCancelled(), "W9 lobby callback permits vanilla");
                h.assertTrue(lobbyResult.getReturnValue(), "W9 lobby callback preserves a true vanilla result");
                var prepControl = (Zombie) control.teleport(new net.minecraft.world.level.portal.TeleportTransition(prep,
                        Vec3.atBottomCenterOf(prepPosition), Vec3.ZERO, 0, 0,
                        net.minecraft.world.level.portal.TeleportTransition.DO_NOTHING));
                h.assertTrue(prepControl != null && prepControl.level() == prep, "W9 control moves to team prep overworld");
                try {
                    var prepResult = new org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean>(
                            "isSunBurnTick", true, true);
                    callback.invoke(prepControl, prepResult);
                    h.assertFalse(prepResult.isCancelled(), "W9 prep callback permits vanilla");
                    h.assertTrue(prepResult.getReturnValue(), "W9 prep callback preserves a true vanilla result");
                } finally { prepControl.discard(); }
            } finally { control.discard(); }
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
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
