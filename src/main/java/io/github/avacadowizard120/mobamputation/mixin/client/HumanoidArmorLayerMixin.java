package io.github.avacadowizard120.mobamputation.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.avacadowizard120.mobamputation.api.Limb;
import io.github.avacadowizard120.mobamputation.client.GibManager;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
        value = HumanoidArmorLayer.class
        //? if forge {
        /*, remap = false*/
        //?}
)
public abstract class HumanoidArmorLayerMixin {
    @Unique
    private LivingEntity mobamputation$currentRenderEntity;

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
            at = @At("HEAD")
    )*/
    //?} else {
    @Inject(
            method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;I"
                    + "Lnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At("HEAD")
    )
    //?}
    private void mobamputation$beginRender(
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
        mobamputation$currentRenderEntity = entity;
    }

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
            at = @At("RETURN")
    )*/
    //?} else {
    @Inject(
            method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;I"
                    + "Lnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At("RETURN")
    )
    //?}
    private void mobamputation$endRender(
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
        mobamputation$currentRenderEntity = null;
    }

    //? if forge {
    /*@Inject(
            method = {
                    "setPartVisibility(Lnet/minecraft/client/model/HumanoidModel;"
                            + "Lnet/minecraft/world/entity/EquipmentSlot;)V",
                    "m_117125_(Lnet/minecraft/client/model/HumanoidModel;"
                            + "Lnet/minecraft/world/entity/EquipmentSlot;)V"
            },
            at = @At("RETURN")
    )*/
    //?} else {
    @Inject(
            method = "setPartVisibility(Lnet/minecraft/client/model/HumanoidModel;"
                    + "Lnet/minecraft/world/entity/EquipmentSlot;)V",
            at = @At("RETURN")
    )
    //?}
    private void mobamputation$hideAmputatedArmorParts(
            HumanoidModel<?> armorModel,
            EquipmentSlot slot,
            CallbackInfo ci
    ) {
        LivingEntity entity = mobamputation$currentRenderEntity;
        if (entity == null || !GibManager.shouldRenderModelAmputations(entity)) {
            return;
        }

        if (GibManager.isDetached(entity, Limb.HEAD)) {
            armorModel.head.visible = false;
            armorModel.hat.visible = false;
        }
        if (GibManager.isDetached(entity, Limb.LEFT_ARM)) {
            armorModel.leftArm.visible = false;
        }
        if (GibManager.isDetached(entity, Limb.RIGHT_ARM)) {
            armorModel.rightArm.visible = false;
        }
    }
}
