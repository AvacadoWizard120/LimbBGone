package io.github.avacadowizard120.mobamputation.mixin;

import io.github.avacadowizard120.mobamputation.logic.ServerAmputationAuthority;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Scopes exact server projectile impacts around their real damage callback. */
@Mixin(
        value = Projectile.class
        //? if forge {
        /*, remap = false*/
        //?}
)
public abstract class ProjectileMixin {
    //? if forge {
    /*@Inject(method = {"hitTargetOrDeflectSelf", "m_320847_"}, at = @At("HEAD"))*/
    //?} else {
    @Inject(method = "hitTargetOrDeflectSelf", at = @At("HEAD"))
    //?}
    private void mobamputation$beginProjectileHit(
            HitResult hit,
            CallbackInfoReturnable<ProjectileDeflection> cir
    ) {
        if (hit instanceof EntityHitResult entityHit) {
            ServerAmputationAuthority.beginProjectileImpact((Projectile) (Object) this, entityHit);
        }
    }

    //? if forge {
    /*@Inject(method = {"hitTargetOrDeflectSelf", "m_320847_"}, at = @At("RETURN"))*/
    //?} else {
    @Inject(method = "hitTargetOrDeflectSelf", at = @At("RETURN"))
    //?}
    private void mobamputation$recordProjectileHit(
            HitResult hit,
            CallbackInfoReturnable<ProjectileDeflection> cir
    ) {
        if (hit instanceof EntityHitResult entityHit) {
            ServerAmputationAuthority.endProjectileImpact(
                    (Projectile) (Object) this,
                    entityHit,
                    cir.getReturnValue() == ProjectileDeflection.NONE
            );
        }
    }
}
