package dev.mobarena.gametest;

import dev.mobarena.arena.BattleFlow;
import dev.mobarena.backpack.*;
import dev.mobarena.core.Phase;
import dev.mobarena.debug.DebugDriver;
import dev.mobarena.randomizer.RandomizerModule;
import dev.mobarena.team.TeamId;
import dev.mobarena.wave.*;
import dev.mobarena.world.TeamWorlds;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import java.util.*;

/** Integration checks without T8 outcomes. Unique environments isolate the GameManager singleton. */
public class FullGameTest {
    @GameTest(environment = "mobarena:full_ready", maxTicks = 500)
    public void allReadyEndsSetupAndPutsRemainderInWaveThree(GameTestHelper h) {
        var f = new FullGameFixture(h, true);
        try {
            DebugDriver.poolAdd(TeamId.RED, "zombie", 3); DebugDriver.poolAdd(TeamId.RED, "cow", 1);
            DebugDriver.poolAdd(TeamId.BLUE, "squid", 1);
            f.waveConfig();
            DebugDriver.waves(TeamId.RED, "zombie:1", "cow:1", "-");
            f.command("ma waves", f.red);
            for (var p : List.of(f.red, f.redMate, f.blue)) f.command("ma ready", p);
            h.assertTrue(f.game.phase() == Phase.WAVE_CONFIG, "An unready player keeps setup open");
            long remaining = f.ctx.timer().remainingTicks();
            f.command("ma ready", f.blueMate);
            h.assertTrue(remaining > 0 && f.game.phase() == Phase.ARENA, "All ready ends setup before timeout");
            var plans = f.ctx.get(WaveKeys.PLANS);
            h.assertTrue(plans.get(TeamId.RED).waves().get(0).get("minecraft:zombie") == 1, "Selected first wave stays");
            h.assertTrue(plans.get(TeamId.RED).waves().get(2).get("minecraft:zombie") == 2, "Remaining zombies enter wave three");
            h.assertTrue(plans.get(TeamId.BLUE).waves().get(2).get("minecraft:squid") == 1, "Unconfigured enemy pool enters wave three");
            h.assertTrue(f.red.containerMenu == f.red.inventoryMenu, "Ready transition closes the wave menu");
            h.startSequence().thenWaitUntil(f::arenaReady).thenExecute(f.check(() -> f.state(Phase.ARENA))).thenExecute(f::close).thenSucceed();
        } catch (RuntimeException | Error e) { f.close(); throw e; }
    }

    @GameTest(environment = "mobarena:full_respawn", maxTicks = 600)
    public void twoPlayersRespawnThenEliminateAndWatchEnemyArena(GameTestHelper h) {
        var f = new FullGameFixture(h, true);
        try {
            f.arena();
            long[] died = {0};
            h.startSequence().thenWaitUntil(f::arenaReady)
                .thenExecute(f.check(() -> {
                    f.down(f.red); died[0] = f.server.getTickCount();
                    h.assertTrue(f.red.isAlive() && f.red.isSpectator(), "Death enters spectator without a vanilla death");
                    h.assertTrue(f.battle(TeamId.RED).running(), "The teammate keeps RED active");
                }))
                .thenIdle(58)
                .thenExecute(f.check(() -> h.assertTrue(f.red.isSpectator(), "Player stays down before the three-second deadline")))
                .thenWaitUntil(() -> h.assertTrue(f.red.gameMode() == GameType.SURVIVAL, "Player respawns after three seconds"))
                .thenExecute(f.check(() -> {
                    long elapsed = f.server.getTickCount() - died[0];
                    // GameTests poll during the world tick; respawns run at END_SERVER_TICK.
                    h.assertTrue(elapsed >= 60 && elapsed <= 61, "Respawn occurs at 60 ticks, visible by the next world tick; actual " + elapsed);
                    var spawn = Vec3.atBottomCenterOf(f.ctx.get(TeamWorlds.KEY).get(TeamId.RED).layout().teamSpawn());
                    h.assertTrue(f.red.position().distanceTo(spawn) < 1.5 && f.red.level() == f.ctx.arenaLevel(TeamId.RED), "Respawn uses own team spawn");
                    h.assertTrue(f.red.getHealth() == f.red.getMaxHealth() && f.red.getFoodData().getFoodLevel() == 20, "Respawn restores health and food");
                    f.down(f.red); f.down(f.redMate);
                    h.assertTrue(f.red.isSpectator() && f.redMate.isSpectator(), "Both RED players are down");
                }))
                .thenWaitUntil(() -> h.assertTrue(f.battle(TeamId.RED).state() == BattleFlow.State.ELIMINATED, "Both down eliminates RED"))
                .thenExecute(f.check(() -> {
                    for (var p : List.of(f.red, f.redMate)) h.assertTrue(p.isSpectator() && p.level() == f.ctx.arenaLevel(TeamId.BLUE), "Eliminated players watch BLUE");
                    h.assertTrue(f.battle(TeamId.BLUE).running(), "BLUE continues its battle");
                    var state = DebugDriver.state(f.server);
                    h.assertTrue(state.getAsJsonObject("battles").getAsJsonObject("RED").get("state").getAsString().equals("ELIMINATED"), "State records elimination");
                })).thenExecute(f::close).thenSucceed();
        } catch (RuntimeException | Error e) { f.close(); throw e; }
    }

