package io.github.avacadowizard120.mobamputation.mixin;

import io.github.avacadowizard120.mobamputation.api.Limb;
import io.github.avacadowizard120.mobamputation.logic.ArmorProtectionPolicy;
import io.github.avacadowizard120.mobamputation.logic.LimbHitResolver;
import io.github.avacadowizard120.mobamputation.logic.ServerAmputationAuthority;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Covers damaging projectile implementations that bypass Projectile's collision helper. */
@Mixin(
        value = LivingEntity.class
        //? if forge {
        /*, remap = false*/
        //?}
)
public abstract class LivingEntityDamageMixin {
    /**
     * One entry per active hurt invocation. ArrayList is intentional because a
     * null entry represents a non-player/client invocation and ArrayDeque does
     * not permit nulls. The LIFO pairing also survives nested damage calls.
    */
    @Unique
    private List<ArmorProtectionPolicy.ArmorSnapshot> mobamputation$armorBeforeDamage;
    @Unique
    private List<Limb> mobamputation$limbBeforeDamage;

    //? if forge {
    /*@Inject(method = {"hurt", "m_6469_"}, at = @At("HEAD"))*/
    //?} else {
    @Inject(method = "hurt", at = @At("HEAD"))
    //?}
    private void mobamputation$captureArmorBeforeDamage(
            DamageSource source,
            float amount,
            CallbackInfoReturnable<Boolean> cir
    ) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (mobamputation$armorBeforeDamage == null) {
            mobamputation$armorBeforeDamage = new ArrayList<>(1);
        }
        mobamputation$armorBeforeDamage.add(!self.level().isClientSide && self instanceof Player player
                ? ArmorProtectionPolicy.snapshot(player)
                : null);
        if (mobamputation$limbBeforeDamage == null) {
            mobamputation$limbBeforeDamage = new ArrayList<>(1);
        }
        Entity direct = source.getDirectEntity();
        ServerAmputationAuthority.ProjectileImpact exactImpact =
                ServerAmputationAuthority.currentProjectileImpact(self, direct);
        mobamputation$limbBeforeDamage.add(self.level().isClientSide
                || direct == null
                || !source.is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE)
                ? null
                // A scoped impact may intentionally contain null: that is
                // exact proof that this accepted damage segment hit no limb,
                // and must not be replaced by the old nearest-position guess.
                : exactImpact != null
                        ? exactImpact.hitLimb()
                        : LimbHitResolver.projectile(self, direct, direct.position()));
    }

    //? if forge {
    /*@Inject(method = {"hurt", "m_6469_"}, at = @At("RETURN"))*/
    //?} else {
    @Inject(method = "hurt", at = @At("RETURN"))
    //?}
    private void mobamputation$recordProjectileDamage(
            DamageSource source,
            float amount,
            CallbackInfoReturnable<Boolean> cir
    ) {
        ArmorProtectionPolicy.ArmorSnapshot armorSnapshot = mobamputation$armorBeforeDamage == null
                || mobamputation$armorBeforeDamage.isEmpty()
                ? null
                : mobamputation$armorBeforeDamage.remove(mobamputation$armorBeforeDamage.size() - 1);
        Limb hitLimb = mobamputation$limbBeforeDamage == null || mobamputation$limbBeforeDamage.isEmpty()
                ? null
                : mobamputation$limbBeforeDamage.remove(mobamputation$limbBeforeDamage.size() - 1);
        ServerAmputationAuthority.recordProjectileDamage(
                (LivingEntity) (Object) this,
                source,
                cir.getReturnValueZ(),
                armorSnapshot,
                hitLimb
        );
    }
}
