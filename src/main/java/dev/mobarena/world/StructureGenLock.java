package dev.mobarena.world;

/**
 * Stronghold, Nether fortress and End city piece generation use static mutable state in vanilla.
 * RED and BLUE levels generate them in parallel, so their mixins share this lock.
 */
public final class StructureGenLock {
    public static final Object LOCK = new Object();
    private StructureGenLock() {}
}