    @GameTest(environment = "mobarena:full_join", maxTicks = 500)
    public void playersWhoJoinDuringGameStayInLobbyAsSpectators(GameTestHelper h) {
        var f = new FullGameFixture(h, true);
        try {
            checkLateJoin(h, f);
            for (var team : TeamId.values()) DebugDriver.poolAdd(team, "cow", 3);
            f.waveConfig(); checkLateJoin(h, f);
            for (var team : TeamId.values()) DebugDriver.waves(team, "cow:1", "cow:1", "cow:1");
            f.admin("ma skip");
            h.startSequence().thenWaitUntil(f::arenaReady).thenExecute(f.check(() -> {
                checkLateJoin(h, f);
                f.game.enterSuddenDeath(); checkLateJoin(h, f);
                f.game.finish(); checkLateJoin(h, f);
                f.state(Phase.END);
            })).thenExecute(f::close).thenSucceed();
        } catch (RuntimeException | Error e) { f.close(); throw e; }
    }
    private static void checkLateJoin(GameTestHelper h, FullGameFixture f) {
        var late = f.player();
        h.assertTrue(late.isSpectator() && late.level() == f.server.overworld(), "Late join uses lobby spectator mode");
        h.assertTrue(late.getAbilities().invulnerable && f.ctx.team(late.getUUID()) == null, "Late player stays protected and unassigned");
        h.assertTrue(f.ctx.get(TeamWorlds.KEY).values().stream().noneMatch(w -> w.handles().anyMatch(handle -> handle.asLevel() == late.level())), "Late player cannot enter a game level");
    }

