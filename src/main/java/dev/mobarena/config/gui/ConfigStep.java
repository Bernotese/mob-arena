package dev.mobarena.config.gui;

public record ConfigStep(int step, int min, int max) {
    public ConfigStep {
        if (step <= 0 || min > max) throw new IllegalArgumentException("Invalid step bounds");
    }
    public int apply(int value, boolean right, boolean shift) {
        long change = (long) step * (shift ? 10 : 1) * (right ? -1 : 1);
        return (int) Math.clamp((long) value + change, min, max);
    }
    public static boolean toggle(boolean value) { return !value; }
}
