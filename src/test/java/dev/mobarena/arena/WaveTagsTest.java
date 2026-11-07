package dev.mobarena.arena;

import dev.mobarena.team.TeamId;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WaveTagsTest {
    @Test void tagAndParseRoundTrip() {
        assertEquals("mobarena.wave.red.2", WaveTags.tag(TeamId.RED, 2));
        assertEquals("mobarena.wave.blue.4", WaveTags.tag(TeamId.BLUE, WaveTags.SUDDEN_DEATH_WAVE));
        assertEquals(new WaveTags.Parsed(TeamId.BLUE, 3), WaveTags.parse(WaveTags.tag(TeamId.BLUE, 3)).orElseThrow());
        assertTrue(WaveTags.tag(TeamId.RED, 1).startsWith(WaveTags.teamPrefix(TeamId.RED)));
    }
    @Test void foreignTagsAreRejected() {
        for (String tag : new String[]{null, "", "mobarena.wave.", "mobarena.wave.red", "mobarena.wave.red.0",
                "mobarena.wave.red.x", "mobarena.wave.green.1", "mobarena.wave.RED.1", "mobarena.wave.red.01",
                "mobarena.wave.red.1.2", "other.wave.red.1"}) assertFalse(WaveTags.isWaveTag(tag), String.valueOf(tag));
        assertThrows(IllegalArgumentException.class, () -> WaveTags.tag(TeamId.RED, 0));
    }
}
