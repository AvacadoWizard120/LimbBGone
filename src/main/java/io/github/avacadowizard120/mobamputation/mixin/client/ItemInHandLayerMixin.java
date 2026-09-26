package io.github.avacadowizard120.mobamputation.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.avacadowizard120.mobamputation.api.Limb;
import io.github.avacadowizard120.mobamputation.client.GibManager;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevents an item floating where a detached third-person arm used to be. */
@Mixin(
        value = ItemInHandLayer.class
        //? if forge {
        /*, remap = false*/
        //?}
)
public abstract class ItemInHandLayerMixin {
    //? if forge {
    /*@Inject(method = {
            "renderArmWithItem",
            "m_117184_"
    }, at = @At("HEAD"), cancellable = true)*/
    //?} else {
    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    //?}
    private void mobamputation$hideDetachedHeldItem(
            LivingEntity entity,
            ItemStack stack,
            ItemDisplayContext displayContext,
            HumanoidArm arm,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight,
            CallbackInfo ci
    ) {
        Limb limb = arm == HumanoidArm.LEFT ? Limb.LEFT_ARM : Limb.RIGHT_ARM;
        if (GibManager.isDetached(entity, limb)) {
            ci.cancel();
        }
    }
}
