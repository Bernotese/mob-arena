package dev.mobarena.debug;

import java.util.*;

/** Pure parser. An empty string or '-' denotes an empty wave. */
public final class WaveSpec {
    private WaveSpec() {}
    public static SortedMap<String, Integer> parse(String spec) {
        var result = new TreeMap<String, Integer>();
        if (spec.isEmpty() || spec.equals("-")) return Collections.unmodifiableSortedMap(result);
        for (String entry : spec.split(",", -1)) {
            int colon = entry.lastIndexOf(':');
            if (colon < 1) throw new IllegalArgumentException("Use entity:count for each wave entry");
            String id = entry.substring(0, colon);
            if (!id.contains(":")) id = "minecraft:" + id;
            if (!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) throw new IllegalArgumentException("Invalid entity identifier");
            int count = Integer.parseInt(entry.substring(colon + 1));
            if (count <= 0) throw new IllegalArgumentException("Count must be positive");
            result.merge(id, count, Math::addExact);
        }
        return Collections.unmodifiableSortedMap(result);
    }
}
