package dev.mobarena.world;

import dev.mobarena.core.GameContext;
import dev.mobarena.team.TeamId;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

final class PortalWorldChecks {
    private PortalWorldChecks() {}
    static ServerPlayer[] verify(GameTestHelper h, GameContext ctx, ServerPlayer red, ServerPlayer blue) {
        ServerPlayer[] players = {red, blue};
        for (int index = 0; index < players.length; index++) {
            var player = players[index];
            TeamId team = ctx.team(player.getUUID());
            var world = ctx.get(TeamWorlds.KEY).get(team);
            var ow = world.overworld().asLevel();
            var nether = world.nether().asLevel();
            var end = world.end().asLevel();
            var pos = world.prepSpawn().offset(8, 0, 0);
            player.teleport(new TeleportTransition(ow, Vec3.atBottomCenterOf(pos), Vec3.ZERO, 0, 0, TeleportTransition.DO_NOTHING));
            // W4: fire placement must light a real obsidian frame in a custom dimension.
            for (int x = -1; x <= 2; x++) for (int y = -1; y <= 3; y++) {
                ow.setBlock(pos.offset(x, y, 0), (x == -1 || x == 2 || y == -1 || y == 3
                        ? Blocks.OBSIDIAN : Blocks.AIR).defaultBlockState(), 3);
            }
            ow.setBlockAndUpdate(pos, Blocks.FIRE.defaultBlockState());
            h.assertTrue(ow.getBlockState(pos).is(Blocks.NETHER_PORTAL), "W4 fire lights team portal");
            var toNether = ((NetherPortalBlock) Blocks.NETHER_PORTAL).getPortalDestination(ow, player, pos);
            h.assertTrue(toNether != null && toNether.newLevel() == nether, "W4 team Nether destination");
            var zombie = net.minecraft.world.entity.EntityTypes.ZOMBIE.create(ow, EntitySpawnReason.COMMAND);
            zombie.setPos(Vec3.atBottomCenterOf(pos));
            ow.addFreshEntity(zombie);
            var mobTransition = ((NetherPortalBlock) Blocks.NETHER_PORTAL).getPortalDestination(ow, zombie, pos);
            h.assertTrue(mobTransition != null && mobTransition.newLevel() == nether, "Mob portal follows its source team");
            var movedMob = zombie.teleport(mobTransition);
            h.assertTrue(movedMob != null && movedMob.level() == nether, "Zombie arrives in team Nether");
            movedMob.discard();
            player.teleport(toNether);
            var back = ((NetherPortalBlock) Blocks.NETHER_PORTAL).getPortalDestination(nether, player, player.blockPosition());
            h.assertTrue(back != null && back.newLevel() == ow, "W4 return to own overworld");
            player.teleport(back);
            // W12: actual destination code creates the End platform in the team End.
            ow.setBlockAndUpdate(pos, Blocks.END_PORTAL.defaultBlockState());
            var toEnd = ((EndPortalBlock) Blocks.END_PORTAL).getPortalDestination(ow, player, pos);
            h.assertTrue(toEnd != null && toEnd.newLevel() == end, "W12 team End destination");
            player.teleport(toEnd);
            h.assertTrue(end.getBlockState(ServerLevel.END_SPAWN_POINT.below(2)).is(Blocks.OBSIDIAN), "W12 End platform");
            player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 3));
            // W15: exit uses the pre-respawn hook, without a death or credits flow.
            var exit = ((EndPortalBlock) Blocks.END_PORTAL).getPortalDestination(end, player, BlockPos.ZERO);
            h.assertTrue(exit.newLevel() == ow, "W15 End exit routes to team overworld");
            h.assertTrue(exit.position().distanceTo(Vec3.atBottomCenterOf(world.prepSpawn())) < 1, "W15 own spawn");
            player.teleport(exit);
            h.assertValueEqual(player.getInventory().getItem(0).getCount(), 3, "W15 exit keeps items");
            var item = new ItemEntity(end, 0, 70, 0, new ItemStack(Items.STONE));
            var itemExit = ((EndPortalBlock) Blocks.END_PORTAL).getPortalDestination(end, item, BlockPos.ZERO);
            h.assertTrue(itemExit.newLevel() == ow, "W16 item exits to own team");
            h.assertTrue(itemExit.position().distanceTo(Vec3.atBottomCenterOf(world.prepSpawn())) < 10, "W16 item exits at team spawn");
            // W6/W18: use the real death and PlayerList respawn paths.
            for (var deathLevel : new ServerLevel[]{nether, end}) {
                player.teleport(new TeleportTransition(deathLevel, new Vec3(0.5, 100, 0.5), Vec3.ZERO, 0, 0, TeleportTransition.DO_NOTHING));
                // Complete the embedded client's dimension-load acknowledgement.
                player.hasChangedDimension();
                player.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
                player.setInvulnerableTime(0);
                player.hurtServer(deathLevel, deathLevel.damageSources().genericKill(), 1000);
                h.assertTrue(player.isDeadOrDying(), "W6/W18 player dies in team dimension");
                var connection = player.connection;
                connection.handleClientCommand(new net.minecraft.network.protocol.game.ServerboundClientCommandPacket(
                        net.minecraft.network.protocol.game.ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
                player = connection.player;
                h.assertTrue(player.level() == ow, "W6/W18 actual respawn selects own overworld");
                h.assertTrue(player.getInventory().getItem(0).is(Items.DIAMOND), "W6/W18 actual respawn keeps items");
                h.assertValueEqual(player.getInventory().getItem(0).getCount(), 3, "W6/W18 item count survives");
            }
            players[index] = player;
            bedExit(h, world, player);
        }
        var worlds = ctx.get(TeamWorlds.KEY);
        var redEnd = worlds.get(TeamId.RED).end().asLevel();
        var blueEnd = worlds.get(TeamId.BLUE).end().asLevel();
        h.assertTrue(redEnd.getDragonFight() != null && blueEnd.getDragonFight() != null, "W14 both fights exist");
        h.assertTrue(redEnd.getDragonFight() != blueEnd.getDragonFight(), "W14 independent fight objects");
        for (var pos : new BlockPos[]{new BlockPos(0, 60, 0), new BlockPos(20, 65, 0), new BlockPos(-20, 60, 20)}) {
            h.assertValueEqual(redEnd.getBlockState(pos), blueEnd.getBlockState(pos), "W13 equal End terrain");
        }
        var redSpikes = net.minecraft.world.level.levelgen.feature.EndSpikeFeature.getSpikesForLevel(redEnd);
        var blueSpikes = net.minecraft.world.level.levelgen.feature.EndSpikeFeature.getSpikesForLevel(blueEnd);
        for (int i = 0; i < 3; i++) {
            var spike = redSpikes.get(i);
            h.assertValueEqual(spike.getHeight(), blueSpikes.get(i).getHeight(), "W13 equal spike height");
            var pos = new BlockPos(spike.getCenterX(), spike.getHeight() - 1, spike.getCenterZ());
            h.assertTrue(redEnd.getBlockState(pos).is(Blocks.OBSIDIAN), "W13 spike is generated");
            h.assertValueEqual(redEnd.getBlockState(pos), blueEnd.getBlockState(pos), "W13 equal spike blocks");
        }
        // W5: exercise the guarded entityInside methods outside prep, including the lobby.
        var lobby = ctx.server().overworld();
        red = players[0];
        red.teleport(new TeleportTransition(lobby, new Vec3(0.5, 80, 0.5), Vec3.ZERO, 0, 0, TeleportTransition.DO_NOTHING));
        for (var portal : new Block[]{Blocks.NETHER_PORTAL, Blocks.END_PORTAL}) {
            red.portalProcess = null;
            portal.defaultBlockState().entityInside(lobby, red.blockPosition(), red, InsideBlockEffectApplier.NOOP, false);
            h.assertTrue(red.portalProcess == null, "W5 lobby portal stays inactive");
        }
        return players;
    }
    private static void bedExit(GameTestHelper h, TeamWorlds worlds, ServerPlayer player) {
        var ow = worlds.overworld().asLevel();
        BlockPos bed = worlds.prepSpawn().offset(20, 1, 20);
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
            ow.setBlockAndUpdate(bed.offset(x, -1, z), Blocks.STONE.defaultBlockState());
            for (int y = 0; y <= 2; y++) ow.setBlockAndUpdate(bed.offset(x, y, z), Blocks.AIR.defaultBlockState());
        }
        var facing = net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING;
        var part = net.minecraft.world.level.block.state.properties.BlockStateProperties.BED_PART;
        ow.setBlock(bed.west(), Blocks.BED.red().defaultBlockState().setValue(facing, Direction.EAST)
                .setValue(part, net.minecraft.world.level.block.state.properties.BedPart.FOOT), 2);
        ow.setBlock(bed, Blocks.BED.red().defaultBlockState().setValue(facing, Direction.EAST)
                .setValue(part, net.minecraft.world.level.block.state.properties.BedPart.HEAD), 2);
        player.setRespawnPosition(new ServerPlayer.RespawnConfig(new net.minecraft.world.level.storage.LevelData.RespawnData(
                GlobalPos.of(ow.dimension(), bed), 0, 0), false), false);
        var end = worlds.end().asLevel();
        player.teleport(new TeleportTransition(end, new Vec3(0.5, 80, 0.5), Vec3.ZERO, 0, 0, TeleportTransition.DO_NOTHING));
        var exit = ((EndPortalBlock) Blocks.END_PORTAL).getPortalDestination(end, player, BlockPos.ZERO);
        h.assertTrue(exit.newLevel() == ow && exit.position().distanceTo(Vec3.atCenterOf(bed)) < 4, "W15 team bed is preserved");
        player.teleport(exit);
    }
}
