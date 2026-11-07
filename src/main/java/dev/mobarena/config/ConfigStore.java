package dev.mobarena.config;
import java.io.IOException;
import java.nio.file.Path;
public final class ConfigStore {
    private final Path path;
    private MobArenaConfig current;
    public ConfigStore(Path path) throws IOException {
        this.path = path;
        try { current = MobArenaConfig.load(path); }
        catch (RuntimeException | IOException e) {
            dev.mobarena.MobArena.LOGGER.error("Cannot load config {}; using defaults and preserving the file", path, e);
            current = new MobArenaConfig();
        }
    }
    /** Defensive editable copy for config menus. */
    public MobArenaConfig current() { return current.copy(); }
    /** Returns null for non-numeric or unknown keys. */
    public ConfigBounds.Bounds bounds(String key) { return ConfigBounds.bounds(key); }
    public void save() throws IOException { current.save(path); }
    public MobArenaConfig snapshot() { return current.copy(); }
    public void set(String key, String value) throws IOException {
        MobArenaConfig next = current.with(key, value); next.save(path); current = next;
    }
    public void reset() throws IOException { MobArenaConfig next = new MobArenaConfig(); next.save(path); current = next; }
}
