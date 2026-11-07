package dev.mobarena.gametest;
import dev.mobarena.core.*;
import dev.mobarena.collect.*;
import dev.mobarena.wave.*;
import dev.mobarena.team.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import eu.pb4.sgui.api.ClickType;
import net.minecraft.world.inventory.ContainerInput;
import java.util.*;

public class WaveCollectionGameTest {
    private static final String ZOMBIE="minecraft:zombie";
    private static final class Fixture implements AutoCloseable {
        final GameManager game=GameManager.get();
        final ServerPlayer red,blue,mate;
        final GameContext ctx;
        Fixture(GameTestHelper h) {
            game.stop();red=TestPlayers.create(h);blue=TestPlayers.create(h);mate=TestPlayers.create(h);
            TeamManager.get().assign(mate,TeamId.RED);TeamManager.get().assign(red,TeamId.RED);TeamManager.get().assign(blue,TeamId.BLUE);
            if(!game.start(h.getLevel().getServer(),Map.of(red.getUUID(),TeamId.RED,blue.getUUID(),TeamId.BLUE,mate.getUUID(),TeamId.RED),
                    Set.of(red.getUUID(),blue.getUUID(),mate.getUUID()))) throw new IllegalStateException("Cannot start fixture");
            ctx=game.context();
        }
        @Override public void close() {
            game.stop();
            for(var p:List.of(red,blue,mate)) { p.closeContainer();TeamManager.get().remove(p);ctx.server().getPlayerList().remove(p); }
        }
        void command(String command,ServerPlayer p) throws Exception {
            ctx.server().getCommands().getDispatcher().execute(command,p.createCommandSourceStack());
        }
    }
    private void click(WaveConfigGui gui,int slot,ClickType click) {
        gui.getGuiElement(slot).getGuiCallback().click(slot,click,ContainerInput.PICKUP,gui);
    }
    @GameTest public void prepKillAndBlacklist(GameTestHelper h) {
        try(var f=new Fixture(h)) {
            var zombie=h.spawn(EntityTypes.ZOMBIE,1,2,1);
            zombie.hurtServer(h.getLevel(),zombie.damageSources().playerAttack(f.red),1000);
            h.assertTrue(f.ctx.get(CollectKeys.POOLS).get(TeamId.RED).count(ZOMBIE)==1,"Prep zombie kill counts once");
            h.assertTrue(f.ctx.get(CollectKeys.HITS).size()==0,"Killing hit leaves no ledger entry");
            var cod=h.spawn(EntityTypes.COD,2,2,1);
            cod.hurtServer(h.getLevel(),cod.damageSources().playerAttack(f.red),1000);
            h.assertTrue(f.ctx.get(CollectKeys.POOLS).get(TeamId.RED).count("minecraft:cod")==0,"Default short blacklist id blocks cod");
            var baby=h.spawn(EntityTypes.ZOMBIE,3,2,1);baby.setBaby(true);
            baby.hurtServer(h.getLevel(),baby.damageSources().playerAttack(f.blue),1000);
            h.assertTrue(f.ctx.get(CollectKeys.POOLS).get(TeamId.BLUE).count(ZOMBIE)==1,"Baby zombie uses blue pool");
            f.game.endPhaseNow();
            var outside=h.spawn(EntityTypes.ZOMBIE,4,2,1);
            outside.hurtServer(h.getLevel(),outside.damageSources().playerAttack(f.red),1000);
            h.assertTrue(f.ctx.get(CollectKeys.POOLS).get(TeamId.RED).count(ZOMBIE)==1,"Wave setup kill does not count");
        }h.succeed();
    }
    @GameTest public void lastHitAndUnassignedKiller(GameTestHelper h) {
        try(var f=new Fixture(h)) {
            var victim=h.spawn(EntityTypes.ZOMBIE,1,2,1);
            victim.hurtServer(h.getLevel(),victim.damageSources().playerAttack(f.red),1);
            h.assertTrue(f.ctx.get(CollectKeys.HITS).lookup(victim.getUUID(),f.ctx.server().getTickCount()).isPresent(),"Damage event records hit");
            victim.die(victim.damageSources().lava());
            h.assertTrue(f.ctx.get(CollectKeys.POOLS).get(TeamId.RED).count(ZOMBIE)==1,"Trap death uses last hit");
            var expired=h.spawn(EntityTypes.ZOMBIE,2,2,1);
            f.ctx.get(CollectKeys.HITS).record(expired.getUUID(),f.red.getUUID(),f.ctx.server().getTickCount()-201);
            expired.die(expired.damageSources().fall());
            h.assertTrue(f.ctx.get(CollectKeys.POOLS).get(TeamId.RED).count(ZOMBIE)==1,"Expired hit does not count");
            var stranger=TestPlayers.create(h);
            try {
                var mob=h.spawn(EntityTypes.ZOMBIE,3,2,1);
                mob.hurtServer(h.getLevel(),mob.damageSources().playerAttack(f.red),1);
                mob.die(mob.damageSources().playerAttack(stranger));
                h.assertTrue(f.ctx.get(CollectKeys.POOLS).get(TeamId.RED).count(ZOMBIE)==2,"Unassigned killer preserves attribution");
            } finally { f.ctx.server().getPlayerList().remove(stranger); }
        }h.succeed();
    }
    @GameTest public void projectileAndPetKills(GameTestHelper h) {
        try(var f=new Fixture(h)) {
            var arrow=h.spawn(EntityTypes.ARROW,1,2,1);arrow.setOwner(f.red);
            var shot=h.spawn(EntityTypes.ZOMBIE,2,2,1);
            shot.hurtServer(h.getLevel(),shot.damageSources().arrow(arrow,f.red),1000);
            var wolf=h.spawn(EntityTypes.WOLF,3,2,1);wolf.tame(f.red);
            var bitten=h.spawn(EntityTypes.ZOMBIE,4,2,1);
            bitten.hurtServer(h.getLevel(),bitten.damageSources().mobAttack(wolf),1000);
            h.assertTrue(f.ctx.get(CollectKeys.POOLS).get(TeamId.RED).count(ZOMBIE)==2,"Projectile and pet responsibility count");
            var stand=h.spawn(EntityTypes.ARMOR_STAND,5,2,1);
            h.assertTrue(!MobEligibility.counts(stand,Set.of()),"Armor stand has no eligible spawn egg");
        }h.succeed();
    }
    @GameTest public void menuMovesSyncAndLeftovers(GameTestHelper h) throws Exception {
        try(var f=new Fixture(h)) {
            for(int i=0;i<30;i++) f.ctx.get(CollectKeys.POOLS).get(TeamId.RED).add(ZOMBIE);
            f.ctx.get(CollectKeys.POOLS).get(TeamId.RED).add("minecraft:cow");
            f.game.endPhaseNow();
            h.assertTrue(WaveMenuItem.isMenu(f.red.getInventory().getItem(8)),"Wave menu item occupies hotbar slot nine");
            f.command("ma waves",f.red);
            var hub=f.ctx.get(WaveKeys.HUBS).get(TeamId.RED);
            var second=hub.open(f.mate);
            var gui=hub.open(f.red);
            try {
                var setup=f.ctx.get(WaveKeys.SETUPS).get(TeamId.RED);
                click(gui,1,ClickType.MOUSE_LEFT);
                h.assertTrue(setup.waveCount(1,ZOMBIE)==1,"Left moves one");
                click(gui,1,ClickType.MOUSE_LEFT_SHIFT);
                h.assertTrue(setup.waveCount(1,ZOMBIE)==11,"Shift left moves ten");
                click(gui,1,ClickType.MOUSE_RIGHT);
                h.assertTrue(setup.waveCount(1,ZOMBIE)==30,"Right moves all");
                click(gui,36,ClickType.MOUSE_LEFT_SHIFT);
                h.assertTrue(setup.poolCount(ZOMBIE)==10,"Wave egg returns ten");
                h.assertTrue(second.getGuiElement(36)==null,"Other viewer waits for tick end");
                hub.tick(false);
                h.assertTrue(second.getGuiElement(36)!=null,"Tick end refreshes teammate menu");
                h.assertTrue(second.getGuiElement(36).getItemStack().get(net.minecraft.core.component.DataComponents.LORE)
                        .lines().stream().anyMatch(line->line.getString().equals("Count: 20")),"Teammate sees current wave count");
                f.game.endPhaseNow();
                var plan=f.ctx.get(WaveKeys.PLANS).get(TeamId.RED);
                h.assertTrue(plan.author()==TeamId.RED,"Plan records author");
                h.assertTrue(plan.waves().get(2).get(ZOMBIE)==10 && plan.waves().get(2).get("minecraft:cow")==1,"Leftovers enter wave three");
                h.assertTrue(!gui.isOpen(),"Phase exit closes menu");
                h.assertTrue(!WaveMenuItem.isMenu(f.red.getInventory().getItem(8)),"Phase exit removes menu item");
            } finally { gui.close();second.close(); }
        }h.succeed();
    }
    @GameTest public void readyToggle(GameTestHelper h) throws Exception {
        try(var f=new Fixture(h)) {
            f.game.endPhaseNow();
            f.command("ma ready",f.red);
            h.assertTrue(f.ctx.get(WaveKeys.READY).contains(f.red.getUUID()),"Ready sets player flag");
            f.command("ma ready",f.red);
            h.assertTrue(!f.ctx.get(WaveKeys.READY).contains(f.red.getUUID()),"Ready toggles off");
            f.command("ma ready",f.blue);f.command("ma ready",f.mate);f.command("ma ready",f.red);
            h.assertTrue(f.game.phase()==Phase.ARENA,"All online team players end setup");
        }h.succeed();
    }
    @GameTest public void disconnectRechecksReady(GameTestHelper h) throws Exception {
        try(var f=new Fixture(h)) {
            f.game.endPhaseNow();
            f.command("ma ready",f.red);f.command("ma ready",f.mate);
            h.assertTrue(f.game.phase()==Phase.WAVE_CONFIG,"Unready player keeps setup open");
            net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.invoker()
                    .onPlayDisconnect(f.blue.connection,f.ctx.server());
            h.assertTrue(f.game.phase()==Phase.ARENA,"Leaving unready player completes setup");
        }h.succeed();
    }
    @GameTest public void menuItemCannotDropAndRestores(GameTestHelper h) {
        try(var f=new Fixture(h)) {
            f.game.endPhaseNow();
            var inventory=f.red.getInventory();inventory.setSelectedSlot(8);
            h.assertTrue(WaveMenuItem.isMenu(inventory.getSelectedItem()),"Menu item exists in wave setup");
            f.red.drop(false);
            h.assertTrue(WaveMenuItem.isMenu(inventory.getSelectedItem()),"Drop key keeps menu item");
            h.assertTrue(f.red.drop(inventory.getSelectedItem().copy(),false,net.minecraft.util.Prediction.SERVER_ONLY)==null,
                    "Direct drop creates no menu item entity");
            inventory.setItem(8,net.minecraft.world.item.ItemStack.EMPTY);
            net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.invoker().onEndTick(f.ctx.server());
            h.assertTrue(hasMenu(f.red),"End tick restores missing menu item");
            f.game.endPhaseNow();
            net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.invoker().onEndTick(f.ctx.server());
            h.assertTrue(!hasMenu(f.red),"Menu item stays removed after wave setup");
        }h.succeed();
    }
    private boolean hasMenu(ServerPlayer player) {
        var inventory=player.getInventory();
        for(int i=0;i<inventory.getContainerSize();i++) if(WaveMenuItem.isMenu(inventory.getItem(i))) return true;
        return false;
    }
    @GameTest public void menuItemOverflowUsesBackpack(GameTestHelper h) {
        for(boolean fits:new boolean[]{true,false}) {
            try(var f=new Fixture(h)) {
                var inventory=f.red.getInventory();
                for(int slot=0;slot<36;slot++) inventory.setItem(slot,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND,64));
                inventory.setItem(8,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD,7));
                var backpack=dev.mobarena.backpack.TeamBackpack.get(f.ctx.server()).inventory(TeamId.RED);
                if(!fits) {
                    for(int slot=0;slot<27;slot++) backpack.setItem(slot,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND,64));
                    backpack.setItem(0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD,63));
                }
                f.game.endPhaseNow();
                if(fits) {
                    h.assertTrue(WaveMenuItem.isMenu(inventory.getItem(8)),"Overflow frees menu slot");
                    h.assertTrue(backpack.getItem(0).is(net.minecraft.world.item.Items.EMERALD) && backpack.getItem(0).getCount()==7,
                            "Backpack preserves displaced stack");
                } else {
                    h.assertTrue(inventory.getItem(8).getCount()==7 && inventory.getItem(8).is(net.minecraft.world.item.Items.EMERALD),
                            "Insufficient backpack space keeps original slot");
                    h.assertTrue(backpack.getItem(0).getCount()==63,"Failed overflow does not insert a partial stack");
                }
            }
        }h.succeed();
    }
    private static void endTick(GameContext ctx) {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.invoker().onEndTick(ctx.server());
    }
    private static void join(GameContext ctx,ServerPlayer player) {
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.invoker().onPlayReady(player.connection,
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.getSender(player),ctx.server());
    }
    @GameTest public void skippedMenuItemIsNotForcedIn(GameTestHelper h) {
        try(var f=new Fixture(h)) {
            var inventory=f.red.getInventory();
            // Keep the backpack (its own restore rule) and fill every other slot.
            for(int slot=0;slot<36;slot++)
                if(!dev.mobarena.backpack.GuardedItems.isGuarded(inventory.getItem(slot)))
                    inventory.setItem(slot,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND,64));
            if(dev.mobarena.backpack.GuardedItems.isGuarded(inventory.getItem(8))) {
                var backpackItem=inventory.getItem(8);inventory.setItem(0,backpackItem);
            }
            inventory.setItem(8,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD,7));
            var backpack=dev.mobarena.backpack.TeamBackpack.get(f.ctx.server()).inventory(TeamId.RED);
            for(int slot=0;slot<27;slot++) backpack.setItem(slot,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND,64));
            h.assertTrue(inventory.getFreeSlot()<0,"Fixture inventory is full");
            int backpacks=0;
            for(int slot=0;slot<36;slot++) if(dev.mobarena.backpack.GuardedItems.isGuarded(inventory.getItem(slot))) backpacks++;
            h.assertTrue(backpacks==1,"Fixture keeps exactly one backpack: "+backpacks);
            f.game.endPhaseNow();
            h.assertTrue(!hasMenu(f.red),"Full inventory and backpack skip the menu item");
            for(int i=0;i<3;i++) endTick(f.ctx);
            h.assertTrue(!hasMenu(f.red),"Tick end does not force a skipped menu item in");
            h.assertTrue(inventory.getItem(8).is(net.minecraft.world.item.Items.EMERALD) && inventory.getItem(8).getCount()==7,"Slot nine keeps its stack");
            int diamonds=0;
            for(int slot=0;slot<36;slot++) if(inventory.getItem(slot).is(net.minecraft.world.item.Items.DIAMOND)) diamonds++;
            h.assertTrue(diamonds==34,"No inventory stack was displaced: "+diamonds);
            var drops=f.red.level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,f.red.getBoundingBox().inflate(16));
            h.assertTrue(drops.isEmpty(),"No item is dropped");
            h.assertTrue(f.ctx.server().getPlayerList().getPlayers().contains(f.mate) && hasMenu(f.mate),"Other players still get the menu item");
        }h.succeed();
    }
    @GameTest public void rejoinedMenuItemOpensMenu(GameTestHelper h) {
        try(var f=new Fixture(h)) {
            f.game.endPhaseNow();
            net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.invoker().onPlayDisconnect(f.red.connection,f.ctx.server());
            h.assertTrue(!hasMenu(f.red),"Disconnect removes the menu item and its action");
            join(f.ctx,f.red);
            h.assertTrue(f.game.phase()==Phase.WAVE_CONFIG,"Rejoin keeps wave setup running");
            endTick(f.ctx);
            int slot=-1;
            for(int i=0;i<f.red.getInventory().getContainerSize();i++) if(WaveMenuItem.isMenu(f.red.getInventory().getItem(i))) slot=i;
            h.assertTrue(slot>=0,"Restore gives the menu item after rejoin");
            var stack=f.red.getInventory().getItem(slot);f.red.getInventory().setItem(slot,net.minecraft.world.item.ItemStack.EMPTY);
            f.red.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,stack);
            var result=net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.invoker()
                    .interact(f.red,f.red.level(),net.minecraft.world.InteractionHand.MAIN_HAND);
            h.assertTrue(result.consumesAction(),"Menu item use is consumed");
            h.assertTrue(f.red.containerMenu!=f.red.inventoryMenu,"Restored menu item opens the wave menu");
            f.red.closeContainer();
        }h.succeed();
    }
    @GameTest public void joinRemovesStaleMenuItem(GameTestHelper h) {
        try(var f=new Fixture(h)) {
            f.red.getInventory().setItem(20,WaveMenuItem.createStack());
            join(f.ctx,f.red);
            h.assertTrue(!hasMenu(f.red),"JOIN in PREP removes a stale menu item");
            f.game.endPhaseNow();
            h.assertTrue(hasMenu(f.mate),"Wave setup gives the menu item");
            join(f.ctx,f.mate);
            h.assertTrue(hasMenu(f.mate),"JOIN in wave setup keeps the menu item");
        }h.succeed();
    }
}
