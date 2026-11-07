package dev.mobarena.config.gui;

import dev.mobarena.core.GameManager;
import dev.mobarena.core.Phase;
import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public final class ConfigGui extends SimpleGui {
    private static final String[] KEYS = {"prepTimeSeconds", "waveConfigTimeSeconds", "waveCountdownSeconds",
        "waveCooldownSeconds", "waveMaxTimeSeconds", "suddenDeathMaxTimeSeconds", "arenaRespawnSeconds",
        "mobGlowAfterSeconds", "endScreenSeconds", "maxAliveMobs"};
    private static final int[] STEPS = {60, 10, 1, 5, 30, 30, 1, 10, 1, 10};
    private final ConfigGuiHub hub;
    private String error;
    ConfigGui(ServerPlayer player, ConfigGuiHub hub) {
        super(MenuType.GENERIC_9x3, player, false);
        this.hub = hub;
        setTitle(Component.literal("Mob Arena config"));
        setLockPlayerInventory(true);
        render();
    }
    public String error() { return error; }
    public void render() {
        var config = hub.current();
        for (int i = 0; i < 27; i++) setSlot(i, new GuiElementBuilder(Items.STAINED_GLASS_PANE.gray())
                .setName(Component.literal(" ")).build());
        for (int i = 0; i < KEYS.length; i++) {
            String key = KEYS[i];
            var range = hub.bounds(key);
            var item = entry(i == 7 ? Items.GLOWSTONE_DUST : i == 9 ? Items.ZOMBIE_HEAD : Items.CLOCK,
                    key, config.get(key)).addLoreLine(Component.literal("Step: " + STEPS[i]))
                    .addLoreLine(Component.literal("Range: " + range.min() + "–" + range.max()));
            int slot = i;
            setSlot(i, item.setCallback((index, type, input, gui) -> handleClick(slot, type)).build());
        }
        setSlot(10, entry(config.randomizerEnabled ? Items.DYE.lime() : Items.DYE.gray(), "randomizerEnabled",
                Boolean.toString(config.randomizerEnabled))
                .setCallback((index, type, input, gui) -> handleClick(10, type)).build());
        var blacklist = entry(Items.PAPER, "mobBlacklist", "Read only. Use /ma config set.");
        config.mobBlacklist.forEach(id -> blacklist.addLoreLine(Component.literal(id)));
        setSlot(11, blacklist.build());
        setSlot(12, entry(Items.MAP, "arenaTemplate", config.arenaTemplate)
                .addLoreLine(Component.literal("Read only. Use /ma config set.")).build());
        setSlot(18, new GuiElementBuilder(Items.BOOK).setName(Component.literal("Help"))
                .addLoreLine(Component.literal("Left: increase. Right: decrease."))
                .addLoreLine(Component.literal("Shift: change ten steps."))
                .addLoreLine(Component.literal("Reset: Shift + left click.")).build());
        setSlot(22, new GuiElementBuilder(Items.BARRIER).setName(Component.literal("Reset all"))
                .addLoreLine(Component.literal("Shift + left click to reset."))
                .setCallback((index, type, input, gui) -> handleClick(22, type)).build());
        setSlot(26, new GuiElementBuilder(Items.OAK_DOOR).setName(Component.literal("Close"))
                .setCallback((index, type, input, gui) -> handleClick(26, type)).build());
        if (error != null) setSlot(17, new GuiElementBuilder(Items.PAPER).setName(Component.literal("Config error"))
                .addLoreLine(Component.literal(error)).build());
    }
    private GuiElementBuilder entry(Item item, String key, String value) {
        var result = new GuiElementBuilder(item).setName(Component.literal(key))
                .addLoreLine(Component.literal("Value: " + value));
        if (GameManager.get().phase() != Phase.LOBBY)
            result.addLoreLine(Component.literal("Applies from the next phase."));
        return result;
    }
    private void handleClick(int slot, ClickType click) {
        if (!ConfigGuiHub.admin(player)) {
            player.sendSystemMessage(Component.literal("You need permission level 2."));
            close();
            return;
        }
        boolean left = click == ClickType.MOUSE_LEFT || click == ClickType.MOUSE_LEFT_SHIFT;
        boolean right = click == ClickType.MOUSE_RIGHT || click == ClickType.MOUSE_RIGHT_SHIFT;
        if (!left && !right) return;
        error = null;
        try {
            if (slot == 26) { close(); return; }
            if (slot == 22) { if (left && click.shift) hub.reset(); return; }
            var config = hub.current();
            if (slot == 10) hub.set("randomizerEnabled", Boolean.toString(ConfigStep.toggle(config.randomizerEnabled)));
            else if (slot >= 0 && slot < KEYS.length) {
                String key = KEYS[slot];
                var range = hub.bounds(key);
                int next = new ConfigStep(STEPS[slot], range.min(), range.max())
                        .apply(Integer.parseInt(config.get(key)), right, click.shift);
                hub.set(key, Integer.toString(next));
            }
        } catch (java.io.IOException | IllegalArgumentException e) {
            error = "Cannot save config: " + e.getMessage();
            player.sendSystemMessage(Component.literal(error));
            render();
        }
    }
    @Override public boolean onAnyClick(int slot, ClickType click, net.minecraft.world.inventory.ContainerInput input) {
        if (ConfigGuiHub.admin(player)) return true;
        close();
        return false;
    }
    @Override public void onRemoved() { hub.removed(this); }
    @Override public void onTick() {
        if (!ConfigGuiHub.admin(player)) close();
    }
}
