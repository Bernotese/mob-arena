package dev.mobarena.hud;

import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/** Vanilla cues, resolved lazily so pure result tests need no registry bootstrap. */
public enum GameSound {
    COUNTDOWN(() -> SoundEvents.NOTE_BLOCK_PLING, 1, 1),
    GO(() -> SoundEvents.NOTE_BLOCK_PLING, 1, 2),
    WAVE_CLEARED(() -> holder(SoundEvents.PLAYER_LEVELUP), .8f, 1),
    FINISHED(() -> holder(SoundEvents.PLAYER_LEVELUP), 1, 1.5f),
    ELIMINATED(() -> holder(SoundEvents.BEACON_DEACTIVATE), 1, 1),
    SUDDEN_DEATH(() -> SoundEvents.RAID_HORN, 1, 1),
    WIN(() -> holder(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE), 1, 1),
    LOSE(() -> holder(SoundEvents.WITHER_DEATH), .5f, 1),
    DRAW(() -> holder(SoundEvents.BELL_BLOCK), 1, 1);

    private final Supplier<Holder<SoundEvent>> sound;
    private final float volume, pitch;
    GameSound(Supplier<Holder<SoundEvent>> sound, float volume, float pitch) {
        this.sound = sound; this.volume = volume; this.pitch = pitch;
    }
    private static Holder<SoundEvent> holder(SoundEvent sound) { return BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound); }
    public Holder<SoundEvent> sound() { return sound.get(); }
    public float volume() { return volume; }
    public float pitch() { return pitch; }
}
