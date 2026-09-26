package io.github.avacadowizard120.mobamputation.mixin;

import io.github.avacadowizard120.mobamputation.logic.LimbHitResolver;
import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Restores client-proxy projectile interception on the logical server. */
@Mixin(
        value = ProjectileUtil.class
        //? if forge {
        /*, remap = false*/
        //?}
)
public abstract class ProjectileUtilMixin {
    //? if forge {
    /*@Inject(
            method = {
                    "getEntityHitResult(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;F)Lnet/minecraft/world/phys/EntityHitResult;",
                    "m_150175_(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;F)Lnet/minecraft/world/phys/EntityHitResult;"
            },
            at = @At("RETURN"),
            cancellable = true
    )*/
    //?} else {
    @Inject(
            method = "getEntityHitResult(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;F)Lnet/minecraft/world/phys/EntityHitResult;",
            at = @At("RETURN"),
            cancellable = true
    )
    //?}
    private static void mobamputation$interceptAttachedProxy(
            Level level,
            Entity source,
            Vec3 start,
            Vec3 end,
            AABB searchBox,
            Predicate<Entity> canHit,
            float tolerance,
            CallbackInfoReturnable<EntityHitResult> cir
    ) {
        EntityHitResult intercepted = LimbHitResolver.interceptProjectileProxy(
                level,
                source,
                start,
                end,
                searchBox,
                canHit,
                tolerance,
                cir.getReturnValue()
        );
        if (intercepted != cir.getReturnValue()) {
            cir.setReturnValue(intercepted);
        }
    }
}
