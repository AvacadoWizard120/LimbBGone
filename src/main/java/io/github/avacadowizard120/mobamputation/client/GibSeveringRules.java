package io.github.avacadowizard120.mobamputation.client;

import io.github.avacadowizard120.mobamputation.config.MobAmputationConfig;
import io.github.avacadowizard120.mobamputation.entity.GibEntity;
import io.github.avacadowizard120.mobamputation.logic.MeleeChancePolicy;
import io.github.avacadowizard120.mobamputation.logic.ProjectileChancePolicy;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

/** Literal client-side sever rolls and projectile-list compatibility rules. */
public final class GibSeveringRules {

    /** Applies the configured per-tool chance, then an optional limb-aware enchantment bonus. */
    public static boolean rollMelee(GibEntity gib, ItemStack held) {
        return rollPercentage(gib, MeleeChancePolicy.effectiveChance(gib.limb(), held));
    }

    private static boolean rollPercentage(GibEntity gib, int percentage) {
        if (percentage <= 0) {
            return false;
        }
        if (percentage >= 100) {
            return true;
        }
        return gib.getRandom().nextFloat() < percentage / 100.0F;
    }

    public static boolean rollFishing(GibEntity gib) {
        MobAmputationConfig.Snapshot config = MobAmputationConfig.get();
        float chance = config.fishingChance() / 100.0F;
        return gib.getRandom().nextFloat() <= chance;
    }

    public static boolean rollProjectile(GibEntity gib, Entity direct, DamageSource source) {
        ProjectileChancePolicy.Decision decision = ProjectileChancePolicy.evaluate(
                direct,
                source.is(DamageTypeTags.IS_PROJECTILE)
        );
        return decision.eligible()
                && gib.getRandom().nextFloat() <= decision.percentage() / 100.0F;
    }

    private GibSeveringRules() {
    }
}
