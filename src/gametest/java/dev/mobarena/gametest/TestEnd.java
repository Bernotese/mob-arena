package dev.mobarena.gametest;

import dev.mobarena.MobArena;
import dev.mobarena.core.GameManager;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import java.lang.reflect.Field;

/**
 * Cleanup that runs when a test passes or fails. GameTestHelper.runBeforeTestEnd runs only on the
 * timeout tick, so it never runs for a test that succeeds early.
 */
public final class TestEnd {
    private static final Field TEST_INFO;
    static {
        try { TEST_INFO = GameTestHelper.class.getDeclaredField("testInfo"); TEST_INFO.setAccessible(true); }
        catch (NoSuchFieldException e) { throw new ExceptionInInitializerError(e); }
    }
    private TestEnd() {}

    public static void run(GameTestHelper helper, Runnable cleanup) {
        GameTestInfo info;
        try { info = (GameTestInfo) TEST_INFO.get(helper); }
        catch (IllegalAccessException e) { throw new IllegalStateException(e); }
        info.addListener(new GameTestListener() {
            private boolean done;
            private void end() {
                if (done) return;
                done = true;
                try { cleanup.run(); } catch (RuntimeException e) { MobArena.LOGGER.error("Test cleanup failed: {}", info.id(), e); }
            }
            @Override public void testStructureLoaded(GameTestInfo test) {}
            @Override public void testPassed(GameTestInfo test, GameTestRunner runner) { end(); }
            @Override public void testFailed(GameTestInfo test, GameTestRunner runner) { end(); }
            @Override public void testAddedForRerun(GameTestInfo old, GameTestInfo next, GameTestRunner runner) {}
        });
    }

    /** Stops a game that an earlier test left running; ends at LOBBY even if a listener fails. */
    public static void stopStaleGame() {
        GameManager game = GameManager.get();
        game.stop();
        if (game.context() != null) { MobArena.LOGGER.warn("Stale game did not stop; shutting it down"); game.shutdown(); }
    }

    /** Stop the test's game at the end of the test (pass or fail). */
    public static void stopGameAtEnd(GameTestHelper helper) { run(helper, TestEnd::stopStaleGame); }
}
