package dev.mobarena.arena;

public record TickInput(boolean arenaReady, boolean spawningDone, int alive, int killsInWave,
                        boolean allDown, boolean anyOnline) {
    public TickInput {
        if (alive < 0 || killsInWave < 0) throw new IllegalArgumentException("Negative count");
    }
}
