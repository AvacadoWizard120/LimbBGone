package io.github.avacadowizard120.mobamputation.effect;

import io.github.avacadowizard120.mobamputation.logic.PlayerTraumaManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.InstantenousMobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** Instant server-owned restoration used by every limb-regrowth potion form. */
public final class LimbRegrowthEffect extends InstantenousMobEffect {
    public LimbRegrowthEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x55D979);
    }

    @Override
    public boolean applyEffectTick(LivingEntity target, int amplifier) {
        regrow(target);
        return true;
    }

    @Override
    public void applyInstantenousEffect(
            Entity source,
            Entity indirectSource,
            LivingEntity target,
            int amplifier,
            double proximity
    ) {
        regrow(target);
    }

    private static void regrow(LivingEntity target) {
        // Drinking, splash clouds, lingering clouds, and /effect can all run
        // on both logical sides. The server is the sole trauma authority and
        // broadcasts the resulting intact snapshot to every compatible client.
        if (target instanceof ServerPlayer player) {
            PlayerTraumaManager.restoreIntactBody(player);
        }
    }
}
