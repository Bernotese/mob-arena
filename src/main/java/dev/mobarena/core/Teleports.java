package dev.mobarena.core;

import net.minecraft.server.level.ServerPlayer;

public final class Teleports {
    private Teleports() {}

    /**
     * Call after a teleport to another level. Vanilla keeps the support block of the old level
     * (Entity.mainSupportingBlockPos). A ticked player (for example a Carpet fake player) then reads
     * that position in the new level and generates a far chunk on the server thread. Clear it and update the collision below the feet without loading chunks.
     */
    public static void clearSupport(ServerPlayer player) {
        player.setOnGround(false);
        var box = player.getBoundingBox();
        var feet = new net.minecraft.world.phys.AABB(box.minX, box.minY - 1.0E-6, box.minZ,
                box.maxX, box.minY, box.maxZ);
        player.verticalCollisionBelow = !player.isSpectator()
                && player.level().findSupportingBlock(player, feet).isPresent();
    }
}
