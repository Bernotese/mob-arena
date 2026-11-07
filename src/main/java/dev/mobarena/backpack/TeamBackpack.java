package dev.mobarena.backpack;

import dev.mobarena.team.TeamId;
import java.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;

/** Game-lifetime storage. No inventory data is written to disk. Server thread only. */
public final class TeamBackpack {
    private static final Map<MinecraftServer, TeamBackpack> SERVERS = new WeakHashMap<>();
    private final Map<TeamId, SimpleContainer> teams = new EnumMap<>(TeamId.class);

    public static TeamBackpack get(MinecraftServer server) {
        return SERVERS.computeIfAbsent(server, ignored -> new TeamBackpack());
    }

    public static void release(MinecraftServer server) {
        TeamBackpack storage = SERVERS.remove(server);
        if (storage != null) storage.clear(server);
    }

    public SimpleContainer inventory(TeamId team) {
        return teams.computeIfAbsent(team, key -> new SimpleContainer(27) {
            @Override public boolean canPlaceItem(int slot, ItemStack stack) { return !GuardedItems.isGuarded(stack); }
        });
    }

    public void open(ServerPlayer player, TeamId team) {
        player.openMenu(new SimpleMenuProvider((id, inv, owner) ->
            ChestMenu.threeRows(id, inv, inventory(team)), Component.literal("Team Backpack")));
    }

    public static void removeBackpacks(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (GuardedItems.hasTag(player.getInventory().getItem(i), BackpackItem.TAG)) player.getInventory().setItem(i, ItemStack.EMPTY);
        }
        if (GuardedItems.hasTag(player.containerMenu.getCarried(), BackpackItem.TAG)) player.containerMenu.setCarried(ItemStack.EMPTY);
    }

    public void closeViewers(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers())
            if (player.containerMenu instanceof ChestMenu chest && teams.containsValue(chest.getContainer())) player.closeContainer();
    }

    /** Close viewers before clearing shared storage and backpack items. */
    public void clear(MinecraftServer server) {
        closeViewers(server);
        server.getPlayerList().getPlayers().forEach(TeamBackpack::removeBackpacks);
        teams.values().forEach(SimpleContainer::clearContent);
        teams.clear();
    }
}
