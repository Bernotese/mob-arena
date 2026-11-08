package dev.mobarena.arena;

import dev.mobarena.core.GameContext;
import net.minecraft.gametest.framework.GameTestHelper;
import java.util.UUID;

/** Inspect package-private respawn timers without adding a production test hook. */
public final class ArenaPrepChecks {
    private ArenaPrepChecks() {}
    public static void assertDown(GameTestHelper helper, GameContext context, UUID id) {
        helper.assertTrue(ArenaBattles.get(context).respawns().isDown(id), "Player has a respawn timer");
    }
    public static void assertNotDown(GameTestHelper helper, GameContext context, UUID id) {
        helper.assertTrue(!ArenaBattles.get(context).respawns().isDown(id), "Player has no respawn timer");
    }
}