    @GameTest(environment = "mobarena:full_randomizer", maxTicks = 500)
    public void teamBlockDropsDifferAndPlayerDropsStayUnmapped(GameTestHelper h) {
        var f = new FullGameFixture(h, true);
        try {
            var hook = RandomizerModule.hook();
            Block source = Arrays.stream(new Block[]{Blocks.OAK_LOG, Blocks.BIRCH_LOG, Blocks.SPRUCE_LOG})
                    .filter(b -> hook.mapFor("RED").get(b.asItem()) != hook.mapFor("BLUE").get(b.asItem())
                            && hook.mapFor("RED").get(b.asItem()) != b.asItem()
                            && hook.mapFor("BLUE").get(b.asItem()) != b.asItem()).findFirst().orElseThrow();
            var checks = List.of(f.red, f.blue);
            h.startSequence().thenIdle(2).thenExecute(f.check(() -> {
                for (var p : checks) {
                    var team = f.ctx.team(p.getUUID());
                    var pos = f.ctx.get(TeamWorlds.KEY).get(team).prepSpawn().offset(3, 1, 3);
                    var level = p.level();
                    level.getEntities(EntityTypes.ITEM, new AABB(pos).inflate(4), e -> true).forEach(Entity::discard);
                    level.setBlockAndUpdate(pos, source.defaultBlockState());
                    p.getInventory().setSelectedSlot(1);
                    p.getInventory().setItem(1, ItemStack.EMPTY);
                    FullGameFixture.acceptLoad(p);
                    h.assertTrue(p.gameMode.destroyBlock(pos), "Survival player breaks a block in its team world");
                }
            })).thenIdle(1).thenExecute(f.check(() -> {
                for (var p : checks) {
                    var team = f.ctx.team(p.getUUID());
                    var pos = f.ctx.get(TeamWorlds.KEY).get(team).prepSpawn().offset(3, 1, 3);
                    var items = p.level().getEntities(EntityTypes.ITEM, new AABB(pos).inflate(4), e -> true);
                    Item expected = hook.mapFor(team.name()).get(source.asItem());
                    h.assertTrue(expected != null && items.size() == 1 && items.getFirst().getItem().is(expected),
                            "Block loot uses " + team + " map: expected " + expected + ", actual "
                                    + items.stream().map(e -> e.getItem().toString()).toList() + ", mode " + p.gameMode());
                    items.forEach(Entity::discard);
                    p.getInventory().setItem(1, new ItemStack(source.asItem(), 5));
                    p.drop(false);
                    h.assertTrue(p.getInventory().getItem(1).getCount() == 4, "Drop key removes one item");
                }
            })).thenIdle(1).thenExecute(f.check(() -> {
                for (var p : checks) {
                    var drops = p.level().getEntities(EntityTypes.ITEM, p.getBoundingBox().inflate(4), e -> true);
                    h.assertTrue(drops.size() == 1 && drops.getFirst().getItem().is(source.asItem())
                            && drops.getFirst().getItem().getCount() == 1, "Drop key preserves its item after one tick");
                    drops.forEach(Entity::discard);
                    // Production PREP keeps inventory. Disable it only for this death-drop check.
                    var rules = p.level().getGameRules();
                    var keepRule = net.minecraft.world.level.gamerules.GameRules.KEEP_INVENTORY;
                    var team = f.ctx.team(p.getUUID());
                    var otherRules = f.ctx.prepLevel(team.opponent()).getGameRules();
                    var testRules = h.getLevel().getGameRules();
                    h.assertTrue(rules != f.server.getGlobalGameRules() && rules != otherRules && rules != testRules,
                            "Death check uses the team's own game rules");
                    boolean keep = rules.get(keepRule), otherKeep = otherRules.get(keepRule), testKeep = testRules.get(keepRule);
                    try {
                        rules.set(net.minecraft.world.level.gamerules.GameRules.KEEP_INVENTORY, false, f.server);
                        h.assertTrue(otherRules.get(keepRule) == otherKeep && testRules.get(keepRule) == testKeep,
                                "Changing one team's keepInventory leaves other levels unchanged");
                        f.down(p);
                        h.assertTrue(p.isDeadOrDying(), "Real PREP death executes the inventory-drop path");
                    } finally { rules.set(net.minecraft.world.level.gamerules.GameRules.KEEP_INVENTORY, keep, f.server); }
                }
            })).thenIdle(1).thenExecute(f.check(() -> {
                for (var p : checks) {
                    var drops = p.level().getEntities(EntityTypes.ITEM, p.getBoundingBox().inflate(4), e -> true);
                    h.assertTrue(drops.size() == 1 && drops.getFirst().getItem().is(source.asItem())
                            && drops.getFirst().getItem().getCount() == 4, "Death drop keeps its original item and count after one tick");
                }
                f.state(Phase.PREP);
            })).thenExecute(f::close).thenSucceed();
        } catch (RuntimeException | Error e) { f.close(); throw e; }
    }

    @GameTest(environment = "mobarena:full_backpack", maxTicks = 500)
    public void teammatesShareBackpackAcrossRealPrepDeathAndRespawn(GameTestHelper h) {
        var f = new FullGameFixture(h, true);
        try {
            f.command("ma backpack", f.red); f.command("ma backpack", f.redMate); f.command("ma backpack", f.blue);
            var first = (ChestMenu) f.red.containerMenu; var second = (ChestMenu) f.redMate.containerMenu;
            h.assertTrue(first.getContainer() == second.getContainer(), "Teammates share the same backpack storage");
            h.assertTrue(first.getContainer() != ((ChestMenu) f.blue.containerMenu).getContainer(), "Enemy storage is separate");
            f.red.getInventory().setItem(1, new ItemStack(Items.DIAMOND, 7));
            int slot = first.findSlot(f.red.getInventory(), 1).orElseThrow();
            first.clicked(slot, 0, ContainerInput.QUICK_MOVE, f.red);
            h.assertTrue(second.getSlot(0).getItem().is(Items.DIAMOND) && second.getSlot(0).getItem().getCount() == 7, "Inventory click inserts items visible to the teammate");
            f.down(f.red);
            h.assertTrue(f.red.isDeadOrDying(), "PREP uses real vanilla death");
            var respawned = f.server.getPlayerList().respawn(f.red, false, Entity.RemovalReason.KILLED);
            FullGameFixture.acceptLoad(respawned);
            h.assertTrue(respawned.isAlive(), "Player respawns through PlayerList");
            f.command("ma backpack", respawned);
            h.assertTrue(((ChestMenu) respawned.containerMenu).getContainer() == second.getContainer(), "Respawn keeps the shared backpack");
            h.assertTrue(second.getSlot(0).getItem().is(Items.DIAMOND) && second.getSlot(0).getItem().getCount() == 7, "Death preserves stored items");
            h.assertTrue(hasBackpack(respawned), "Respawn restores the backpack item");
            f.state(Phase.PREP); f.close(); h.succeed();
        } catch (RuntimeException | Error e) { f.close(); throw e; }
    }
    private static boolean hasBackpack(ServerPlayer p) {
        for (int i = 0; i < p.getInventory().getContainerSize(); i++)
            if (GuardedItems.hasTag(p.getInventory().getItem(i), BackpackItem.TAG)) return true;
        return false;
    }

