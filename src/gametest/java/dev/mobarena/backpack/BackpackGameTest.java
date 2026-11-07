package dev.mobarena.backpack;

import dev.mobarena.gametest.TestPlayers;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerListener;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class BackpackGameTest {
    @GameTest
    public void sharedStorageAndLiveView(GameTestHelper helper) {
        ServerPlayer first = TestPlayers.create(helper);
        ServerPlayer second = TestPlayers.create(helper);
        TeamBackpack storage = new TeamBackpack();
        SimpleContainer red = storage.inventory("red");
        ChestMenu a = ChestMenu.threeRows(1, first.getInventory(), red);
        ChestMenu b = ChestMenu.threeRows(2, second.getInventory(), storage.inventory("red"));
        int[] seen = {0, 0};
        a.addSlotListener(listener(seen, 0));
        b.addSlotListener(listener(seen, 1));
        red.setItem(0, new ItemStack(Items.DIAMOND, 7));
        a.broadcastChanges();
        b.broadcastChanges();
        helper.assertTrue(seen[0] == 7 && seen[1] == 7, "Both viewers receive shared changes");
        helper.assertTrue(storage.inventory("blue").isEmpty(), "Enemy storage is separate");
        helper.assertTrue(storage.isDirty(), "Changes mark save data dirty");
        helper.succeed();
    }

    private static ContainerListener listener(int[] seen, int index) {
        return new ContainerListener() {
            public void slotChanged(AbstractContainerMenu menu, int slot, ItemStack stack) {
                if (slot == 0) seen[index] = stack.getCount();
            }
            public void dataChanged(AbstractContainerMenu menu, int id, int value) { }
        };
    }

    @GameTest
    public void saveRoundTrip(GameTestHelper helper) {
        TeamBackpack storage = new TeamBackpack();
        storage.inventory("red").setItem(26, new ItemStack(Items.DIAMOND, 11));
        var ops = helper.getLevel().registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
        var encoded = TeamBackpack.TYPE.codec().encodeStart(ops, storage).getOrThrow();
        TeamBackpack loaded = TeamBackpack.TYPE.codec().parse(ops, encoded).getOrThrow();
        helper.assertTrue(loaded.inventory("red").getItem(26).getCount() == 11, "Save restores slot and count");
        helper.assertTrue(loaded.inventory("red").getItem(0).isEmpty(), "Save preserves empty slots");
        helper.succeed();
    }

    @GameTest
    public void dropAndContainerGuards(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.create(helper);
        ItemStack backpack = BackpackItem.create();
        helper.assertTrue(!backpack.has(DataComponents.BUNDLE_CONTENTS), "Vanilla bundle storage is absent");
        player.getInventory().setItem(player.getInventory().getSelectedSlot(), backpack);
        player.drop(false);
        helper.assertTrue(GuardedItems.isGuarded(player.getInventory().getSelectedItem()), "Drop key keeps item");
        helper.assertTrue(player.drop(backpack.copy(), false, Prediction.SERVER_ONLY) == null, "Direct drop creates no entity");
        SimpleContainer chest = new SimpleContainer(27);
        ChestMenu menu = ChestMenu.threeRows(1, player.getInventory(), chest);
        player.containerMenu = menu;
        int slot = menu.findSlot(player.getInventory(), player.getInventory().getSelectedSlot()).orElseThrow();
        menu.clicked(slot, 0, ContainerInput.QUICK_MOVE, player);
        helper.assertTrue(chest.isEmpty(), "Shift click cannot store backpack");
        menu.setCarried(backpack.copy());
        menu.clicked(0, 0, ContainerInput.PICKUP, player);
        helper.assertTrue(chest.isEmpty(), "Cursor cannot store backpack");
        menu.setCarried(ItemStack.EMPTY);
        menu.clicked(0, player.getInventory().getSelectedSlot(), ContainerInput.SWAP, player);
        helper.assertTrue(chest.isEmpty(), "Hotbar swap cannot store backpack");
        helper.succeed();
    }

    @GameTest
    public void deathAndMissingItemRecovery(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.create(helper);
        BackpackModule.setAccess(p -> p == player ? "death-test" : null);
        try {
            GuardedItems.restore(player);
            TeamBackpack storage = new TeamBackpack();
            storage.inventory("death-test").setItem(0, new ItemStack(Items.EMERALD, 3));
            player.die(player.damageSources().genericKill());
            helper.assertTrue(hasBackpack(player), "Backpack stays in its slot before recovery");
            GuardedItems.restore(player);
            helper.assertTrue(hasBackpack(player), "Backpack remains available after death");
            helper.assertTrue(storage.inventory("death-test").getItem(0).getCount() == 3, "Death preserves team storage");
            player.getInventory().clearContent();
            GuardedItems.restore(player);
            helper.assertTrue(hasBackpack(player), "Missing backpack is replaced");
            player.getInventory().clearContent();
            for (int i = 0; i < 36; i++) player.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
            GuardedItems.restore(player);
            helper.assertTrue(hasBackpack(player), "Full inventory still receives backpack");
        } finally {
            BackpackModule.setAccess(p -> null);
        }
        helper.succeed();
    }

    private static boolean hasBackpack(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (GuardedItems.hasTag(player.getInventory().getItem(i), BackpackItem.TAG)) return true;
        }
        return false;
    }
}
