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
    static void verify(GameTestHelper h, GameContext ctx, ServerPlayer red, ServerPlayer blue) {
        for (var player : new ServerPlayer[]{red, blue}) {
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
            // W6/W18: use the exact transition selected before PlayerList restores inventory.
            for (var deathLevel : new ServerLevel[]{nether, end}) {
                player.setServerLevel(deathLevel);
                var respawn = player.findRespawnPositionAndUseSpawnBlock(false, TeleportTransition.DO_NOTHING);
                h.assertTrue(respawn.newLevel() == ow, "W6/W18 respawn selects own overworld");
                var replacement = h.makeMockServerPlayerInLevel();
                replacement.setServerLevel(respawn.newLevel());
                replacement.restoreFrom(player, false);
                h.assertTrue(replacement.getInventory().getItem(0).is(Items.DIAMOND), "W6/W18 restore keeps items");
            }
            player.setServerLevel(ow);
        }
        var worlds = ctx.get(TeamWorlds.KEY);
        var redEnd = worlds.get(TeamId.RED).end().asLevel();
        var blueEnd = worlds.get(TeamId.BLUE).end().asLevel();
        h.assertTrue(redEnd.getDragonFight() != null && blueEnd.getDragonFight() != null, "W14 both fights exist");
        h.assertTrue(redEnd.getDragonFight() != blueEnd.getDragonFight(), "W14 independent fight objects");
        for (var pos : new BlockPos[]{new BlockPos(0, 60, 0), new BlockPos(20, 65, 0), new BlockPos(-20, 60, 20)}) {
            h.assertValueEqual(redEnd.getBlockState(pos), blueEnd.getBlockState(pos), "W13 equal End terrain");
        }
        // W5: exercise the guarded entityInside methods outside prep, including the lobby.
        var lobby = ctx.server().overworld();
        red.setServerLevel(lobby);
        for (var portal : new Block[]{Blocks.NETHER_PORTAL, Blocks.END_PORTAL}) {
            red.portalProcess = null;
            portal.defaultBlockState().entityInside(lobby, red.blockPosition(), red, InsideBlockEffectApplier.NOOP, false);
            h.assertTrue(red.portalProcess == null, "W5 lobby portal stays inactive");
        }
    }
}
