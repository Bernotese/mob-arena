package dev.mobarena.wave;
import dev.mobarena.team.TeamId;
import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import java.util.*;

public final class WaveConfigGui extends SimpleGui {
    private static final int[] WAVE_SLOTS={36,37,38,39,40,41,42,43,44,46,47,48,49,50,51,52};
    private final WaveConfigHub hub;
    private int selected=1,poolPage,wavePage;
    WaveConfigGui(ServerPlayer player, WaveConfigHub hub) {
        super(MenuType.GENERIC_9x6,player,false);this.hub=hub;
        setTitle(Component.literal("Waves – Team "+hub.team).withStyle(color()));
        setLockPlayerInventory(true);render();
    }
    public ServerPlayer viewer() { return player; }
    private ChatFormatting color() { return hub.team==TeamId.RED?ChatFormatting.RED:ChatFormatting.BLUE; }
    private boolean active() { if(hub.active(player)) return true;close();return false; }
    private static int amount(ClickType click) {
        return MoveAmount.of(click==ClickType.MOUSE_LEFT || click==ClickType.MOUSE_LEFT_SHIFT,
                click==ClickType.MOUSE_RIGHT || click==ClickType.MOUSE_RIGHT_SHIFT,click.shift);
    }
    private GuiElementBuilder text(Item item,String name) {
        return new GuiElementBuilder(item).setName(Component.literal(name));
    }
    private void button(int slot, Item item, String name, Runnable action) {
        setSlot(slot,text(item,name).setCallback((index,click,input,gui)->{
            if(active() && amount(click)>0) { action.run();if(isOpen()) render(); }
        }).build());
    }
    private void egg(int slot,String id,int count,boolean pool) {
        setSlot(slot,GuiElementBuilder.from(EggIcons.icon(id)).setName(EggIcons.type(id).getDescription())
                .setCount(Math.min(count,64)).hideDefaultTooltip()
                .addLoreLine(Component.literal("Count: "+count))
                .addLoreLine(Component.literal("Left: 1  Shift+Left: 10  Right: all"))
                .setCallback((index,click,input,gui)->{
                    if(!active()) return;
                    int n=amount(click);if(n==0) return;
                    int moved=pool?hub.setup.toWave(id,selected,n):hub.setup.toPool(selected,id,n);
                    if(moved>0) { hub.dirty();render(); }
                }).build());
    }
    public void render() {
        if(!hub.active(player)) { close();return; }
        for(int i=0;i<54;i++) clearSlot(i);
        var pool=new ArrayList<>(hub.setup.pool().entrySet());
        var wave=new ArrayList<>(hub.setup.wave(selected).entrySet());
        int poolLast=Math.max(0,(pool.size()-1)/27),waveLast=Math.max(0,(wave.size()-1)/16);
        poolPage=Math.min(poolPage,poolLast);wavePage=Math.min(wavePage,waveLast);
        for(int i=0;i<27 && poolPage*27+i<pool.size();i++) {
            var entry=pool.get(poolPage*27+i);egg(i,entry.getKey(),entry.getValue(),true);
        }
        for(int i=0;i<16 && wavePage*16+i<wave.size();i++) {
            var entry=wave.get(wavePage*16+i);egg(WAVE_SLOTS[i],entry.getKey(),entry.getValue(),false);
        }
        button(27,poolPage>0?Items.ARROW:Items.STAINED_GLASS_PANE.gray(),"Pool: previous page",()->{if(poolPage>0)poolPage--;});
        button(35,poolPage<poolLast?Items.ARROW:Items.STAINED_GLASS_PANE.gray(),"Pool: next page",()->{if(poolPage<poolLast)poolPage++;});
        setSlot(28,text(Items.PAPER,"Pool: "+hub.setup.poolTotal()+" mobs, "+pool.size()+" types").build());
        Item[] tabs={Items.BANNER.white(),Items.BANNER.yellow(),Items.BANNER.red()};
        for(int w=1;w<=3;w++) {
            int target=w;
            var tab=text(tabs[w-1],"Wave "+w).setName(Component.literal("Wave "+w).withStyle(color()))
                    .addLoreLine(Component.literal(hub.setup.waveTotal(w)+" mobs, "+hub.setup.wave(w).size()+" types"));
            if(w==selected)tab.glow();
            setSlot(28+w,tab.setCallback((index,click,input,gui)->{
                if(active() && amount(click)>0) { selected=target;wavePage=0;render(); }
            }).build());
        }
        long seconds=Math.max(0,hub.ctx.timer().remainingTicks())/20;
        setSlot(32,text(Items.CLOCK,"Time left: "+seconds/60+":"+String.format(java.util.Locale.ROOT,"%02d",seconds%60)).build());
        boolean ready=hub.ctx.get(WaveKeys.READY).contains(player.getUUID());
        setSlot(33,text(ready?Items.DYE.lime():Items.DYE.gray(),ready?"Ready":"Not ready")
                .addLoreLine(Component.literal(WavePhaseListener.readyCount(hub.ctx)+"/"+WavePhaseListener.players(hub.ctx,null).size()+" players ready"))
                .setCallback((index,click,input,gui)->{if(active() && amount(click)>0)hub.owner.toggle(player);}).build());
        setSlot(34,text(Items.STAINED_GLASS_PANE.gray()," ").build());
        button(45,wavePage>0?Items.ARROW:Items.STAINED_GLASS_PANE.gray(),"Wave: previous page",()->{if(wavePage>0)wavePage--;});
        button(53,wavePage<waveLast?Items.ARROW:Items.STAINED_GLASS_PANE.gray(),"Wave: next page",()->{if(wavePage<waveLast)wavePage++;});
    }
    @Override public boolean onAnyClick(int slot,ClickType type,ContainerInput input) { return active(); }
    @Override public void onRemoved() { hub.removed(this); }
}
