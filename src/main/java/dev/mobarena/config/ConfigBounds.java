package dev.mobarena.config;
import java.util.Map;
/** Shared numeric bounds for JSON, commands and config menus. */
public final class ConfigBounds {
    public record Bounds(int min, int max) {
        public void validate(String key, int value) {
            if (value < min || value > max) throw new IllegalArgumentException(key + " must be between " + min + " and " + max);
        }
    }
    private static final Map<String, Bounds> VALUES = Map.ofEntries(
        Map.entry("prepTimeSeconds", new Bounds(60, 14400)),
        Map.entry("waveConfigTimeSeconds", new Bounds(10, 1800)),
        Map.entry("waveCountdownSeconds", new Bounds(3, 60)),
        Map.entry("waveCooldownSeconds", new Bounds(1, 300)),
        Map.entry("waveMaxTimeSeconds", new Bounds(60, 3600)),
        Map.entry("suddenDeathMaxTimeSeconds", new Bounds(60, 3600)),
        Map.entry("arenaRespawnSeconds", new Bounds(1, 120)),
        Map.entry("mobGlowAfterSeconds", new Bounds(1, 900)),
        Map.entry("maxAliveMobs", new Bounds(10, 500)),
        Map.entry("endScreenSeconds", new Bounds(5, 120)));
    private ConfigBounds() {}
    public static Bounds bounds(String key) { return VALUES.get(key); }
}
