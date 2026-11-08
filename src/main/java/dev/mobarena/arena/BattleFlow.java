package dev.mobarena.arena;

import dev.mobarena.core.EliminationReason;
import dev.mobarena.core.TeamProgress;
import java.util.*;

/** One tick call advances time by one tick after arena readiness. */
public final class BattleFlow {
    public enum State { WAITING_ARENA, PREP, COUNTDOWN, FIGHT, COOLDOWN, FINISHED, ELIMINATED }
    private final BattleScript script;
    private State state = State.WAITING_ARENA;
    private int wave, stateTicks, cleared, kills;
    private long elapsed;
    private boolean glowing;

    public BattleFlow(BattleScript script) { this.script = Objects.requireNonNull(script); }
    public State state() { return state; }
    public int wave() { return wave; }
    public long elapsedTicks() { return elapsed; }
    public TeamProgress progress() { return new TeamProgress(cleared, kills, elapsed); }

    public List<BattleEvent> tick(TickInput in) {
        Objects.requireNonNull(in);
        if (state == State.FINISHED || state == State.ELIMINATED) return List.of();
        var events = new ArrayList<BattleEvent>();
        if (state != State.WAITING_ARENA && state != State.PREP) {
            elapsed++;
            stateTicks++;
        }
        if (state == State.WAITING_ARENA) {
            if (!in.arenaReady()) return List.of();
            wave = 1;
            stateTicks = 0;
            if (script.prepTicks() > 0) {
                state = State.PREP;
                return List.of(new BattleEvent.Prep((script.prepTicks() + 19) / 20));
            }
            state = State.COUNTDOWN;
            events.add(new BattleEvent.Countdown(1, (script.countdownTicks() + 19) / 20));
            return List.copyOf(events);
        }
        if (state == State.PREP) {
            if (++stateTicks < script.prepTicks()) return List.of();
            state = State.COUNTDOWN;
            stateTicks = 0;
            return List.of(new BattleEvent.Countdown(1, (script.countdownTicks() + 19) / 20));
        }
        if (state == State.FIGHT) kills = in.killsInWave();
        if (!in.anyOnline() || in.allDown()) {
            eliminate(events, !in.anyOnline() ? EliminationReason.NO_PLAYERS : EliminationReason.ALL_DOWN);
            return List.copyOf(events);
        }
        if (state == State.COOLDOWN && stateTicks >= script.cooldownTicks()) {
            wave++;
            state = State.COUNTDOWN;
            stateTicks = 0;
        }
        if (state == State.COUNTDOWN) {
            if (stateTicks < script.countdownTicks()) {
                if (stateTicks % 20 == 0)
                    events.add(new BattleEvent.Countdown(wave, (script.countdownTicks() - stateTicks + 19) / 20));
            } else {
                spawn(events);
            }
        } else if (state == State.FIGHT) {
            if (in.spawningDone() && in.alive() == 0) clear(events);
            else if (stateTicks >= script.maxWaveTicks()) eliminate(events, EliminationReason.TIME);
            else checkGlow(events);
        }
        return List.copyOf(events);
    }

    public int prepLeftTicks() { return state == State.PREP ? Math.max(0, script.prepTicks() - stateTicks) : 0; }
    public void setPrepLeft(int ticks) {
        if (state != State.PREP) throw new IllegalStateException("Battle is not in PREP");
        stateTicks = script.prepTicks() - Math.clamp(ticks, 0, script.prepTicks());
    }

    private void spawn(List<BattleEvent> events) {
        state = State.FIGHT;
        stateTicks = 0;
        glowing = false;
        kills = 0;
        events.add(new BattleEvent.SpawnWave(wave));
        // Input describes the previous wave until the new spawn event is applied.
        if (script.waves().get(wave - 1).values().stream().allMatch(n -> n == 0)) clear(events);
        else checkGlow(events);
    }

    private void checkGlow(List<BattleEvent> events) {
        if (!glowing && stateTicks >= script.glowAfterTicks()) {
            glowing = true;
            events.add(new BattleEvent.Glow(wave));
        }
    }

    private void clear(List<BattleEvent> events) {
        cleared++;
        kills = 0;
        events.add(new BattleEvent.WaveCleared(wave));
        stateTicks = 0;
        if (cleared == script.waves().size()) {
            state = State.FINISHED;
            events.add(new BattleEvent.Finished(elapsed));
        } else {
            state = State.COOLDOWN;
            if (script.cooldownTicks() == 0) {
                wave++;
                state = State.COUNTDOWN;
                if (script.countdownTicks() > 0)
                    events.add(new BattleEvent.Countdown(wave, (script.countdownTicks() + 19) / 20));
                else spawn(events);
            }
        }
    }

    private void eliminate(List<BattleEvent> events, EliminationReason reason) {
        state = State.ELIMINATED;
        events.add(new BattleEvent.Eliminated(progress(), reason));
    }
}
