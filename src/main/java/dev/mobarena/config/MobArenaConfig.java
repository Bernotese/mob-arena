package dev.mobarena.config;
import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
public final class MobArenaConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public int prepTimeSeconds = 2700;
    public int waveConfigTimeSeconds = 180;
    public int waveCountdownSeconds = 10;
    public int waveCooldownSeconds = 20;
    public int waveMaxTimeSeconds = 900;
    public int maxAliveMobs = 150;
    public int suddenDeathMaxTimeSeconds = 600;
    public int arenaRespawnSeconds = 10;
    public int mobGlowAfterSeconds = 60;
    public int endScreenSeconds = 15;
    public boolean randomizerEnabled = true;
    public List<String> mobBlacklist = new ArrayList<>(List.of("ender_dragon", "player", "armor_stand", "cod", "salmon", "tropical_fish", "pufferfish"));
    public String arenaTemplate = "default";
    public record Snapshot(int prepTimeSeconds, int waveConfigTimeSeconds, int waveCountdownSeconds,
            int waveCooldownSeconds, int waveMaxTimeSeconds, int suddenDeathMaxTimeSeconds,
            int arenaRespawnSeconds, int mobGlowAfterSeconds, int endScreenSeconds, int maxAliveMobs,
            boolean randomizerEnabled, List<String> mobBlacklist, String arenaTemplate) {
        public Snapshot { mobBlacklist = List.copyOf(mobBlacklist); }
    }
    public Snapshot snapshot() {
        validate();
        return new Snapshot(prepTimeSeconds, waveConfigTimeSeconds, waveCountdownSeconds, waveCooldownSeconds,
            waveMaxTimeSeconds, suddenDeathMaxTimeSeconds, arenaRespawnSeconds, mobGlowAfterSeconds,
            endScreenSeconds, maxAliveMobs, randomizerEnabled, mobBlacklist, arenaTemplate);
    }
    public MobArenaConfig copy() { return GSON.fromJson(GSON.toJson(this), MobArenaConfig.class); }
    public static MobArenaConfig load(Path path) throws IOException {
        if (!Files.exists(path)) { MobArenaConfig c = new MobArenaConfig(); c.save(path); return c; }
        try (Reader reader = Files.newBufferedReader(path)) {
            MobArenaConfig c = GSON.fromJson(reader, MobArenaConfig.class);
            if (c == null) throw new IllegalArgumentException("Empty config");
            c.validate(); return c;
        }
    }
    public void save(Path path) throws IOException {
        validate(); Files.createDirectories(path.toAbsolutePath().getParent());
        Path temp = Files.createTempFile(path.toAbsolutePath().getParent(), "mobarena", ".json");
        try { Files.writeString(temp, GSON.toJson(this)); Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING); }
        finally { Files.deleteIfExists(temp); }
    }
    public void validate() {
        for (var field : getClass().getFields()) try {
            if (field.getType() == int.class && field.getInt(this) < 1) throw new IllegalArgumentException(field.getName() + " must be positive");
        } catch (IllegalAccessException e) { throw new IllegalStateException(e); }
        if (arenaTemplate == null || !arenaTemplate.matches("[a-zA-Z0-9_-]+")) throw new IllegalArgumentException("Invalid arena template");
        if (mobBlacklist == null || mobBlacklist.stream().anyMatch(s -> s == null || !s.matches("([a-z0-9_.-]+:)?[a-z0-9_./-]+"))) throw new IllegalArgumentException("Invalid mob blacklist");
    }
    public String get(String key) {
        JsonElement value = GSON.toJsonTree(this).getAsJsonObject().get(key);
        if (value == null) throw new IllegalArgumentException("Unknown config key: " + key);
        return value.toString();
    }
    public MobArenaConfig with(String key, String value) {
        JsonObject json = GSON.toJsonTree(this).getAsJsonObject();
        if (!json.has(key)) throw new IllegalArgumentException("Unknown config key: " + key);
        JsonElement old = json.get(key);
        JsonElement parsed;
        if (key.equals("arenaTemplate")) parsed = new JsonPrimitive(value);
        else if (key.equals("mobBlacklist")) parsed = JsonParser.parseString(value);
        else if (old.getAsJsonPrimitive().isBoolean()) {
            if (!value.equals("true") && !value.equals("false")) throw new IllegalArgumentException("Use true or false");
            parsed = new JsonPrimitive(Boolean.parseBoolean(value));
        } else parsed = new JsonPrimitive(Integer.parseInt(value));
        json.add(key, parsed);
        MobArenaConfig result = GSON.fromJson(json, MobArenaConfig.class); result.validate(); return result;
    }
}
