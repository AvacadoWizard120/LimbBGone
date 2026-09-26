package io.github.avacadowizard120.mobamputation.network;

/** Cause information used only to match a request to server-observed evidence. */
public enum DetachCause {
    MELEE,
    PROJECTILE,
    FISHING;

    public static DetachCause fromNetwork(byte value) {
        return value >= 0 && value < values().length ? values()[value] : null;
    }
}
