package io.github.avacadowizard120.mobamputation.mixin.client;

import io.github.avacadowizard120.mobamputation.api.Limb;
import io.github.avacadowizard120.mobamputation.client.GibManager;
import net.minecraft.client.model.CreeperModel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
        value = CreeperModel.class
        //? if forge {
        /*, remap = false*/
        //?}
)
public abstract class CreeperModelMixin<T extends Entity> {
    //? if forge {
    /*@Inject(method = { "setupAnim", "m_6973_" }, at = @At("TAIL"))*/
    //?} else {
    @Inject(method = "setupAnim", at = @At("TAIL"))
    //?}
    private void mobamputation$hideHead(
            T entity,
            float limbSwing,
            float limbSwingAmount,
            float ageInTicks,
            float netHeadYaw,
            float headPitch,
            CallbackInfo ci
    ) {
        // CreeperPowerLayer owns a second CreeperModel which is not the main
        // renderer model captured by LivingEntityRendererMixin. Assign the
        // complete state every animation pass so a charged headless Creeper
        // cannot leak hidden-head state into the next intact Creeper.
        boolean detached = entity instanceof net.minecraft.world.entity.LivingEntity living
                && GibManager.isDetached(living, Limb.HEAD);
        ((CreeperModelAccessor) this).mobamputation$head().visible = !detached;
    }
}
