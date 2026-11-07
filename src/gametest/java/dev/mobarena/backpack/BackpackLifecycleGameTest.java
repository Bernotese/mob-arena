package dev.mobarena.backpack;

import dev.mobarena.core.*;
import dev.mobarena.team.TeamId;
import dev.mobarena.gametest.TestPlayers;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.*;
import java.util.*;

public class BackpackLifecycleGameTest {
    private static long backpacks(ServerPlayer player) {
        long count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++)
            if (GuardedItems.hasTag(player.getInventory().getItem(i), BackpackItem.TAG)) count++;
        return count;
    }

    @GameTest
    public void gamePhasesControlBackpackAccessAndStorage(GameTestHelper helper) throws Exception {
        GameManager game = GameManager.get(); game.stop(); BackpackModule.useGameAccess();
        ServerPlayer red = TestPlayers.create(helper), teammate = TestPlayers.create(helper), blue = TestPlayers.create(helper);
        var server = helper.getLevel().getServer();
        try {
            TeamBackpack storage = TeamBackpack.get(server);
            storage.inventory(TeamId.RED).setItem(0, new ItemStack(Items.STONE));
            helper.assertTrue(!BackpackModule.open(red), "Lobby has no backpack access");
            game.start(server, Map.of(red.getUUID(), TeamId.RED, teammate.getUUID(), TeamId.RED, blue.getUUID(), TeamId.BLUE),
                Set.of(red.getUUID(), teammate.getUUID(), blue.getUUID()));
            helper.assertTrue(backpacks(red) == 1 && backpacks(teammate) == 1 && backpacks(blue) == 1, "PREP gives one backpack after inventory cleanup");
            helper.assertTrue(storage.inventory(TeamId.RED).isEmpty(), "PREP clears old team storage");
            var dispatcher = server.getCommands().getDispatcher();
            helper.assertTrue(dispatcher.execute("ma backpack", red.createCommandSourceStack()) == 1, "Players can open the backpack command");
            BackpackModule.open(teammate); BackpackModule.open(blue);
            helper.assertTrue(((ChestMenu) red.containerMenu).getContainer() == ((ChestMenu) teammate.containerMenu).getContainer(), "Teammates share the command inventory");
            helper.assertTrue(((ChestMenu) red.containerMenu).getContainer() != ((ChestMenu) blue.containerMenu).getContainer(), "Enemy inventory stays separate");
            storage.inventory(TeamId.RED).setItem(1, new ItemStack(Items.DIAMOND, 7));
            game.endPhaseNow();
            helper.assertTrue(BackpackModule.open(red), "WAVE_CONFIG allows backpack access");
            helper.assertTrue(storage.inventory(TeamId.RED).getItem(1).getCount() == 7, "Wave configuration keeps team storage");
            game.endPhaseNow(); helper.assertTrue(BackpackModule.open(red), "ARENA allows backpack access");
            game.enterSuddenDeath(); helper.assertTrue(!BackpackModule.open(red), "SUDDEN_DEATH has no backpack access");
            helper.assertTrue(red.containerMenu == red.inventoryMenu, "Disallowed phases close existing backpack viewers");
            game.stop();
            helper.assertTrue(storage.inventory(TeamId.RED).isEmpty() && backpacks(red) == 0 && backpacks(blue) == 0, "LOBBY clears storage and backpack items");
            helper.assertTrue(red.containerMenu == red.inventoryMenu, "Game stop closes the shared menu");
            helper.succeed();
        } finally {
            game.stop(); BackpackModule.useGameAccess();
            for (var player : List.of(red, teammate, blue)) { player.closeContainer(); server.getPlayerList().remove(player); player.discard(); }
        }
    }

    @GameTest
    public void lobbyJoinRemovesStaleBackpack(GameTestHelper helper) {
        GameManager.get().stop(); BackpackModule.useGameAccess();
        ServerPlayer player = TestPlayers.create(helper, p -> p.getInventory().setItem(0, BackpackItem.create()));
        try {
            helper.assertTrue(backpacks(player) == 0, "JOIN removes a backpack outside an active game");
            helper.succeed();
        } finally { player.level().getServer().getPlayerList().remove(player); player.discard(); }
    }
}
