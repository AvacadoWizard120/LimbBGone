package io.github.avacadowizard120.mobamputation.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/** Visual status marker; PlayerTraumaManager owns the authoritative damage. */
public final class BleedingEffect extends MobEffect {
    public BleedingEffect() {
        super(MobEffectCategory.HARMFUL, 0x9A1010);
    }
}
