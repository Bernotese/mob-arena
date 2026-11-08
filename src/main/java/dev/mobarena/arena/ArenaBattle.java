package dev.mobarena.arena;

import dev.mobarena.core.EliminationReason;
import dev.mobarena.hud.HudLine;
import dev.mobarena.hud.HudOverrides;
import dev.mobarena.hud.TimeFormat;
import dev.mobarena.hud.Titles;
import dev.mobarena.hud.GameSound;
import dev.mobarena.hud.Sounds;
import dev.mobarena.team.TeamId;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent.BossBarColor;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import java.util.function.Supplier;

/** One team's arena run: BattleFlow timing plus the Minecraft side effects. Server thread only. */
public final class ArenaBattle {
    private EliminationReason eliminationReason;
    final ArenaBattles group;
    final TeamId team;
    private final Supplier<Optional<Arena>> arenaSource;
    final BattleScript script;
    final BattleListener listener;
    final BattleFlow flow;
    final boolean suddenDeath;
    private Arena arena;
    private SpawnQueue queue;
    private String currentTag;
    private int kills;
    private boolean glow;
    private long fightStart;
    private boolean stopped;

    ArenaBattle(ArenaBattles group, TeamId team, Supplier<Optional<Arena>> arenaSource, BattleScript script,
                BattleListener listener, boolean suddenDeath) {
        this.group = group; this.team = team; this.arenaSource = arenaSource; this.script = script;
        this.listener = listener; this.suddenDeath = suddenDeath; this.flow = new BattleFlow(script);
    }

    public TeamId team() { return team; }
    public BattleFlow.State state() { return flow.state(); }
    public Optional<Arena> arena() { return Optional.ofNullable(arena); }
    /** Gets the arena from the source once it exists. */
    Optional<Arena> loadArena() {
        if (arena == null && !stopped) {
            arena = arenaSource.get().orElse(null);
            if (arena != null) group.register(arena.level(), this);
        }
        return arena();
    }
    public String currentTag() { return currentTag; }
    public int kills() { return kills; }
    public boolean glowing() { return glow; }
    /** Why the battle ended in ELIMINATED; empty while it runs or after it finished. */
    public Optional<EliminationReason> eliminationReason() { return Optional.ofNullable(eliminationReason); }
    /** True while mobs and deaths still matter (prep, countdown, fight, cooldown). */
    public boolean running() {
        var state = flow.state();
        return !stopped && state != BattleFlow.State.WAITING_ARENA && state != BattleFlow.State.FINISHED
                && state != BattleFlow.State.ELIMINATED;
    }
    boolean waiting() { return !stopped && flow.state() == BattleFlow.State.WAITING_ARENA; }

    void tick(long now) {
        if (stopped || flow.state() == BattleFlow.State.FINISHED || flow.state() == BattleFlow.State.ELIMINATED) {
            if (!stopped && now % 20 == 0) hud();
            return;
        }
        loadArena();
        boolean ready = arena != null;
        int alive = 0;
        boolean spawningDone = true;
        if (ready && currentTag != null) {
            // Order (T7a review): scan, reserve with tick(alive), spawn, then use the estimate.
            alive = countAlive();
            if (queue != null) {
                var batch = queue.tick(alive);
                int failed = 0;
                for (var order : batch) if (!WaveSpawner.spawn(this, order)) failed++;
                alive = queue.aliveEstimate() - failed;
                spawningDone = queue.spawningDone();
            }
        }
        if (ready && currentTag != null) WaveMobAi.dropWaveTargets(this);
        if (ready && currentTag != null && now % 20 == 0) WaveMobAi.retarget(this);
        if (ready && now % 5 == 0) ArenaBounds.check(this);
        boolean wasPrep = flow.state() == BattleFlow.State.PREP;
        boolean wasWaiting = flow.state() == BattleFlow.State.WAITING_ARENA;
        var input = new TickInput(ready, spawningDone, Math.max(0, alive), kills,
                group.allDown(this), group.anyOnline(team));
        var events = flow.tick(input);
        if (wasWaiting && flow.state() != BattleFlow.State.WAITING_ARENA) onReady();
        if (wasPrep && flow.state() == BattleFlow.State.COUNTDOWN) onPrepEnd();
        for (var event : events) handle(event, now);
        if (now % 20 == 0) hud();
    }

    private void onReady() {
        group.respawns().cancelTeam(team);
        for (ServerPlayer player : group.onlinePlayers(team)) placeAtSpawn(player);
    }

    private void onPrepEnd() {
        for (var player : group.onlinePlayers(team)) {
            if (group.respawns().isDown(player.getUUID())) placeAtSpawn(player);
        }
        group.respawns().cancelTeam(team);
    }

