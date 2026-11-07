package dev.mobarena.core;
public final class GameClock {
    private long remainingTicks;
    private long elapsedTicks;
    public void reset(long ticks) { remainingTicks = ticks; elapsedTicks = 0; }
    public void tick() { elapsedTicks++; if (remainingTicks > 0) remainingTicks--; }
    public long remainingTicks() { return remainingTicks; }
    public long elapsedTicks() { return elapsedTicks; }
    public boolean expired() { return remainingTicks == 0; }
}
