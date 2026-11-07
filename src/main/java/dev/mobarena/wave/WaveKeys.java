package dev.mobarena.wave;
import dev.mobarena.core.Key;
import dev.mobarena.team.TeamId;
import java.util.*;
public final class WaveKeys {
    private WaveKeys() {}
    public static final Key<EnumMap<TeamId,WaveSetup>> SETUPS=Key.of("wave.setups");
    public static final Key<EnumMap<TeamId,WavePlan>> PLANS=Key.of("wave.plans");
    public static final Key<Set<UUID>> READY=Key.of("wave.ready");
    public static final Key<EnumMap<TeamId,WaveConfigHub>> HUBS=Key.of("wave.hubs");
}
