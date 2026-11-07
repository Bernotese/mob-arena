package dev.mobarena.core;

import net.minecraft.server.MinecraftServer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Prepares a game before GameManager.start, possibly over several ticks. Server thread only. */
public interface StartPreparer {
    /** Runs {@code start} at once: no preparation. */
    StartPreparer NONE = new StartPreparer() {
        public boolean busy() { return false; }
        public void begin(MinecraftServer server, long seed, BooleanSupplier start, Consumer<RuntimeException> failed) { start.getAsBoolean(); }
        public void cancel(MinecraftServer server) {}
    };

    boolean busy();
    /** {@code start} runs on the server thread when ready; it returns false if the game did not start. */
    void begin(MinecraftServer server, long seed, BooleanSupplier start, Consumer<RuntimeException> failed);
    void cancel(MinecraftServer server);
}
