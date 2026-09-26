package io.github.avacadowizard120.mobamputation.mixin.client;

import io.github.avacadowizard120.mobamputation.api.Limb;
import io.github.avacadowizard120.mobamputation.client.GibManager;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
        value = HumanoidModel.class
        //? if forge {
        /*, remap = false*/
        //?}
)
public abstract class HumanoidModelMixin<T extends LivingEntity> {
    //? if forge {
    /*@Inject(
            method = {
                    "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V",
                    "m_6973_(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V"
            },
            at = @At("TAIL")
    )*/
    //?} else {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    //?}
    private void mobamputation$hideLimbs(
            T entity,
            float limbSwing,
            float limbSwingAmount,
            float ageInTicks,
            float netHeadYaw,
            float headPitch,
            CallbackInfo ci
    ) {
        if (!GibManager.shouldRenderModelAmputations(entity)) {
            return;
        }
        @SuppressWarnings("unchecked")
        HumanoidModel<T> model = (HumanoidModel<T>) (Object) this;
        if (GibManager.isDetached(entity, Limb.HEAD)) {
            model.head.visible = false;
            model.hat.visible = false;
        }
        if (GibManager.isDetached(entity, Limb.LEFT_ARM)) {
            model.leftArm.visible = false;
            if (model instanceof PlayerModel<?> playerModel) {
                playerModel.leftSleeve.visible = false;
            }
        }
        if (GibManager.isDetached(entity, Limb.RIGHT_ARM)) {
            model.rightArm.visible = false;
            if (model instanceof PlayerModel<?> playerModel) {
                playerModel.rightSleeve.visible = false;
            }
        }
    }
}