    public int prepLeftTicks() { return flow.prepLeftTicks(); }
    public void setPrepLeft(int ticks) { flow.setPrepLeft(ticks); }

    void placeAtSpawn(ServerPlayer player) {
        placeAt(player, Vec3.atBottomCenterOf(arena.layout().teamSpawn()));
    }

    /** Rejoin only: prefer a supported, unoccupied position within three blocks of the spawn. */
    void placeRejoiningPlayer(ServerPlayer player) {
        Vec3 center = Vec3.atBottomCenterOf(arena.layout().teamSpawn());
        var level = arena.level();
        for (int radius = 0; radius <= 3; radius++) {
            for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
                if (Math.max(Math.abs(x), Math.abs(z)) != radius) continue;
                var target = center.add(x, 0, z);
                var box = player.getBoundingBox().move(target.subtract(player.position()));
                var feet = new net.minecraft.world.phys.AABB(box.minX, box.minY - 1.0E-6, box.minZ,
                        box.maxX, box.minY, box.maxZ);
                if (level.findSupportingBlock(player, feet).isEmpty() || !level.noCollision(player, box)) continue;
                boolean occupied = level.players().stream().anyMatch(other -> other != player
                        && (other.position().distanceToSqr(target) < 1.0 || other.getBoundingBox().intersects(box)));
                if (!occupied) {
                    placeAt(player, target);
                    return;
                }
            }
        }
        placeAt(player, center);
    }

    private void placeAt(ServerPlayer player, Vec3 spawn) {
        player.stopRiding();
        player.teleportTo(arena.level(), spawn.x, spawn.y, spawn.z, Set.of(), player.getYRot(), 0, true);
        player.setGameMode(GameType.SURVIVAL);
        dev.mobarena.core.Teleports.clearSupport(player);
        if (player.getAbilities().invulnerable) { player.getAbilities().invulnerable = false; player.onUpdateAbilities(); }
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(5);
        player.clearFire();
        player.resetFallDistance();
        player.setDeltaMovement(Vec3.ZERO);
    }

    private void handle(BattleEvent event, long now) {
        switch (event) {
            case BattleEvent.Prep p -> {
                title(Component.literal("Get ready"), Component.literal("Wave 1 in " + p.secondsLeft() + " s"), 0, 40, 10);
                hud();
            }
            case BattleEvent.Countdown c -> {
                if (c.secondsLeft() > 0) sound(GameSound.COUNTDOWN);
                title(Component.literal(String.valueOf(c.secondsLeft())), Component.literal(waveName(c.wave())), 0, 25, 5);
            }
            case BattleEvent.SpawnWave s -> spawnWave(s.wave(), now);
            case BattleEvent.Glow g -> {
                glow = true;
                for (var mob : waveMobs()) mob.setGlowingTag(true);
            }
            case BattleEvent.WaveCleared c -> {
                if (c.wave() < script.waves().size()) sound(GameSound.WAVE_CLEARED);
                queue = null;
                currentTag = null;
                title(Component.literal(waveName(c.wave()) + " cleared"), cooldownText(c.wave()), 5, 40, 10);
            }
            case BattleEvent.Finished f -> {
                sound(GameSound.FINISHED);
                title(Component.literal("All waves cleared"), Component.literal("Time " + TimeFormat.mmss(f.ticks())), 5, 60, 10);
                listener.finished(team, f.ticks());
            }
            case BattleEvent.Eliminated e -> {
                eliminationReason = e.reason();
                discardWaveMobs();
                queue = null;
                currentTag = null;
                title(Component.literal("Eliminated"), Component.literal(reasonText(e.reason())), 5, 60, 10);
                group.onEliminated(this);
                sound(GameSound.ELIMINATED);
                listener.eliminated(team, e.progress(), e.reason());
            }
        }
    }

    private void spawnWave(int wave, long now) {
        sound(GameSound.GO);
        int tagWave = suddenDeath ? WaveTags.SUDDEN_DEATH_WAVE : wave;
        currentTag = WaveTags.tag(team, tagWave);
        kills = 0;
        glow = false;
        fightStart = now;
        var layout = arena.layout();
        long seed = group.ctx().seed() ^ ((long) team.ordinal() << 32) ^ tagWave;
        var orders = SpawnPlanner.plan(script.waves().get(wave - 1), layout.mobSpawns().size(),
                layout.waterSpawns().size(), WaveSpawner::isWater, seed);
        queue = new SpawnQueue(orders, group.maxAliveMobs());
        if (!orders.isEmpty()) title(Component.literal("Go"), Component.literal(waveName(wave) + " / " + orders.size() + " mobs"), 0, 30, 10);
    }

    void countKill(LivingEntity entity) {
        if (currentTag != null && entity.entityTags().contains(currentTag)) {
            kills++;
            listener.kill(team, kills);
        }
    }

    /**
     * Wave mobs that are not removed yet. Dying bodies count: a slime splits only when its body is
     * removed (about 20 ticks after death), so the wave must not clear before the children exist.
     */
    int countAlive() {
        if (arena == null || currentTag == null) return 0;
        String tag = currentTag;
        return arena.level().getEntities(EntityTypeTest.forClass(LivingEntity.class),
                e -> !e.isRemoved() && e.entityTags().contains(tag)).size();
    }

    /** Alive mobs of the current wave in this arena. */
    public List<? extends LivingEntity> waveMobs() {
        if (arena == null || currentTag == null) return List.of();
        String tag = currentTag;
        return arena.level().getEntities(EntityTypeTest.forClass(LivingEntity.class),
                e -> e.isAlive() && e.entityTags().contains(tag));
    }

    /** Remove all wave mobs of this team (any wave) from the arena. */
    void discardWaveMobs() {
        if (arena == null) return;
        String prefix = WaveTags.teamPrefix(team);
        for (var entity : arena.level().getEntities(EntityTypeTest.forClass(net.minecraft.world.entity.Entity.class),
                e -> e.entityTags().stream().anyMatch(t -> t.startsWith(prefix)))) discardNoSplit(entity);
    }

    /** discard() of a dying slime or magma cube splits it (AbstractCubeMob.remove), so shrink it first. */
    static void discardNoSplit(net.minecraft.world.entity.Entity entity) {
        if (entity instanceof net.minecraft.world.entity.monster.cubemob.AbstractCubeMob cube) cube.setSize(1, false);
        entity.discard();
    }

    void stop() {
        if (stopped) return;
        discardWaveMobs();
        stopped = true;
        queue = null;
        currentTag = null;
        group.unregister(this);
        HudOverrides.of(group.ctx()).clear(team);
    }

    /** Debug driver hook: cancel pending spawns of the current wave. */
    public void clearQueue() { queue = null; }

    int remainingInQueue() { return queue == null ? 0 : queue.remaining(); }

    private void hud() {
        var state = flow.state();
        int waves = script.waves().size();
        String waveText = suddenDeath ? "Sudden death" : "Wave " + Math.max(1, flow.wave()) + "/" + waves;
        BossBarColor color = suddenDeath ? BossBarColor.PURPLE : BossBarColor.RED;
        long now = group.ctx().server().getTickCount();
        HudLine line = switch (state) {
            case WAITING_ARENA -> new HudLine(Component.literal("Arena – waiting for the arena"), 1, color);
            case PREP -> new HudLine(Component.literal("Get ready – " + TimeFormat.mmss(flow.prepLeftTicks())),
                    (float) flow.prepLeftTicks() / script.prepTicks(), color);
            case COUNTDOWN -> new HudLine(Component.literal(waveText + " – get ready"), 1, color);
            case FIGHT -> {
                long left = Math.max(0, script.maxWaveTicks() - (now - fightStart));
                int mobs = countAlive() + remainingInQueue();
                yield new HudLine(Component.literal(waveText + " – " + mobs + " mobs left – " + TimeFormat.mmss(left)),
                        (float) left / script.maxWaveTicks(), color);
            }
            case COOLDOWN -> new HudLine(Component.literal("Cooldown – " + waveName(flow.wave() + 1) + " next"), 1, BossBarColor.GREEN);
            case FINISHED -> new HudLine(Component.literal("Finished – " + TimeFormat.mmss(flow.elapsedTicks())), 1, BossBarColor.GREEN);
            case ELIMINATED -> new HudLine(Component.literal("Eliminated – " + flow.progress().clearedWaves() + " waves cleared"), 0, BossBarColor.WHITE);
        };
        HudOverrides.of(group.ctx()).set(team, line);
    }

    private String waveName(int wave) { return suddenDeath ? "Sudden death" : "Wave " + wave; }
    private Component cooldownText(int wave) {
        if (wave >= script.waves().size()) return Component.empty();
        return Component.literal("Next wave in " + (script.cooldownTicks() + script.countdownTicks() + 19) / 20 + " s");
    }
    private static String reasonText(EliminationReason reason) {
        return switch (reason) {
            case TIME -> "Time is up";
            case ALL_DOWN -> "All players are down";
            case NO_PLAYERS -> "No team player is online";
        };
    }
    private void sound(GameSound sound) { Sounds.playAll(group.onlinePlayers(team), sound); }
    private void title(Component title, Component subtitle, int in, int stay, int out) {
        Titles.showAll(group.onlinePlayers(team), title, subtitle, in, stay, out);
    }
}
