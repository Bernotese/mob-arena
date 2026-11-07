package dev.mobarena.wave;

import java.util.*;

/** The enemy of author fights these waves. A is the caller's team identifier type. */
public record WavePlan<A>(A author, List<SortedMap<String, Integer>> waves) {
    public WavePlan {
        Objects.requireNonNull(author);
        if (waves.size() != WaveSetup.WAVES) throw new IllegalArgumentException("Exactly three waves required");
        waves = waves.stream().map(wave -> {
            wave.forEach((id, count) -> {
                Objects.requireNonNull(id);
                if (count == null || count <= 0) throw new IllegalArgumentException("Counts must be positive");
            });
            return WaveSetup.copy(wave);
        }).toList();
    }
}
