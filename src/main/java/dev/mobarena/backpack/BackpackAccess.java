package dev.mobarena.backpack;

import dev.mobarena.team.TeamId;
import net.minecraft.server.level.ServerPlayer;

/** T2 supplies team membership and phase access on the server thread. */
@FunctionalInterface
public interface BackpackAccess {
    /** Return the team, or null outside PREP, WAVE_CONFIG and ARENA. */
    TeamId activeTeam(ServerPlayer player);
}
