package io.github.avacadowizard120.mobamputation.mixin.client;

import io.github.avacadowizard120.mobamputation.client.GibManager;
import io.github.avacadowizard120.mobamputation.client.dismemberment.DeathDismembermentManager;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
        value = LivingEntity.class
        //? if forge {
        /*, remap = false*/
        //?}
)
public abstract class LivingEntityClientMixin {
    //? if forge {
    /*@Inject(method = { "tick", "m_8119_" }, at = @At("TAIL"))*/
    //?} else {
    @Inject(method = "tick", at = @At("TAIL"))
    //?}
    private void mobamputation$observeDeathForDismemberment(CallbackInfo ci) {
        DeathDismembermentManager.observe((LivingEntity) (Object) this);
    }

    // iChun's client proxy changes every eligible parent's target box to
    // exactly 0.4 x 1.5 before attaching the three gib hitboxes.
    //? if forge {
    /*@Inject(method = { "getDimensions", "m_6972_" }, at = @At("RETURN"), cancellable = true)*/
    //?} else {
    @Inject(method = "getDimensions", at = @At("RETURN"), cancellable = true)
    //?}
    private void mobamputation$originalParentDimensions(
            Pose pose,
            CallbackInfoReturnable<EntityDimensions> cir
    ) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (GibManager.shouldShrinkParent(entity)) {
            cir.setReturnValue(EntityDimensions.fixed(0.4F, 1.5F));
        }
    }
}
