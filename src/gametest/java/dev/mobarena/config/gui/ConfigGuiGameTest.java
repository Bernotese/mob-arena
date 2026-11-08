package dev.mobarena.config.gui;

import java.nio.file.*;
import java.util.Optional;
import dev.mobarena.config.*;
import eu.pb4.sgui.api.ClickType;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.world.inventory.ContainerInput;

public class ConfigGuiGameTest {
    private void op(GameTestHelper h,ServerPlayer p) {
        h.getLevel().getServer().getPlayerList().op(p.nameAndId(),Optional.of(LevelBasedPermissionSet.GAMEMASTER),Optional.empty());
    }
    private void cleanup(GameTestHelper h,ServerPlayer p) {
        p.closeContainer();
        h.getLevel().getServer().getPlayerList().deop(p.nameAndId());
        h.getLevel().getServer().getPlayerList().remove(p);
    }
    private void click(ConfigGui gui,int slot,ClickType click) {
        gui.getGuiElement(slot).getGuiCallback().click(slot,click,ContainerInput.PICKUP,gui);
    }
    @GameTest public void configSetResetAndSync(GameTestHelper h) throws Exception {
        Path file=Files.createTempFile("mobarena-gui-", ".json");Files.delete(file);
        var store=new ConfigStore(file);
        var hub=new ConfigGuiHub(store);
        var a=h.makeMockServerPlayerInLevel();var b=h.makeMockServerPlayerInLevel();
        try {
            op(h,a);op(h,b);
            var first=hub.open(a);var second=hub.open(b);
            h.assertTrue(first!=null && second!=null,"Admins can open menus");
            click(first,0,ClickType.MOUSE_LEFT);
            h.assertTrue(store.current().prepTimeSeconds==2760,"Left click increases value");
            h.assertTrue(MobArenaConfig.load(file).prepTimeSeconds==2760,"Change saves immediately");
            h.assertTrue(net.minecraft.world.item.ItemStack.isSameItemSameComponents(
                    second.getGuiElement(0).getItemStack(),first.getGuiElement(0).getItemStack()),"Both menus update");
            h.assertTrue(second.getGuiElement(0).getItemStack().get(net.minecraft.core.component.DataComponents.LORE)
                    .lines().stream().anyMatch(line->line.getString().equals("Value: 2760")),"Second admin sees the new value");
            click(first,0,ClickType.MOUSE_RIGHT_SHIFT);
            h.assertTrue(store.current().prepTimeSeconds==2160,"Shift right decreases ten steps");
            click(first,13,ClickType.MOUSE_LEFT);
            h.assertTrue(store.current().arenaPrepSeconds == 35, "Slot 13 increases arena prep by five seconds");
            h.assertTrue(first.getGuiElement(13).getItemStack().is(net.minecraft.world.item.Items.CLOCK), "Prep uses a clock");
            h.assertTrue(store.current().randomizerEnabled, "Prep slot does not change randomizer");
            click(first,10,ClickType.MOUSE_LEFT);
            h.assertTrue(!store.current().randomizerEnabled,"Boolean toggles");
            click(first,22,ClickType.MOUSE_LEFT);
            h.assertTrue(store.current().prepTimeSeconds==2160,"Plain reset click does nothing");
            click(first,22,ClickType.MOUSE_LEFT_SHIFT);
            h.assertTrue(MobArenaConfig.load(file).prepTimeSeconds==2700 && store.current().randomizerEnabled,"Reset saves defaults");
            store.set("prepTimeSeconds","60");hub.tick();
            h.assertTrue(first.getGuiElement(0).getItemStack().get(net.minecraft.core.component.DataComponents.LORE)
                    .lines().stream().anyMatch(line->line.getString().equals("Value: 60")),"Text command changes refresh the menu");
            click(first,0,ClickType.MOUSE_RIGHT);
            h.assertTrue(store.current().prepTimeSeconds==60,"Click clamps to minimum");
            store.set("prepTimeSeconds","14400");hub.tick();
            click(first,0,ClickType.MOUSE_LEFT_SHIFT);
            h.assertTrue(store.current().prepTimeSeconds==14400,"Click clamps to maximum");
        } finally {hub.closeAll();cleanup(h,a);cleanup(h,b);Files.deleteIfExists(file);}
        h.succeed();
    }
    @GameTest public void configValidationError(GameTestHelper h) throws Exception {
        Path file=Files.createTempFile("mobarena-gui-", ".json");Files.delete(file);
        var store=new ConfigStore(file);
        // Exercise a save failure while retaining valid numeric bounds.
        var hub=new ConfigGuiHub(store);
        var p=h.makeMockServerPlayerInLevel();
        try {
            op(h,p);var gui=hub.open(p);Files.delete(file);Files.createDirectory(file);Files.writeString(file.resolve("keep"),"keep");click(gui,0,ClickType.MOUSE_RIGHT);
            h.assertTrue(gui.error()!=null,"Validation error is visible");
            h.assertTrue(store.current().prepTimeSeconds==2700,"Validation keeps old value");
            h.assertTrue(Files.isDirectory(file),"Save failure keeps the existing path");
        } finally {hub.closeAll();cleanup(h,p);if(Files.isDirectory(file)) Files.deleteIfExists(file.resolve("keep"));Files.deleteIfExists(file);}
        h.succeed();
    }
    @GameTest public void configPermissions(GameTestHelper h) throws Exception {
        Path file=Files.createTempFile("mobarena-gui-", ".json");Files.delete(file);
        var store=new ConfigStore(file);var hub=new ConfigGuiHub(store);
        var p=h.makeMockServerPlayerInLevel();
        try {
            h.assertTrue(hub.open(p)==null,"Non-admin cannot open menu");
            op(h,p);var gui=hub.open(p);
            h.getLevel().getServer().getPlayerList().deop(p.nameAndId());
            click(gui,0,ClickType.MOUSE_LEFT);
            h.assertTrue(store.current().prepTimeSeconds==2700,"Permission loss blocks click");
            h.assertTrue(!gui.isOpen(),"Permission loss closes menu");
        } finally {hub.closeAll();cleanup(h,p);if(Files.isDirectory(file)) Files.deleteIfExists(file.resolve("keep"));Files.deleteIfExists(file);}
        h.succeed();
    }
    @GameTest public void configCommand(GameTestHelper h) throws Exception {
        var p=h.makeMockServerPlayerInLevel();
        try {
            op(h,p);
            var dispatcher=h.getLevel().getServer().getCommands().getDispatcher();
            h.assertTrue(dispatcher.execute("ma config",p.createCommandSourceStack())==1,"Config command opens menu");
            h.assertTrue(dispatcher.execute("ma config get prepTimeSeconds",p.createCommandSourceStack())==1,"Existing config subcommand remains");
        } finally {cleanup(h,p);}
        h.succeed();
    }
}
