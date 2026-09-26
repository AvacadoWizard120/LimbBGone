package io.github.avacadowizard120.mobamputation.api;

/**
 * Physical/color description for a gib's client-side blood particles.
 *
 * <p>This record currently describes the built-in blood profiles. It is kept
 * separate from rendering so a complete custom-mob system can reuse the same
 * physical concepts later; it is not a supported add-on contract in this
 * release.</p>
 */
public record BloodProperties(
        float red,
        float green,
        float blue,
        float stickiness,
        float drippiness,
        float weight,
        float velocityMultiplier
) {
    public BloodProperties {
        red = clamp(red);
        green = clamp(green);
        blue = clamp(blue);
        stickiness = clamp(stickiness);
        drippiness = Math.max(0.05F, drippiness);
        weight = Math.max(0.05F, weight);
        velocityMultiplier = Math.max(0.0F, velocityMultiplier);
    }

    /** Exact physical constants used by iChun's original blood. */
    public static BloodProperties original(float red, float green, float blue) {
        // Upstream ground damping retains 70% horizontal motion, therefore
        // the intuitive stickiness value is 30%.
        return new BloodProperties(red, green, blue, 0.3F, 1.0F, 1.0F, 1.2F);
    }

    /** Thick, genuinely green Creeper blood supplied by this port. */
    public static BloodProperties creeper() {
        return new BloodProperties(0.18F, 0.82F, 0.12F, 0.62F, 0.72F, 1.25F, 0.95F);
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
