package dev.mobarena.backpack;

import dev.mobarena.gametest.TestPlayers;
import dev.mobarena.team.TeamId;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.*;
import java.util.*;

public class BackpackGuardGameTest {
    private static void cleanup(ServerPlayer player) {
        player.closeContainer(); BackpackModule.useGameAccess();
        player.level().getServer().getPlayerList().remove(player); player.discard();
    }

    @GameTest
    public void cancelledDropKeyResendsBackpackToClient(GameTestHelper helper) {
        var packets = new ArrayList<net.minecraft.network.protocol.Packet<?>>();
        ServerPlayer player = TestPlayers.recording(helper, null, packets);
        try {
            player.hasChangedDimension();
            player.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
            player.setGameMode(GameType.SURVIVAL);
            player.getInventory().setSelectedSlot(0);
            ItemStack backpack = BackpackItem.create();
            player.getInventory().setItem(0, backpack);
            int slot = player.inventoryMenu.findSlot(player.getInventory(), 0).orElseThrow();
            for (var action : List.of(
                    net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.DROP_ITEM,
                    net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.DROP_ALL_ITEMS)) {
                packets.clear();
                player.connection.handlePlayerAction(new net.minecraft.network.protocol.game.ServerboundPlayerActionPacket(
                        action, BlockPos.ZERO, Direction.DOWN));
                helper.assertTrue(player.getInventory().getItem(0) == backpack, "Cancelled Q drop preserves the original backpack");
                helper.assertTrue(packets.stream().anyMatch(packet -> packet instanceof
                        net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket content
                        && content.containerId() == player.inventoryMenu.containerId
                        && GuardedItems.hasTag(content.items().get(slot), BackpackItem.TAG)),
                        "Cancelled Q drop resends the backpack slot to the client");
            }
            helper.succeed();
        } finally { cleanup(player); }
    }

