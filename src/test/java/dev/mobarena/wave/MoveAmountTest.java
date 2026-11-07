package dev.mobarena.wave;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MoveAmountTest {
    @Test void allBooleanCombinations() {
        for (boolean left : new boolean[]{false, true}) {
            for (boolean right : new boolean[]{false, true}) {
                for (boolean shift : new boolean[]{false, true}) {
                    int expected = right ? Integer.MAX_VALUE : left ? (shift ? 10 : 1) : 0;
                    assertEquals(expected, MoveAmount.of(left, right, shift));
                }
            }
        }
    }
}
