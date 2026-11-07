package dev.mobarena.config.gui;

/** Temporary adapter for ConfigStore.bounds until the T2 bounds contract is available. */
@FunctionalInterface
public interface ConfigBounds {
    record Range(int min, int max) {}
    Range bounds(String key);
    static ConfigBounds spec() {
        return key -> switch (key) {
            case "prepTimeSeconds" -> new Range(60, 14400);
            case "waveConfigTimeSeconds" -> new Range(10, 1800);
            case "waveCountdownSeconds" -> new Range(3, 60);
            case "waveCooldownSeconds" -> new Range(1, 300);
            case "waveMaxTimeSeconds", "suddenDeathMaxTimeSeconds" -> new Range(60, 3600);
            case "arenaRespawnSeconds" -> new Range(1, 120);
            case "mobGlowAfterSeconds" -> new Range(1, 900);
            case "maxAliveMobs" -> new Range(10, 500);
            case "endScreenSeconds" -> new Range(5, 120);
            default -> throw new IllegalArgumentException("No numeric bounds: " + key);
        };
    }
}
