package dev.mobarena.config;
import java.io.IOException;
import java.nio.file.Path;
public final class ConfigStore {
    private final Path path;
    private MobArenaConfig current;
    public ConfigStore(Path path) throws IOException { this.path = path; current = MobArenaConfig.load(path); }
    /** Defensive editable copy for config menus. */
    public MobArenaConfig current() { return current.copy(); }
    public void save() throws IOException { current.save(path); }
    public MobArenaConfig snapshot() { return current.copy(); }
    public void set(String key, String value) throws IOException {
        MobArenaConfig next = current.with(key, value); next.save(path); current = next;
    }
    public void reset() throws IOException { MobArenaConfig next = new MobArenaConfig(); next.save(path); current = next; }
}
