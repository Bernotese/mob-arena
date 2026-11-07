package dev.mobarena.config.gui;

import java.io.IOException;
import java.util.*;
import dev.mobarena.config.ConfigStore;
import dev.mobarena.config.MobArenaConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import static net.minecraft.commands.Commands.*;

/** Server-thread owner of all open admin menus. */
public final class ConfigGuiHub {
    private final ConfigStore store;
    private final Set<ConfigGui> menus = new HashSet<>();
    private MobArenaConfig.Snapshot rendered;
    public ConfigGuiHub(ConfigStore store) { this.store = store; }
    public static boolean admin(ServerPlayer player) {
        return hasPermission(LEVEL_GAMEMASTERS).test(player.createCommandSourceStack());
    }
    public ConfigGui open(ServerPlayer player) {
        if (!admin(player)) {
            player.sendSystemMessage(Component.literal("You need permission level 2."));
            return null;
        }
        var gui = new ConfigGui(player, this);
        if (gui.open()) { menus.add(gui); return gui; }
        return null;
    }
    MobArenaConfig current() { return store.current(); }
    dev.mobarena.config.ConfigBounds.Bounds bounds(String key) { return store.bounds(key); }
    void set(String key, String value) throws IOException { store.set(key, value); renderAll(); }
    void reset() throws IOException { store.reset(); renderAll(); }
    void removed(ConfigGui gui) { menus.remove(gui); }
    public void renderAll() {
        for (ConfigGui gui : List.copyOf(menus)) {
            if (gui.isOpen()) gui.render(); else menus.remove(gui);
        }
        rendered = store.current().snapshot();
    }
    /** Include changes made through the text commands. */
    public void tick() {
        if (!store.current().snapshot().equals(rendered)) renderAll();
    }
    public void closeAll() {
        for (ConfigGui gui : List.copyOf(menus)) gui.close();
        menus.clear();
    }
}
