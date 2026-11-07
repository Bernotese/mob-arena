package dev.mobarena.world;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public final class PortalLink {
    private PortalLink() {}
    public static WorldKind kindFor(ResourceKey<Level> key) {
        return kindFor(key.identifier().toString());
    }
    public static WorldKind kindFor(String id) {
        if ("minecraft:the_nether".equals(id)) return WorldKind.PREP_NETHER;
        if ("minecraft:the_end".equals(id)) return WorldKind.PREP_END;
        return WorldKind.PREP_OVERWORLD;
    }
}
