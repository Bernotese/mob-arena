package dev.mobarena.backpack;

import net.minecraft.server.level.ServerPlayer;

/** T2 supplies team membership and phase access on the server thread. */
@FunctionalInterface
public interface BackpackAccess {
    /** Return a stable team key, or null outside PREP, WAVE_CONFIG and ARENA. */
    String activeTeam(ServerPlayer player);
}
