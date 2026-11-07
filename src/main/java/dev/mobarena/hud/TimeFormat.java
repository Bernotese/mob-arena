package dev.mobarena.hud;

import java.util.Locale;

public final class TimeFormat {
    private TimeFormat() {}
    public static String mmss(long ticks) {
        long seconds = Math.max(0, ticks) / 20;
        return seconds >= 3600
                ? String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
                : String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }
}
