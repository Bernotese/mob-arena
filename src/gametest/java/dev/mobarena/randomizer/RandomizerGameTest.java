package dev.mobarena.randomizer;

import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

public class RandomizerGameTest {
    private static final BlockPos POS = new BlockPos(1, 1, 1);

    private static RandomizerLootHook configure(GameTestHelper helper) {
        RandomizerLootHook hook = RandomizerModule.hook();
        // All tests use the same configuration because GameTests share one server level.
        TeamWorldLookup testWorlds = level -> level == helper.getLevel() ? Optional.of("RED") : Optional.empty();
        hook.startGame(731, List.of("RED", "BLUE"), testWorlds, true);
        return hook;
    }

    @GameTest
    public void blockDrop(GameTestHelper helper) {
        var hook = configure(helper);
        helper.setBlock(POS, Blocks.OAK_LOG);
        helper.getLevel().destroyBlock(helper.absolutePos(POS), true);
        helper.assertItemEntityPresent(hook.mapFor("RED").get(Items.OAK_LOG), POS, 3);
        helper.succeed();
    }

    @GameTest
    public void mobLoot(GameTestHelper helper) {
        var hook = configure(helper);
        var pig = helper.spawnWithNoFreeWill(EntityTypes.PIG, POS);
        pig.hurtServer(helper.getLevel(), helper.getLevel().damageSources().genericKill(), 1000);
        helper.assertItemEntityPresent(hook.mapFor("RED").get(Items.PORKCHOP), POS, 3);
        helper.succeed();
    }

