package dev.mobarena.hud;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TimeFormatTest {
    @Test void formatsTicks() {
        assertEquals("0:00",TimeFormat.mmss(0));
        assertEquals("0:59",TimeFormat.mmss(59*20));
        assertEquals("44:59",TimeFormat.mmss(2699*20));
        assertEquals("1:00:00",TimeFormat.mmss(3600*20));
        assertEquals("1:01:01",TimeFormat.mmss(3661*20));
        assertEquals("0:00",TimeFormat.mmss(-20));
    }
}
