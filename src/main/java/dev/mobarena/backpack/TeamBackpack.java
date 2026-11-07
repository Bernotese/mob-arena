package dev.mobarena.backpack;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public final class TeamBackpack extends SavedData {
    private static final Codec<TeamBackpack> CODEC = Codec.unboundedMap(Codec.STRING,
        ItemStack.OPTIONAL_CODEC.listOf()).xmap(TeamBackpack::decode, TeamBackpack::encode);
    public static final SavedDataType<TeamBackpack> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath("mobarena", "team_backpacks"), TeamBackpack::new, CODEC, null);
    private final Map<String, SimpleContainer> teams = new HashMap<>();

    public static TeamBackpack get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public SimpleContainer inventory(String team) {
        return teams.computeIfAbsent(team, key -> {
            SimpleContainer container = new SimpleContainer(27) {
                @Override public void setChanged() {
                    super.setChanged();
                    TeamBackpack.this.setDirty();
                }
                @Override public boolean canPlaceItem(int slot, ItemStack stack) {
                    return !GuardedItems.isGuarded(stack);
                }
            };
            return container;
        });
    }

    public void open(ServerPlayer player, String team) {
        player.openMenu(new SimpleMenuProvider((id, inv, owner) ->
            ChestMenu.threeRows(id, inv, inventory(team)), Component.literal("Team Backpack")));
    }

    /** Call at game end. Close viewers before clearing the shared storage. */
    public void clear(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.containerMenu instanceof ChestMenu chest
                && teams.containsValue(chest.getContainer())) player.closeContainer();
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                if (GuardedItems.hasTag(player.getInventory().getItem(i), BackpackItem.TAG)) {
                    player.getInventory().setItem(i, ItemStack.EMPTY);
                }
            }
            if (GuardedItems.hasTag(player.containerMenu.getCarried(), BackpackItem.TAG)) {
                player.containerMenu.setCarried(ItemStack.EMPTY);
            }
        }
        teams.values().forEach(SimpleContainer::clearContent);
        teams.clear();
        setDirty();
    }

    private Map<String, List<ItemStack>> encode() {
        Map<String, List<ItemStack>> result = new HashMap<>();
        teams.forEach((team, container) -> result.put(team, container.getItems().stream().map(ItemStack::copy).toList()));
        return result;
    }

    private static TeamBackpack decode(Map<String, List<ItemStack>> data) {
        TeamBackpack result = new TeamBackpack();
        data.forEach((team, items) -> {
            SimpleContainer container = result.inventory(team);
            for (int i = 0; i < Math.min(27, items.size()); i++) container.setItem(i, items.get(i));
        });
        result.setDirty(false);
        return result;
    }
}
