package dev.mobarena.randomizer;

import java.util.Optional;
import net.minecraft.server.level.ServerLevel;

/** T2/T5 supplies the team key for each game level, including the Nether. */
@FunctionalInterface
public interface TeamWorldLookup {
    Optional<String> teamFor(ServerLevel level);
}
