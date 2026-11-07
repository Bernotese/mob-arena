package dev.mobarena.wave;
import dev.mobarena.core.GameContext;
import net.minecraft.server.level.ServerPlayer;
/** T4 installs the guarded menu item adapter at integration. */
public interface WaveMenuItems {
    WaveMenuItems NONE = new WaveMenuItems() {};
    default void give(ServerPlayer player, GameContext ctx, Runnable open) {}
    default void remove(ServerPlayer player) {}
    /** True if give() skipped this player in the current wave setup. */
    default boolean skipped(java.util.UUID player) { return false; }
    default void clearSkipped() {}
    /** Fallback for menu items without a stored open action (for example after a rejoin). */
    default void setOpener(java.util.function.Consumer<ServerPlayer> opener) {}
}
