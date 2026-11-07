package dev.mobarena.core;

import net.minecraft.server.level.ServerPlayer;

public final class Teleports {
    private Teleports() {}

    /**
     * Call after a teleport to another level. Vanilla keeps the support block of the old level
     * (Entity.mainSupportingBlockPos). A ticked player (for example a Carpet fake player) then reads
     * that position in the new level and generates a far chunk on the server thread. Clear it; the
     * next move finds the new support block.
     */
    public static void clearSupport(ServerPlayer player) {
        player.setOnGround(false);
    }
}
