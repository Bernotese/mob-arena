package dev.mobarena.wave;

public final class MoveAmount {
    private MoveAmount() {}
    public static int of(boolean left, boolean right, boolean shift) {
        return right ? Integer.MAX_VALUE : left ? (shift ? 10 : 1) : 0;
    }
}
