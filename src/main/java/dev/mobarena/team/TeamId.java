package dev.mobarena.team;
public enum TeamId {
    RED, BLUE;
    public TeamId opponent() { return this == RED ? BLUE : RED; }
}