    @GameTest(environment = "mobarena:full_wave_menu", maxTicks = 500)
    public void waveMenuClicksUpdateBothViewersOnServerTick(GameTestHelper h) {
        var f = new FullGameFixture(h, true);
        try {
            DebugDriver.poolAdd(TeamId.RED, "zombie", 12); f.waveConfig();
            var hub = f.ctx.get(WaveKeys.HUBS).get(TeamId.RED);
            WaveConfigGui first = hub.open(f.red), second = hub.open(f.redMate);
            h.assertTrue(first != null && second != null && first.isOpen() && second.isOpen(), "Both wave menus open");
            assertSelectedWave(h, first, 1);
            assertSelectedWave(h, second, 1);
            waveClick(f.red, 30, 0);
            assertSelectedWave(h, first, 2);
            assertSelectedWave(h, second, 1);
            waveClick(f.red, 31, 0);
            assertSelectedWave(h, first, 3);
            waveClick(f.red, 29, 0);
            assertSelectedWave(h, first, 1);
            waveClick(f.red, 0, 0);
            var setup = f.ctx.get(WaveKeys.SETUPS).get(TeamId.RED);
            h.assertTrue(setup.waveCount(1, "minecraft:zombie") == 1, "Container click moves one mob");
            h.startSequence().thenIdle(1).thenExecute(f.check(() -> {
                assertCount(h, second, 36, 1);
                waveClick(f.redMate, 0, 1);
                h.assertTrue(setup.waveCount(1, "minecraft:zombie") == 12, "Second viewer moves the remaining pool");
            })).thenIdle(1).thenExecute(f.check(() -> {
                assertCount(h, first, 36, 12); assertCount(h, second, 36, 12);
                waveClick(f.redMate, 36, 0);
            })).thenIdle(1).thenExecute(f.check(() -> {
                assertCount(h, first, 36, 11); assertCount(h, second, 36, 11);
                h.assertTrue(setup.poolCount("minecraft:zombie") == 1, "Returning a mob updates the shared pool");
                f.state(Phase.WAVE_CONFIG);
            })).thenExecute(f::close).thenSucceed();
        } catch (RuntimeException | Error e) { f.close(); throw e; }
    }
    /** sgui handles clicks in its packet listener, before AbstractContainerMenu.clicked. */
    private static void waveClick(ServerPlayer player, int slot, int button) {
        var menu = player.containerMenu;
        player.connection.handleContainerClick(new net.minecraft.network.protocol.game.ServerboundContainerClickPacket(
                menu.containerId, menu.getStateId(), (short) slot, (byte) button, ContainerInput.PICKUP,
                new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>(), net.minecraft.network.HashedStack.EMPTY));
    }
    private static void assertSelectedWave(GameTestHelper h, WaveConfigGui gui, int selected) {
        for (int wave = 1; wave <= 3; wave++) {
            var stack = gui.getGuiElement(28 + wave).getItemStack();
            h.assertTrue(stack.getHoverName().getString().equals((wave == selected ? "▶ " : "") + "Wave " + wave),
                    "Only the selected wave has the name prefix");
            h.assertTrue(stack.hasFoil() == (wave == selected), "Only the selected wave has a glint");
        }
    }

    private static void assertCount(GameTestHelper h, WaveConfigGui gui, int slot, int count) {
        var element = gui.getGuiElement(slot);
        h.assertTrue(element != null && element.getItemStack().get(DataComponents.LORE).lines().stream()
                .anyMatch(line -> line.getString().equals("Count: " + count)), "Viewer shows count " + count);
    }
}
