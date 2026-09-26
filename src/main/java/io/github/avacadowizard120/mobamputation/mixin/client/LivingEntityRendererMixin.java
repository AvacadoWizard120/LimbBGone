package io.github.avacadowizard120.mobamputation.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.avacadowizard120.mobamputation.client.dismemberment.DeathDismembermentManager;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.CreeperModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
        value = LivingEntityRenderer.class
        //? if forge {
        /*, remap = false*/
        //?}
)
public abstract class LivingEntityRendererMixin<T extends LivingEntity, M extends EntityModel<T>> {
    @Unique private HumanoidModel<?> mobamputation$visibilityModel;
    @Unique private boolean mobamputation$headVisible;
    @Unique private boolean mobamputation$hatVisible;
    @Unique private boolean mobamputation$leftArmVisible;
    @Unique private boolean mobamputation$rightArmVisible;
    @Unique private PlayerModel<?> mobamputation$playerVisibilityModel;
    @Unique private boolean mobamputation$leftSleeveVisible;
    @Unique private boolean mobamputation$rightSleeveVisible;
    @Unique private ModelPart mobamputation$creeperHead;
    @Unique private boolean mobamputation$creeperHeadVisible;

    //? if forge {
    /*@Inject(
            method = {
                    "render(Lnet/minecraft/world/entity/LivingEntity;FF"
                            + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                            + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
                    "m_7392_(Lnet/minecraft/world/entity/LivingEntity;FF"
                            + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                            + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V"
            },
            at = @At("HEAD"),
            cancellable = true
    )*/
    //?} else {
    @Inject(
            method = "render(Lnet/minecraft/world/entity/LivingEntity;FF"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"),
            cancellable = true
    )
    //?}
    private void mobamputation$captureModelVisibility(
            T entity,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight,
            CallbackInfo ci
    ) {
        mobamputation$restoreModelVisibility();
        if (DeathDismembermentManager.shouldSuppressParent(entity)) {
            ci.cancel();
            return;
        }
        @SuppressWarnings("unchecked")
        LivingEntityRenderer<T, M> renderer = (LivingEntityRenderer<T, M>) (Object) this;
        if (renderer.getModel() instanceof HumanoidModel<?> humanoid) {
            mobamputation$visibilityModel = humanoid;
            mobamputation$headVisible = humanoid.head.visible;
            mobamputation$hatVisible = humanoid.hat.visible;
            mobamputation$leftArmVisible = humanoid.leftArm.visible;
            mobamputation$rightArmVisible = humanoid.rightArm.visible;
            if (humanoid instanceof PlayerModel<?> playerModel) {
                mobamputation$playerVisibilityModel = playerModel;
                mobamputation$leftSleeveVisible = playerModel.leftSleeve.visible;
                mobamputation$rightSleeveVisible = playerModel.rightSleeve.visible;
            }
        } else if (renderer.getModel() instanceof CreeperModel<?>) {
            mobamputation$creeperHead = ((CreeperModelAccessor) renderer.getModel()).mobamputation$head();
            mobamputation$creeperHeadVisible = mobamputation$creeperHead.visible;
        }
    }

    //? if forge {
    /*@Inject(
            method = {
                    "render(Lnet/minecraft/world/entity/LivingEntity;FF"
                            + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                            + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
                    "m_7392_(Lnet/minecraft/world/entity/LivingEntity;FF"
                            + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                            + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V"
            },
            at = @At("TAIL")
    )*/
    //?} else {
    @Inject(
            method = "render(Lnet/minecraft/world/entity/LivingEntity;FF"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("TAIL")
    )
    //?}
    private void mobamputation$restoreAfterRender(
            T entity,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight,
            CallbackInfo ci
    ) {
        mobamputation$restoreModelVisibility();
    }

    @Unique
    private void mobamputation$restoreModelVisibility() {
        HumanoidModel<?> humanoid = mobamputation$visibilityModel;
        if (humanoid != null) {
            humanoid.head.visible = mobamputation$headVisible;
            humanoid.hat.visible = mobamputation$hatVisible;
            humanoid.leftArm.visible = mobamputation$leftArmVisible;
            humanoid.rightArm.visible = mobamputation$rightArmVisible;
            mobamputation$visibilityModel = null;
        }
        if (mobamputation$playerVisibilityModel != null) {
            mobamputation$playerVisibilityModel.leftSleeve.visible = mobamputation$leftSleeveVisible;
            mobamputation$playerVisibilityModel.rightSleeve.visible = mobamputation$rightSleeveVisible;
            mobamputation$playerVisibilityModel = null;
        }
        if (mobamputation$creeperHead != null) {
            mobamputation$creeperHead.visible = mobamputation$creeperHeadVisible;
            mobamputation$creeperHead = null;
        }
    }
}
