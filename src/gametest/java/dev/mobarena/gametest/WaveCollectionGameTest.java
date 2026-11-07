package dev.mobarena.gametest;
import dev.mobarena.core.*;
import dev.mobarena.collect.*;
import dev.mobarena.wave.*;
import dev.mobarena.team.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.zombie.Zombie;
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
            var zombie=h.spawn(EntityType.ZOMBIE,1,2,1);
            zombie.hurtServer(h.getLevel(),zombie.damageSources().playerAttack(f.red),1000);
            h.assertTrue(f.ctx.get(CollectKeys.POOLS).get(TeamId.RED).count(ZOMBIE)==1,"Prep zombie kill counts once");
            h.assertTrue(f.ctx.get(CollectKeys.HITS).size()==0,"Killing hit leaves no ledger entry");
            var cod=h.spawn(EntityType.COD,2,2,1);
            cod.hurtServer(h.getLevel(),cod.damageSources().playerAttack(f.red),1000);
            h.assertTrue(f.ctx.get(CollectKeys.POOLS).get(TeamId.RED).count("minecraft:cod")==0,"Default short blacklist id blocks cod");
            var baby=h.spawn(EntityType.ZOMBIE,3,2,1);baby.setBaby(true);
            baby.hurtServer(h.getLevel(),baby.damageSources().playerAttack(f.blue),1000);
            h.assertTrue(f.ctx.get(CollectKeys.POOLS).get(TeamId.BLUE).count(ZOMBIE)==1,"Baby zombie uses blue pool");
            f.game.endPhaseNow();
            var outside=h.spawn(EntityType.ZOMBIE,4,2,1);
            outside.hurtServer(h.getLevel(),outside.damageSources().playerAttack(f.red),1000);
            h.assertTrue(f.ctx.get(CollectKeys.POOLS).get(TeamId.RED).count(ZOMBIE)==1,"Wave setup kill does not count");
        }h.succeed();
    }
    @GameTest public void lastHitAndUnassignedKiller(GameTestHelper h) {
        try(var f=new Fixture(h)) {
            var victim=h.spawn(EntityType.ZOMBIE,1,2,1);
            victim.hurtServer(h.getLevel(),victim.damageSources().playerAttack(f.red),1);
            h.assertTrue(f.ctx.get(CollectKeys.HITS).lookup(victim.getUUID(),f.ctx.server().getTickCount()).isPresent(),"Damage event records hit");
            victim.die(victim.damageSources().lava());
            h.assertTrue(f.ctx.get(CollectKeys.POOLS).get(TeamId.RED).count(ZOMBIE)==1,"Trap death uses last hit");
            var expired=h.spawn(EntityType.ZOMBIE,2,2,1);
            f.ctx.get(CollectKeys.HITS).record(expired.getUUID(),f.red.getUUID(),f.ctx.server().getTickCount()-201);
            expired.die(expired.damageSources().fall());
            h.assertTrue(f.ctx.get(CollectKeys.POOLS).get(TeamId.RED).count(ZOMBIE)==1,"Expired hit does not count");
            var stranger=TestPlayers.create(h);
            try {
                var mob=h.spawn(EntityType.ZOMBIE,3,2,1);
                mob.hurtServer(h.getLevel(),mob.damageSources().playerAttack(f.red),1);
                mob.die(mob.damageSources().playerAttack(stranger));
                h.assertTrue(f.ctx.get(CollectKeys.POOLS).get(TeamId.RED).count(ZOMBIE)==2,"Unassigned killer preserves attribution");
            } finally { f.ctx.server().getPlayerList().remove(stranger); }
        }h.succeed();
    }
    @GameTest public void menuMovesSyncAndLeftovers(GameTestHelper h) throws Exception {
        try(var f=new Fixture(h)) {
            for(int i=0;i<30;i++) f.ctx.get(CollectKeys.POOLS).get(TeamId.RED).add(ZOMBIE);
            f.ctx.get(CollectKeys.POOLS).get(TeamId.RED).add("minecraft:cow");
            f.game.endPhaseNow();
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
                f.game.endPhaseNow();
                var plan=f.ctx.get(WaveKeys.PLANS).get(TeamId.RED);
                h.assertTrue(plan.author()==TeamId.RED,"Plan records author");
                h.assertTrue(plan.waves().get(2).get(ZOMBIE)==10 && plan.waves().get(2).get("minecraft:cow")==1,"Leftovers enter wave three");
                h.assertTrue(!gui.isOpen(),"Phase exit closes menu");
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
}
