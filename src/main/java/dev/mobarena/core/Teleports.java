package dev.mobarena.core;

import net.minecraft.server.level.ServerPlayer;

public final class Teleports {
    private static final java.util.Map<ServerPlayer, Integer> lastTeleportTicks = new java.util.WeakHashMap<>();
    private Teleports() {}

    /** Server ticks since the last mod teleport, or -1 if none was recorded for this player entity. */
    public static long ticksSinceLastTeleport(ServerPlayer player) {
        var tick = lastTeleportTicks.get(player);
        return tick == null ? -1 : (long) player.level().getServer().getTickCount() - tick;
    }

    /**
     * Call after a teleport to another level. Vanilla keeps the support block of the old level
     * (Entity.mainSupportingBlockPos). A ticked player (for example a Carpet fake player) then reads
     * that position in the new level and generates a far chunk on the server thread. Clear it and update the collision below the feet without loading chunks.
     */
    public static void clearSupport(ServerPlayer player) {
        lastTeleportTicks.put(player, player.level().getServer().getTickCount());
        player.setOnGround(false);
        var box = player.getBoundingBox();
        var feet = new net.minecraft.world.phys.AABB(box.minX, box.minY - 1.0E-6, box.minZ,
                box.maxX, box.minY, box.maxZ);
        player.verticalCollisionBelow = !player.isSpectator()
                && player.level().findSupportingBlock(player, feet).isPresent();
    }
}
