package dev.mobarena.arena;

import dev.mobarena.team.TeamId;
import java.util.Locale;
import java.util.Optional;

/** Scoreboard tags of wave mobs: mobarena.wave.<fighting team>.<wave>. Sudden death uses wave 4. */
public final class WaveTags {
    public static final String PREFIX = "mobarena.wave.";
    public static final int SUDDEN_DEATH_WAVE = 4;
    public record Parsed(TeamId team, int wave) {}
    private WaveTags() {}

    public static String tag(TeamId team, int wave) {
        if (wave < 1) throw new IllegalArgumentException("Wave starts at 1");
        return teamPrefix(team) + wave;
    }
    public static String teamPrefix(TeamId team) { return PREFIX + team.name().toLowerCase(Locale.ROOT) + "."; }

    public static Optional<Parsed> parse(String tag) {
        if (tag == null || !tag.startsWith(PREFIX)) return Optional.empty();
        String[] parts = tag.substring(PREFIX.length()).split("\\.", -1);
        if (parts.length != 2) return Optional.empty();
        try {
            TeamId team = TeamId.valueOf(parts[0].toUpperCase(Locale.ROOT));
            if (!parts[0].equals(parts[0].toLowerCase(Locale.ROOT))) return Optional.empty();
            int wave = Integer.parseInt(parts[1]);
            return wave >= 1 && String.valueOf(wave).equals(parts[1]) ? Optional.of(new Parsed(team, wave)) : Optional.empty();
        } catch (IllegalArgumentException e) { return Optional.empty(); }
    }
    public static boolean isWaveTag(String tag) { return parse(tag).isPresent(); }
    public static boolean isWaveEntity(net.minecraft.world.entity.Entity entity) {
        return entity != null && entity.entityTags().stream().anyMatch(WaveTags::isWaveTag);
    }
}
