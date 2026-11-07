package dev.mobarena.collect;
import java.util.*;
import java.util.stream.Collectors;
/** Normalize config ids without loading Minecraft registries. */
public final class BlacklistIds {
    private BlacklistIds() {}
    public static String normalize(String id) {
        String value = Objects.requireNonNull(id).trim();
        return value.contains(":") ? value : "minecraft:" + value;
    }
    public static Set<String> normalize(Collection<String> ids) {
        return ids.stream().map(BlacklistIds::normalize).collect(Collectors.toUnmodifiableSet());
    }
}
