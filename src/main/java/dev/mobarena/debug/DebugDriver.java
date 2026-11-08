package dev.mobarena.debug;

import com.google.gson.*;
import dev.mobarena.MobArena;
import dev.mobarena.arena.*;
import dev.mobarena.collect.CollectKeys;
import dev.mobarena.core.*;
import dev.mobarena.result.Outcome;
import dev.mobarena.result.ResultKeys;
import dev.mobarena.result.ResultTracker;
import dev.mobarena.team.*;
import dev.mobarena.wave.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import java.util.*;

/** Development controls. Call on the server thread with the debug property enabled. */
public final class DebugDriver {
    public static final int MAX_COUNT = 10_000;
    private DebugDriver() {}
    public static boolean enabled() { return Boolean.getBoolean("mobarena.debug"); }
    /** Test-only effect retention; ignored unless the debug driver is enabled. */
    public static boolean keepEffects() { return enabled() && Boolean.getBoolean("mobarena.debug.keepEffects"); }
    private static void check() {
        if (!enabled()) throw new IllegalStateException("Set -Dmobarena.debug=true to use the debug driver");
    }
    private static GameContext context() {
        check();
        var ctx = GameManager.get().context();
        if (ctx == null) throw new IllegalStateException("No game is running");
        return ctx;
    }
    private static EntityType<?> type(String id, net.minecraft.server.level.ServerLevel level) {
        var key = Identifier.parse(id);
        var type = BuiltInRegistries.ENTITY_TYPE.getOptional(key).orElseThrow(() -> new IllegalArgumentException("Unknown entity: " + id));
        if (!(type.create(level, EntitySpawnReason.COMMAND) instanceof Mob)) throw new IllegalArgumentException("Entity must be a mob");
        return type;
    }
    private static void positive(int count) {
        if (count <= 0 || count > MAX_COUNT) throw new IllegalArgumentException("Count must be between 1 and " + MAX_COUNT);
    }
    public static void poolAdd(TeamId team, String entity, int count) {
        var ctx = context(); positive(count); type(entity, ctx.server().overworld());
        if (GameManager.get().phase() != Phase.PREP) throw new IllegalStateException("Add pool entries during PREP");
        String id = Identifier.parse(entity).toString();
        var pool = ctx.get(CollectKeys.POOLS).get(team);
        Math.addExact(pool.count(id), count);
        for (int i = 0; i < count; i++) pool.add(id);
    }
    public static void waves(TeamId team, String w1, String w2, String w3) {
        var ctx = context();
        if (GameManager.get().phase() != Phase.WAVE_CONFIG) throw new IllegalStateException("Set waves during WAVE_CONFIG");
        var specs = List.of(WaveSpec.parse(w1), WaveSpec.parse(w2), WaveSpec.parse(w3));
        specs.forEach(w -> w.keySet().forEach(id -> type(id, ctx.server().overworld())));
        var setup = ctx.get(WaveKeys.SETUPS).get(team);
        if (setup.finished()) throw new IllegalStateException("Wave setup is finished");
        var available = new TreeMap<>(setup.pool());
        for (int w = 1; w <= 3; w++) setup.wave(w).forEach((id, n) -> available.merge(id, n, Math::addExact));
        var needed = new TreeMap<String, Integer>();
        specs.forEach(w -> w.forEach((id, n) -> needed.merge(id, n, Math::addExact)));
        needed.forEach((id, n) -> { if (available.getOrDefault(id, 0) < n) throw new IllegalArgumentException("Insufficient pool: " + id); });
        for (int w = 1; w <= 3; w++) for (var e : new TreeMap<>(setup.wave(w)).entrySet()) setup.toPool(w, e.getKey(), e.getValue());
        for (int w = 1; w <= 3; w++) for (var e : specs.get(w - 1).entrySet()) setup.toWave(e.getKey(), w, e.getValue());
        ctx.get(WaveKeys.HUBS).get(team).dirty();
    }
    public static void kill(ServerPlayer player, String entity, int count) {
        check(); positive(count);
        var type = type(entity, player.level());
        for (int i = 0; i < count; i++) {
            var mob = (Mob) type.create(player.level(), EntitySpawnReason.COMMAND);
            if (mob == null) throw new IllegalStateException("Cannot create entity");
            mob.snapTo(player.getX(), player.getY(), player.getZ(), 0, 0);
            if (!player.level().addFreshEntity(mob)) throw new IllegalStateException("Cannot spawn entity");
            mob.hurtServer(player.level(), mob.damageSources().playerAttack(player), Float.MAX_VALUE);
        }
    }
    public static void down(ServerPlayer player) {
        check();
        player.hurtServer(player.level(), player.damageSources().genericKill(), Float.MAX_VALUE);
    }
    public static void clearwave(TeamId team) {
        var battles = ArenaBattles.get(context());
        var battle = battles == null ? null : battles.battle(team);
        if (battle == null || battle.currentTag() == null) throw new IllegalStateException("No current wave");
        battle.clearQueue();
        for (var mob : battle.waveMobs()) mob.hurtServer((net.minecraft.server.level.ServerLevel) mob.level(), mob.damageSources().genericKill(), Float.MAX_VALUE);
    }
    public static void timer(int seconds) {
        var ctx = context();
        var phase = GameManager.get().phase();
        if (phase == Phase.ARENA || phase == Phase.SUDDEN_DEATH)
            throw new IllegalStateException("The phase timer cannot control arena battles");
        if (seconds < 0) throw new IllegalArgumentException("Seconds must be nonnegative");
        ctx.timer().reset(seconds * 20L);
    }
    /** Per team: {"arena": outcome, "suddenDeath": outcome or null}; null without a ResultTracker. */
    static JsonElement outcomes(GameContext ctx) {
        if (ctx == null || !(ctx.get(ResultHooks.KEY) instanceof ResultTracker tracker)) return JsonNull.INSTANCE;
        var json = new JsonObject();
        for (var team : TeamId.values()) {
            var t = new JsonObject();
            t.add("arena", outcome(tracker.arenaOutcome(team)));
            t.add("suddenDeath", tracker.suddenDeathOutcome(team).map(DebugDriver::outcome).orElse(JsonNull.INSTANCE));
            json.add(team.name(), t);
        }
        return json;
    }
    static JsonElement outcome(Outcome outcome) {
        var json = new JsonObject();
        switch (outcome) {
            case Outcome.Running running -> json.addProperty("type", "RUNNING");
            case Outcome.Finished finished -> { json.addProperty("type", "FINISHED"); json.addProperty("ticks", finished.ticks()); }
            case Outcome.Eliminated eliminated -> {
                json.addProperty("type", "ELIMINATED");
                json.add("reason", eliminated.reason() == null ? JsonNull.INSTANCE : new JsonPrimitive(eliminated.reason().name()));
                json.addProperty("clearedWaves", eliminated.progress().clearedWaves());
                json.addProperty("killsInCurrentWave", eliminated.progress().killsInCurrentWave());
                json.addProperty("ticks", eliminated.progress().ticks());
            }
        }
        return json;
    }
    /** {"type": PENDING|SUDDEN_DEATH|WIN|DRAW, "winner": team or null, "reason": text or null}; null without a game. */
    static JsonElement verdict(GameManager game, GameContext ctx) {
        if (ctx == null) return JsonNull.INSTANCE;
        var json = new JsonObject();
        var result = ctx.get(ResultKeys.RESULT);
        String type = result != null ? (result.winner().isPresent() ? "WIN" : "DRAW")
                : game.phase() == Phase.SUDDEN_DEATH ? "SUDDEN_DEATH" : "PENDING";
        json.addProperty("type", type);
        json.add("winner", result == null || result.winner().isEmpty() ? JsonNull.INSTANCE : new JsonPrimitive(result.winner().get().name()));
        json.add("reason", result == null ? JsonNull.INSTANCE : new JsonPrimitive(result.reason()));
        return json;
    }
    /** Stable JSON fields use ticks for all times. Also emit the script log marker. */
    public static JsonObject state(MinecraftServer server) {
        check();
        var game = GameManager.get(); var ctx = game.context();
        var json = new JsonObject();
        json.addProperty("phase", game.phase().name());
        json.add("timer", ctx == null ? JsonNull.INSTANCE : new JsonPrimitive(ctx.timer().remainingTicks()));
        var teams = new JsonObject(); var pools = new JsonObject(); var plans = new JsonObject(); var battles = new JsonObject();
        var assignments = ctx == null ? TeamManager.get().assignments() : ctx.teams();
        for (var team : TeamId.values()) {
            var members = new JsonArray();
            assignments.entrySet().stream().filter(e -> e.getValue() == team).map(e -> e.getKey().toString()).sorted().forEach(members::add);
            teams.add(team.name(), members);
            pools.addProperty(team.name(), ctx == null || ctx.get(CollectKeys.POOLS) == null ? 0 : ctx.get(CollectKeys.POOLS).get(team).total());
            var gson = new Gson();
            var plan = ctx == null || ctx.get(WaveKeys.PLANS) == null ? null : ctx.get(WaveKeys.PLANS).get(team);
            var setup = ctx == null || ctx.get(WaveKeys.SETUPS) == null ? null : ctx.get(WaveKeys.SETUPS).get(team);
            plans.add(team.name(), gson.toJsonTree(plan != null ? plan.waves() : setup == null ? null : setup.snapshot()));
            var group = ArenaBattles.get(ctx); var battle = group == null ? null : group.battle(team);
            var b = new JsonObject();
            if (battle != null) {
                b.addProperty("state", battle.state().name()); b.addProperty("tag", battle.currentTag());
                b.addProperty("alive", battle.waveMobs().size()); b.addProperty("kills", battle.kills());
                b.add("reason", battle.eliminationReason().<JsonElement>map(r -> new JsonPrimitive(r.name())).orElse(JsonNull.INSTANCE));
            }
            battles.add(team.name(), battle == null ? JsonNull.INSTANCE : b);
        }
        json.add("teams", teams); json.add("pools", pools); json.add("plans", plans); json.add("battles", battles);
        json.add("outcomes", outcomes(ctx)); json.add("verdict", verdict(game, ctx));
        var levels = new JsonArray();
        for (var level : server.getAllLevels()) levels.add(level.dimension().identifier().toString());
        json.add("openLevels", levels);
        MobArena.LOGGER.info("MOBARENA_STATE {}", json);
        return json;
    }
}
