package dev.mobarena.world;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class WorldCleanupTest {
    @TempDir Path root;
    @Test void removesOnlyOwnedCrashFolders() throws Exception {
        Path old = Files.createDirectories(root.resolve("red_overworld_1/region"));
        Files.writeString(old.resolve("chunk"), "old chunks");
        Path currentFormat = Files.createDirectories(root.resolve("blue_end_aabbcc_12"));
        Path fixed = Files.createDirectories(root.resolve("red_arena"));
        Path other = Files.createDirectories(root.resolve("custom_world"));
        WorldCleanup.removeCrashLeftovers(root);
        assertFalse(Files.exists(old));
        assertFalse(Files.exists(currentFormat));
        assertFalse(Files.exists(fixed));
        assertTrue(Files.exists(other));
    }
    @Test void doesNotFollowLinks() throws Exception {
        Path target = Files.createDirectories(root.resolve("keep"));
        Path content = Files.writeString(target.resolve("content"), "keep");
        Path link = Files.createSymbolicLink(root.resolve("red_end_1"), target);
        WorldCleanup.removeCrashLeftovers(root);
        assertFalse(Files.exists(link, LinkOption.NOFOLLOW_LINKS));
        assertEquals("keep", Files.readString(content));
    }
}
