package dev.mobarena.hud;

import java.util.*;
import dev.mobarena.config.MobArenaConfig;
import dev.mobarena.core.*;
import dev.mobarena.team.TeamId;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent.BossBarColor;
import net.minecraft.network.chat.Component;

public class HudGameTest {
    private static final class Fixture implements AutoCloseable {
        final BossBarHud hud=new BossBarHud();
        final ServerPlayer red,blue,spectator;
        final GameContext ctx;
        Fixture(GameTestHelper helper) {
            red=helper.makeMockServerPlayerInLevel();
            blue=helper.makeMockServerPlayerInLevel();
            spectator=helper.makeMockServerPlayerInLevel();
            ctx=new GameContext(helper.getLevel().getServer(),1,
                    Map.of(red.getUUID(),TeamId.RED,blue.getUUID(),TeamId.BLUE),new MobArenaConfig());
        }
        @Override public void close() {
            hud.close();
            for(var p:List.of(red,blue,spectator)) ctx.server().getPlayerList().remove(p);
        }
        void checkTeams(GameTestHelper h) {
            h.assertTrue(hud.bar(TeamId.RED).getPlayers().contains(red),"Red player sees red bar");
            h.assertTrue(!hud.bar(TeamId.RED).getPlayers().contains(blue),"Blue cannot see red bar");
            h.assertTrue(hud.bar(TeamId.BLUE).getPlayers().contains(blue),"Blue player sees blue bar");
            h.assertTrue(hud.bar(null).getPlayers().contains(spectator),"Spectator sees neutral bar");
        }
    }
    @GameTest public void prep(GameTestHelper h) {
        try(var f=new Fixture(h)) {
            f.ctx.timer().reset(2700*20);f.ctx.timer().tick();
            f.hud.onEnter(Phase.PREP,f.ctx);f.checkTeams(h);
            h.assertTrue(f.hud.bar(TeamId.RED).getName().getString().equals("Preparation – 44:59"),"Prep timer text");
            h.assertTrue(f.hud.bar(TeamId.RED).getColor()==BossBarColor.GREEN,"Prep color");
            h.assertTrue(f.hud.bar(TeamId.RED).getProgress()<1,"Prep progress decreases");
            String initial=f.hud.bar(TeamId.RED).getName().getString();
            for(int tick=2;tick<20;tick++) { f.ctx.timer().tick();f.hud.onTick(Phase.PREP,f.ctx); }
            h.assertTrue(f.hud.bar(TeamId.RED).getName().getString().equals(initial),"No update before twenty ticks");
            f.ctx.timer().tick();f.hud.onTick(Phase.PREP,f.ctx);
            h.assertTrue(f.hud.bar(TeamId.RED).getProgress()==53980F/54000,"Periodic timer update");
            f.hud.remove(f.red);
            h.assertTrue(!f.hud.bar(TeamId.RED).getPlayers().contains(f.red),"Disconnect removes viewer");
            f.hud.add(f.red,Phase.PREP,f.ctx);f.checkTeams(h);
        }h.succeed();
    }
    @GameTest public void waveConfig(GameTestHelper h) {
        try(var f=new Fixture(h)) {
            f.ctx.timer().reset(180*20);
            HudOverrides.of(f.ctx).setReady(1,2);
            f.hud.onEnter(Phase.WAVE_CONFIG,f.ctx);f.checkTeams(h);
            h.assertTrue(f.hud.bar(TeamId.RED).getName().getString().contains("1/2 ready"),"Ready count");
            h.assertTrue(!f.hud.bar(null).getName().getString().contains("ready"),"Neutral bar has phase and time only");
            h.assertTrue(f.hud.bar(TeamId.RED).getColor()==BossBarColor.YELLOW,"Setup color");
        }h.succeed();
    }
    @GameTest public void arena(GameTestHelper h) { battle(h,Phase.ARENA,BossBarColor.RED); }
    @GameTest public void suddenDeath(GameTestHelper h) { battle(h,Phase.SUDDEN_DEATH,BossBarColor.PURPLE); }
    private void battle(GameTestHelper h,Phase phase,BossBarColor color) {
        try(var f=new Fixture(h)) {
            f.ctx.timer().reset(-1);
            f.hud.onEnter(phase,f.ctx);f.checkTeams(h);
            h.assertTrue(f.hud.bar(TeamId.RED).getColor()==color,"Battle phase color");
            HudOverrides.of(f.ctx).set(TeamId.RED,new HudLine(Component.literal("12 mobs left"),0.5F,color));
            f.hud.update(phase,f.ctx);
            h.assertTrue(f.hud.bar(TeamId.RED).getName().getString().equals("12 mobs left"),"Team override");
            h.assertTrue(f.hud.bar(TeamId.RED).getProgress()==0.5F,"Wave progress");
            h.assertTrue(!f.hud.bar(TeamId.BLUE).getName().getString().contains("mobs"),"Override stays private");
            h.assertTrue(!f.hud.bar(null).getName().getString().contains("mobs"),"Neutral bar stays private");
            f.hud.onExit(phase,f.ctx);
            h.assertTrue(HudOverrides.of(f.ctx).get(TeamId.RED)==null,"Phase exit clears battle override");
        }h.succeed();
    }
    @GameTest public void end(GameTestHelper h) {
        try(var f=new Fixture(h)) {
            f.ctx.timer().reset(300);HudOverrides.of(f.ctx).setResult(TeamId.BLUE);
            f.hud.onEnter(Phase.END,f.ctx);f.checkTeams(h);
            h.assertTrue(f.hud.bar(TeamId.RED).getName().getString().equals("Team BLUE wins"),"Winner text");
            h.assertTrue(f.hud.bar(TeamId.RED).getColor()==BossBarColor.BLUE,"Winner color");
            h.assertTrue(f.hud.bar(null).getName().getString().equals("Team BLUE wins"),"Neutral viewer sees winner");
            Titles.show(f.red,Component.literal("Winner"),null,0,20,0);
            h.assertTrue(f.hud.bar(TeamId.RED).getProgress()==1,"End bar is full");
            HudOverrides.of(f.ctx).setResult(null);f.hud.update(Phase.END,f.ctx);
            h.assertTrue(f.hud.bar(TeamId.RED).getName().getString().equals("Draw"),"Draw text");
        }h.succeed();
    }
    @GameTest public void lobby(GameTestHelper h) {
        try(var f=new Fixture(h)) {
            f.ctx.timer().reset(54000);f.hud.onEnter(Phase.PREP,f.ctx);
            f.hud.onEnter(Phase.LOBBY,f.ctx);
            for(TeamId team:TeamId.values()) {
                h.assertTrue(!f.hud.bar(team).isVisible(),"Lobby bar hidden");
                h.assertTrue(f.hud.bar(team).getPlayers().isEmpty(),"Lobby viewers cleared");
            }
            h.assertTrue(!f.hud.bar(null).isVisible(),"Neutral lobby bar hidden");
        }h.succeed();
    }
    @GameTest public void joinDuringGameUsesNeutralBar(GameTestHelper h) {
        try(var f=new Fixture(h)) {
            f.ctx.timer().reset(54000);f.hud.onEnter(Phase.PREP,f.ctx);
            var joined=h.makeMockServerPlayerInLevel();
            try {
                joined.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
                f.hud.add(joined,Phase.PREP,f.ctx);
                h.assertTrue(f.hud.bar(null).getPlayers().contains(joined),"Joined spectator sees neutral bar");
                for(var team:TeamId.values())
                    h.assertTrue(!f.hud.bar(team).getPlayers().contains(joined),"Joined spectator sees no team bar");
            } finally { f.hud.remove(joined);f.ctx.server().getPlayerList().remove(joined); }
        }h.succeed();
    }
    @GameTest public void joinLobbyAfterGameHasNoBar(GameTestHelper h) {
        try(var f=new Fixture(h)) {
            f.ctx.timer().reset(54000);f.hud.onEnter(Phase.PREP,f.ctx);
            f.hud.onEnter(Phase.LOBBY,f.ctx);
            var joined=h.makeMockServerPlayerInLevel();
            try {
                f.hud.add(joined,Phase.LOBBY,null);
                h.assertTrue(f.hud.bar(null).getPlayers().isEmpty(),"Lobby join has no neutral bar");
                for(var team:TeamId.values())
                    h.assertTrue(f.hud.bar(team).getPlayers().isEmpty(),"Lobby join has no team bar");
            } finally { f.ctx.server().getPlayerList().remove(joined); }
        }h.succeed();
    }
}