    @GameTest
    public void bundleAndShulkerNestingCannotMoveGuardedItems(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.create(helper);
        try {
            ItemStack backpack = BackpackItem.create(), bundle = new ItemStack(Items.BUNDLE);
            var menu = player.inventoryMenu;
            int slot = menu.findSlot(player.getInventory(), 0).orElseThrow();
            player.getInventory().setItem(0, backpack);
            menu.setCarried(bundle);
            menu.clicked(slot, 1, ContainerInput.PICKUP, player);
            helper.assertTrue(GuardedItems.hasTag(player.getInventory().getItem(0), BackpackItem.TAG), "Cursor bundle cannot collect the backpack");
            helper.assertTrue(menu.getCarried().get(DataComponents.BUNDLE_CONTENTS).isEmpty(), "Cursor bundle stays empty");
            player.getInventory().setItem(0, new ItemStack(Items.BUNDLE)); menu.setCarried(backpack);
            menu.clicked(slot, 1, ContainerInput.PICKUP, player);
            helper.assertTrue(GuardedItems.hasTag(menu.getCarried(), BackpackItem.TAG), "Slot bundle cannot collect the cursor backpack");
            helper.assertTrue(player.getInventory().getItem(0).get(DataComponents.BUNDLE_CONTENTS).isEmpty(), "Slot bundle stays empty");
            ItemStack shulker = new ItemStack(Items.SHULKER_BOX);
            shulker.set(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
            player.getInventory().setItem(0, shulker);
            helper.assertTrue(GuardedItems.blocksClick(menu, slot, 1, ContainerInput.PICKUP, player), "Shulker item rejects backpack nesting");
            ItemStack nestedBundle = new ItemStack(Items.BUNDLE);
            nestedBundle.set(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY.copyWithContents(java.util.stream.Stream.of(backpack)));
            helper.assertTrue(GuardedItems.isGuarded(nestedBundle), "Existing bundle contents are guarded");
            shulker.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(nestedBundle)));
            helper.assertTrue(GuardedItems.isGuarded(shulker), "Nested shulker contents are guarded recursively");
            helper.assertTrue(player.drop(shulker, false, net.minecraft.util.Prediction.SERVER_ONLY) == null, "Nested guard blocks dropping the containing item");
            helper.succeed();
        } finally { player.containerMenu.setCarried(ItemStack.EMPTY); cleanup(player); }
    }

    @GameTest
    public void deathKeepsMainAndOffhandSlotsBeforeRecovery(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.create(helper);
        var level = helper.getLevel(); var server = level.getServer();
        boolean keep = level.getGameRules().get(GameRules.KEEP_INVENTORY);
        try {
            level.getGameRules().set(GameRules.KEEP_INVENTORY, false, server);
            player.setGameMode(GameType.SURVIVAL);
            var pos = helper.absolutePos(new BlockPos(1, 2, 1)); player.teleportTo(pos.getX(), pos.getY(), pos.getZ());
            ItemStack main = BackpackItem.create(), offhand = BackpackItem.create();
            player.getInventory().setItem(5, main); player.setItemSlot(EquipmentSlot.OFFHAND, offhand);
            player.getInventory().setItem(6, new ItemStack(Items.STONE, 2));
            player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            BackpackModule.setAccess(p -> p == player ? TeamId.RED : null);
            BackpackModule.open(player);
            player.die(player.damageSources().genericKill());
            helper.assertTrue(player.getInventory().getItem(5) == main, "Death preserves the original main inventory stack before restore");
            helper.assertTrue(player.getItemBySlot(EquipmentSlot.OFFHAND) == offhand, "Death preserves the original offhand stack before restore");
            helper.assertTrue(player.getInventory().getItem(6).isEmpty() && player.getItemBySlot(EquipmentSlot.HEAD).isEmpty(), "Death still drops ordinary items and equipment");
            var drops = level.getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(5));
            helper.assertTrue(drops.stream().noneMatch(e -> GuardedItems.isGuarded(e.getItem())), "Death creates no guarded item entity");
            helper.assertTrue(drops.stream().anyMatch(e -> e.getItem().is(Items.STONE)), "Ordinary inventory items still drop");
            helper.assertTrue(drops.stream().anyMatch(e -> e.getItem().is(Items.IRON_HELMET)), "Ordinary equipment still drops");
            helper.assertTrue(player.containerMenu == player.inventoryMenu, "Death closes the backpack menu");
            helper.succeed();
        } finally { level.getGameRules().set(GameRules.KEEP_INVENTORY, keep, server); cleanup(player); }
    }

    @GameTest
    public void guardedRecoveryKeepsOtherGuardedSlots(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.create(helper);
        try {
            BackpackModule.setAccess(p -> p == player ? TeamId.RED : null);
            for (int i = 0; i < 36; i++) player.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
            String key = "mobarena:test_wave_item";
            ItemStack guarded = new ItemStack(Items.PAPER);
            net.minecraft.nbt.CompoundTag data = new net.minecraft.nbt.CompoundTag(); data.putBoolean(key, true);
            guarded.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
            GuardedItems.register(key, guarded::copy, p -> false);
            player.getInventory().setItem(35, guarded);
            GuardedItems.restore(player);
            helper.assertTrue(player.getInventory().getItem(35) == guarded, "Recovery preserves the other guarded item");
            helper.assertTrue(GuardedItems.hasTag(player.getInventory().getItem(34), BackpackItem.TAG), "Recovery uses an unguarded slot");
            helper.succeed();
        } finally { cleanup(player); }
    }

    @GameTest
    public void worldInteractionsOpenBackpackWithoutStoringIt(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.create(helper);
        try {
            BackpackModule.setAccess(p -> p == player ? TeamId.RED : null);
            player.setItemInHand(InteractionHand.MAIN_HAND, BackpackItem.create());
            var level = helper.getLevel();
            for (var block : List.of(Blocks.DECORATED_POT, Blocks.OAK_SHELF)) {
                BlockPos pos = new BlockPos(1, 1, 1); helper.setBlock(pos, block);
                var hit = new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(pos)), Direction.UP, helper.absolutePos(pos), false);
                var result = UseBlockCallback.EVENT.invoker().interact(player, level, InteractionHand.MAIN_HAND, hit);
                helper.assertTrue(result == InteractionResult.SUCCESS && player.containerMenu instanceof ChestMenu, "Block use opens the backpack and consumes interaction");
                helper.assertTrue(GuardedItems.hasTag(player.getMainHandItem(), BackpackItem.TAG), "Block use keeps the backpack in hand");
                player.closeContainer();
            }
            for (Entity entity : List.of(helper.spawn(EntityTypes.ITEM_FRAME, new BlockPos(2, 2, 2)),
                    helper.spawn(EntityTypes.ARMOR_STAND, new BlockPos(2, 1, 2)), helper.spawn(EntityTypes.ALLAY, new BlockPos(2, 2, 2)))) {
                var result = UseEntityCallback.EVENT.invoker().interact(player, level, InteractionHand.MAIN_HAND, entity, null);
                helper.assertTrue(result == InteractionResult.SUCCESS && player.containerMenu instanceof ChestMenu, "Entity use opens the backpack and consumes interaction");
                helper.assertTrue(GuardedItems.hasTag(player.getMainHandItem(), BackpackItem.TAG), "Entity use keeps the backpack in hand");
                if (entity instanceof ItemFrame frame) helper.assertTrue(frame.getItem().isEmpty(), "Item frame stays empty");
                if (entity instanceof LivingEntity living) helper.assertTrue(living.getMainHandItem().isEmpty(), "Entity receives no backpack");
                player.closeContainer();
            }
            player.setItemInHand(InteractionHand.OFF_HAND, player.getMainHandItem()); player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            var result = UseEntityCallback.EVENT.invoker().interact(player, level, InteractionHand.OFF_HAND,
                helper.spawn(EntityTypes.ALLAY, new BlockPos(2, 2, 2)), null);
            helper.assertTrue(result == InteractionResult.SUCCESS && player.containerMenu instanceof ChestMenu, "Offhand entity use also opens the backpack");
            helper.succeed();
        } finally { cleanup(player); }
    }
    @GameTest
    public void poweredShelfHotbarSwapsPreserveBackpack(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.create(helper);
        try {
            BackpackModule.setAccess(p -> p == player ? TeamId.RED : null);
            player.setGameMode(GameType.SURVIVAL);
            var level = helper.getLevel();
            var shelfBlock = (net.minecraft.world.level.block.ShelfBlock) Blocks.OAK_SHELF;
            for (int length : new int[]{1, 3}) {
                player.getInventory().clearContent();
                player.getInventory().setSelectedSlot(0);
                player.getInventory().setItem(0, new ItemStack(Items.STONE, 3));
                player.getInventory().setItem(6, new ItemStack(Items.STONE, 5));
                int backpackSlot = length == 1 ? 7 : 1;
                ItemStack backpack = BackpackItem.create();
                player.getInventory().setItem(backpackSlot, backpack);
                BlockPos origin = helper.absolutePos(new BlockPos(1, 1, length == 1 ? 1 : 3));
                java.util.List<net.minecraft.world.level.block.entity.ShelfBlockEntity> shelves = new java.util.ArrayList<>();
                for (int offset = 0; offset < length; offset++) {
                    BlockPos pos = origin.offset(offset, 0, 0);
                    level.setBlockAndUpdate(pos.below(), Blocks.REDSTONE_BLOCK.defaultBlockState());
                    level.setBlockAndUpdate(pos, Blocks.OAK_SHELF.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.ShelfBlock.FACING, Direction.NORTH)
                        .setValue(net.minecraft.world.level.block.ShelfBlock.POWERED, true));
                    var shelf = (net.minecraft.world.level.block.entity.ShelfBlockEntity) level.getBlockEntity(pos);
                    for (int slot = 0; slot < shelf.getContainerSize(); slot++) shelf.setItem(slot, new ItemStack(Items.DIAMOND));
                    shelves.add(shelf);
                }
                helper.assertTrue(shelfBlock.getAllBlocksConnectedTo(level, origin).size() == length, "Powered shelf chain has the requested length");
                player.teleportTo(origin.getX() + 0.5, origin.getY(), origin.getZ() - 1);
                var hit = new BlockHitResult(new Vec3(origin.getX() + 0.5, origin.getY() + 0.5, origin.getZ() + 0.01), Direction.NORTH, origin, false);
                player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
                helper.assertTrue(player.getInventory().getItem(backpackSlot) == backpack, "Real powered shelf use preserves the original backpack slot");
                boolean ordinarySwapped = false;
                for (var shelf : shelves) for (int slot = 0; slot < shelf.getContainerSize(); slot++) {
                    helper.assertTrue(!GuardedItems.isGuarded(shelf.getItem(slot)), "Powered shelf receives no guarded stack");
                    ordinarySwapped |= shelf.getItem(slot).is(Items.STONE);
                }
                helper.assertTrue(ordinarySwapped, "Real shelf use still swaps ordinary hotbar items");
                GuardedItems.restore(player);
                int count = 0;
                for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++)
                    if (GuardedItems.hasTag(player.getInventory().getItem(slot), BackpackItem.TAG)) count++;
                helper.assertTrue(count == 1, "Recovery does not duplicate the preserved backpack");
            }
            helper.succeed();
        } finally { cleanup(player); }
    }

}
