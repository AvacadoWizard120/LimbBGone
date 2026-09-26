package io.github.avacadowizard120.mobamputation.api;

public enum Limb {
    HEAD(1),
    LEFT_ARM(1 << 1),
    RIGHT_ARM(1 << 2);

    private final byte bit;

    Limb(int bit) {
        this.bit = (byte) bit;
    }

    public byte bit() {
        return bit;
    }
}