    @GameTest
    public void mobEquipment(GameTestHelper helper) {
        var hook = configure(helper);
        var zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, POS);
        zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
        zombie.setGuaranteedDrop(EquipmentSlot.MAINHAND);
        zombie.hurtServer(helper.getLevel(), helper.getLevel().damageSources().genericKill(), 1000);
        helper.assertItemEntityPresent(hook.mapFor("RED").get(Items.DIAMOND_SWORD), POS, 3);
        helper.succeed();
    }

    @GameTest
    public void chestLoot(GameTestHelper helper) {
        var hook = configure(helper);
        helper.setBlock(POS, Blocks.CHEST);
        ChestBlockEntity chest = (ChestBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(POS));
        // Fill a real chest through the same LootTable.fill path as generated structure loot.
        LootTable table = LootTable.lootTable().setParamSet(LootContextParamSets.CHEST)
                .withPool(LootPool.lootPool().add(LootItem.lootTableItem(Items.DIAMOND))).build();
        LootParams params = new LootParams.Builder(helper.getLevel())
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(helper.absolutePos(POS)))
                .create(LootContextParamSets.CHEST);
        table.fill(chest, params, 1);
        helper.assertContainerContainsSingle(POS, hook.mapFor("RED").get(Items.DIAMOND));
        helper.succeed();
    }

    @GameTest
    public void playerDrop(GameTestHelper helper) {
        configure(helper);
        var player = helper.makeMockServerPlayerInLevel();
        player.setPos(Vec3.atCenterOf(helper.absolutePos(POS)));
        var dropped = player.drop(new ItemStack(Items.DIAMOND, 5), false, Prediction.SERVER_ONLY);
        helper.assertTrue(dropped != null && dropped.getItem().is(Items.DIAMOND)
                && dropped.getItem().getCount() == 5, "Player drops keep their item and count");
        helper.succeed();
    }

    @GameTest
    public void playerDeathInventoryDrop(GameTestHelper helper) {
        configure(helper);
        var player = helper.makeMockServerPlayerInLevel();
        player.setPos(Vec3.atCenterOf(helper.absolutePos(POS)));
        player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 5));
        // This is the inventory drop path used by player death when keepInventory is off.
        player.getInventory().dropAll();
        helper.assertItemEntityCountIs(Items.DIAMOND, POS, 3, 5);
        helper.succeed();
    }

    @GameTest
    public void fullPlayerInventoryDrop(GameTestHelper helper) {
        configure(helper);
        var player = helper.makeMockServerPlayerInLevel();
        player.setPos(Vec3.atCenterOf(helper.absolutePos(POS)));
        for (int slot = 0; slot < 36; slot++) {
            player.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        player.getInventory().placeItemBackInInventory(new ItemStack(Items.DIAMOND, 5), Prediction.SERVER_ONLY);
        helper.assertItemEntityCountIs(Items.DIAMOND, POS, 3, 5);
        helper.succeed();
    }

    @GameTest
    public void villagerTrade(GameTestHelper helper) {
        verifyTrade(helper, EntityTypes.VILLAGER);
    }

    @GameTest
    public void wanderingTraderTrade(GameTestHelper helper) {
        verifyTrade(helper, EntityTypes.WANDERING_TRADER);
    }

    private static <T extends AbstractVillager> void verifyTrade(GameTestHelper helper, EntityType<T> type) {
        configure(helper);
        var trader = helper.spawnWithNoFreeWill(type, POS);
        var offers = new MerchantOffers();
        offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.DIAMOND, 3), 10, 1, 0));
        trader.getOffers().clear();
        trader.getOffers().addAll(offers);
        var player = helper.makeMockServerPlayerInLevel();
        trader.setTradingPlayer(player);
        var menu = new MerchantMenu(1, player.getInventory(), trader);
        menu.getSlot(0).set(new ItemStack(Items.EMERALD));
        ItemStack result = menu.getSlot(2).getItem();
        helper.assertTrue(result.is(Items.DIAMOND) && result.getCount() == 3, "Trade output stays unchanged");
        menu.getSlot(2).onTake(player, result);
        helper.assertTrue(offers.getFirst().getUses() == 1, "Trade completes");
        helper.succeed();
    }

    @GameTest
    public void registryPolicyAndStackCap(GameTestHelper helper) {
        var hook = configure(helper);
        var map = hook.mapFor("RED");
        for (Item item : List.of(Items.AIR, Items.BEDROCK, Items.COMMAND_BLOCK, Items.CHAIN_COMMAND_BLOCK,
                Items.REPEATING_COMMAND_BLOCK, Items.STRUCTURE_BLOCK, Items.STRUCTURE_VOID,
                Items.JIGSAW, Items.DEBUG_STICK, Items.BARRIER, Items.LIGHT)) {
            helper.assertTrue(!map.containsKey(item) && !map.containsValue(item), "Restricted item stays outside map: " + item);
        }
        helper.assertTrue(map.containsKey(Items.ZOMBIE_SPAWN_EGG), "Spawn eggs are included");
        Item source = map.entrySet().stream().filter(e -> new ItemStack(e.getValue()).getMaxStackSize() == 1)
                .findFirst().orElseThrow().getKey();
        ItemStack result = hook.mapStack(helper.getLevel(), new ItemStack(source, 64));
        helper.assertTrue(result.getCount() == 1, "Mapped count respects target stack size");
        helper.assertTrue(!map.equals(hook.mapFor("BLUE")), "Teams have different maps");
        helper.succeed();
    }

    @GameTest
    public void disabledAndNonTeamWorld(GameTestHelper helper) {
        var hook = new RandomizerLootHook();
        ItemStack original = new ItemStack(Items.DIAMOND, 5);
        hook.startGame(731, List.of("RED"), level -> Optional.empty(), true);
        helper.assertTrue(hook.mapStack(helper.getLevel(), original) == original, "Non-team world stays unchanged");
        hook.startGame(731, List.of("RED"), level -> Optional.of("RED"), false);
        helper.assertTrue(hook.mapStack(helper.getLevel(), original) == original, "Disabled map stays unchanged");
        hook.clear();
        helper.assertTrue(hook.mapFor("RED").isEmpty(), "Game cleanup removes maps");
        helper.succeed();
    }
}
