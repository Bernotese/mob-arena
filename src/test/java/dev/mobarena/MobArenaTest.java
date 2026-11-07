package dev.mobarena;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MobArenaTest {
	@Test
	void modIdIsMobarena() {
		assertEquals("mobarena", MobArena.MOD_ID);
	}
}
