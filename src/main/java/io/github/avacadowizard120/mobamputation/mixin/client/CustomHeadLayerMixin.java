package io.github.avacadowizard120.mobamputation.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.avacadowizard120.mobamputation.api.Limb;
import io.github.avacadowizard120.mobamputation.client.GibManager;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevents skulls, pumpkins, and other head-slot models floating above a missing head. */
@Mixin(
        value = CustomHeadLayer.class
        //? if forge {
        /*, remap = false*/
        //?}
)
public abstract class CustomHeadLayerMixin {
    //? if forge {
    /*@Inject(
            method = {
                    "render(Lcom/mojang/blaze3d/vertex/PoseStack;"
                            + "Lnet/minecraft/client/renderer/MultiBufferSource;I"
                            + "Lnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
                    "m_6494_(Lcom/mojang/blaze3d/vertex/PoseStack;"
                            + "Lnet/minecraft/client/renderer/MultiBufferSource;I"
                            + "Lnet/minecraft/world/entity/LivingEntity;FFFFFF)V"
            },
            at = @At("HEAD"),
            cancellable = true
    )*/
    //?} else {
    @Inject(
            method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;I"
                    + "Lnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At("HEAD"),
            cancellable = true
    )
    //?}
    private void mobamputation$hideDetachedHeadItem(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            LivingEntity entity,
            float limbSwing,
            float limbSwingAmount,
            float partialTick,
            float ageInTicks,
            float netHeadYaw,
            float headPitch,
            CallbackInfo ci
    ) {
        if (GibManager.shouldRenderModelAmputations(entity)
                && GibManager.isDetached(entity, Limb.HEAD)) {
            ci.cancel();
        }
    }
}
