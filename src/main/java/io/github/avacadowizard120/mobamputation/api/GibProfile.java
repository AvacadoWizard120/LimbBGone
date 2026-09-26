package io.github.avacadowizard120.mobamputation.api;

import java.util.EnumSet;
import java.util.Set;

/** Standard head/arm proxy layout and blood profile for an entity type. */
public record GibProfile(
        Set<Limb> limbs,
        boolean shrinkHumanoidParent,
        double headHeightFromFeet,
        BloodProperties blood
) {
    public GibProfile {
        limbs = limbs == null || limbs.isEmpty()
                ? Set.of()
                : Set.copyOf(EnumSet.copyOf(limbs));
    }

    public boolean supports(Limb limb) {
        return limbs.contains(limb);
    }

    public static GibProfile humanoid(BloodProperties blood) {
        return new GibProfile(EnumSet.allOf(Limb.class), true, 1.5D, blood);
    }

    public static GibProfile player() {
        return new GibProfile(EnumSet.allOf(Limb.class), false, 1.5D,
                BloodProperties.original(1.0F, 0.0F, 0.0F));
    }

    public static GibProfile creeper() {
        return new GibProfile(EnumSet.of(Limb.HEAD), false, 1.25D, BloodProperties.creeper());
    }
}
