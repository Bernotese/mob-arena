package dev.mobarena.wave;
import dev.mobarena.core.GameContext;
import net.minecraft.server.level.ServerPlayer;
/** T4 installs the guarded menu item adapter at integration. */
public interface WaveMenuItems {
    WaveMenuItems NONE = new WaveMenuItems() {};
    default void give(ServerPlayer player, GameContext ctx, Runnable open) {}
    default void remove(ServerPlayer player) {}
}
