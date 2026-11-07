package dev.mobarena.world;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public enum WorldKind {
    PREP_OVERWORLD, PREP_NETHER, PREP_END, ARENA;

    public ResourceKey<Level> vanillaKey() {
        return switch (this) {
            case PREP_OVERWORLD -> Level.OVERWORLD;
            case PREP_NETHER -> Level.NETHER;
            case PREP_END -> Level.END;
            case ARENA -> null;
        };
    }
}
