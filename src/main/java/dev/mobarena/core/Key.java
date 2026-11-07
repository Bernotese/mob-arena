package dev.mobarena.core;
import java.util.Objects;
/** Identity-based typed attachment key. Share one constant per attachment. */
public final class Key<T> {
    private final String name;
    public Key(String name) { this.name = Objects.requireNonNull(name); }
    public static <T> Key<T> of(String name) { return new Key<>(name); }
    @Override public String toString() { return name; }
}
