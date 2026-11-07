package dev.mobarena.wave;
import dev.mobarena.core.*;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Recovery compass menu item with shared T4 protection. */
public final class WaveMenuItem implements WaveMenuItems {
    public interface Guard {
        Guard NONE=new Guard() {};
        default void protect(ServerPlayer player, ItemStack stack) {}
        default boolean storeOverflow(ServerPlayer player,GameContext ctx,ItemStack stack) { return false; }
    }
    public static final String TAG="mobarena:wave_menu";
    private final Guard guard;
    private final Map<UUID,Runnable> actions=new HashMap<>();
    // Players whose hotbar slot could not be freed. restore() must not force the item in.
    private final Set<UUID> skipped=new HashSet<>();
    private java.util.function.Consumer<ServerPlayer> opener=player->{};
    public WaveMenuItem(Guard guard) { this.guard=Objects.requireNonNull(guard); }
    public void register() {
        UseItemCallback.EVENT.register((player,level,hand)->use(player,player.getItemInHand(hand)));
        UseBlockCallback.EVENT.register((player,level,hand,hit)->use(player,player.getItemInHand(hand)));
        UseEntityCallback.EVENT.register((player,level,hand,entity,hit)->use(player,player.getItemInHand(hand)));
    }
    private InteractionResult use(net.minecraft.world.entity.player.Player player,ItemStack stack) {
        if(!isMenu(stack)) return InteractionResult.PASS;
        if(player instanceof ServerPlayer serverPlayer) {
            // Rejoined players get the item from restore() and have no stored action.
            Runnable action=actions.get(serverPlayer.getUUID());
            if(action!=null)action.run(); else opener.accept(serverPlayer);
        }
        return InteractionResult.SUCCESS;
    }
    public static boolean isMenu(ItemStack stack) {
        return dev.mobarena.backpack.GuardedItems.hasTag(stack,TAG);
    }
    public static ItemStack createStack() {
        var stack=new ItemStack(Items.RECOVERY_COMPASS);
        var data=new CompoundTag();data.putBoolean(TAG,true);
        stack.set(DataComponents.CUSTOM_DATA,CustomData.of(data));
        stack.set(DataComponents.CUSTOM_NAME,Component.literal("Wave menu"));
        return stack;
    }
    @Override public void give(ServerPlayer player,GameContext ctx,Runnable open) {
        actions.put(player.getUUID(),open);skipped.remove(player.getUUID());
        var inventory=player.getInventory();var old=inventory.getItem(8);
        if(!old.isEmpty() && !isMenu(old)) {
            int free=inventory.getFreeSlot();
            if(free>=0)inventory.setItem(free,old);
            else if(!guard.storeOverflow(player,ctx,old.copy())) {
                skipped.add(player.getUUID());
                player.sendSystemMessage(Component.literal("Your inventory is full. Use /ma waves."));return;
            }
        }
        var stack=createStack();
        inventory.setItem(8,stack);guard.protect(player,stack);inventory.setChanged();
    }
    @Override public boolean skipped(UUID player) { return skipped.contains(player); }
    @Override public void clearSkipped() { skipped.clear(); }
    @Override public void setOpener(java.util.function.Consumer<ServerPlayer> opener) { this.opener=Objects.requireNonNull(opener); }
    @Override public void remove(ServerPlayer player) {
        actions.remove(player.getUUID());
        var inventory=player.getInventory();
        for(int slot=0;slot<inventory.getContainerSize();slot++)
            if(isMenu(inventory.getItem(slot)))inventory.setItem(slot,ItemStack.EMPTY);
        if(isMenu(player.containerMenu.getCarried()))player.containerMenu.setCarried(ItemStack.EMPTY);
        inventory.setChanged();
    }
}
