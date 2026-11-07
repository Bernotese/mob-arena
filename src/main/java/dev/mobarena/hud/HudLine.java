package dev.mobarena.hud;

import java.util.Objects;
import net.minecraft.network.chat.Component;
import net.minecraft.world.BossEvent.BossBarColor;

public record HudLine(Component text, float progress, BossBarColor color) {
    public HudLine {
        Objects.requireNonNull(text);
        Objects.requireNonNull(color);
        progress = Float.isFinite(progress) ? Math.clamp(progress, 0, 1) : 0;
    }
}
