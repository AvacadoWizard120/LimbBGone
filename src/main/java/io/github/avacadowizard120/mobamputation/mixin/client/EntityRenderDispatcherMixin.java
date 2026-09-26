package io.github.avacadowizard120.mobamputation.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.avacadowizard120.mobamputation.client.GibRenderer;
import io.github.avacadowizard120.mobamputation.client.dismemberment.DeathGibEntity;
import io.github.avacadowizard120.mobamputation.client.dismemberment.DeathGibRenderer;
import io.github.avacadowizard120.mobamputation.client.dismemberment.DeathDismembermentManager;
import io.github.avacadowizard120.mobamputation.entity.GibEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
        value = EntityRenderDispatcher.class
        //? if forge {
        /*, remap = false*/
        //?}
)
public abstract class EntityRenderDispatcherMixin {
    //? if forge {
    /*@Inject(method = { "render", "m_114384_" }, at = @At("HEAD"), cancellable = true)*/
    //?} else {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    //?}
    private <E extends Entity> void mobamputation$renderGib(
            E entity,
            double x,
            double y,
            double z,
            float yaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight,
            CallbackInfo ci
    ) {
        if (entity instanceof net.minecraft.world.entity.LivingEntity living
                && DeathDismembermentManager.shouldSuppressParent(living)) {
            // Upstream killed the local corpse at handoff. Canceling the full
            // dispatcher path also removes its shadow, fire overlay and debug
            // hitbox while retaining the server-owned entity until vanilla's
            // normal removal packet arrives.
            ci.cancel();
            return;
        }
        if (entity instanceof GibEntity gib) {
            GibRenderer.render(gib, x, y, z, partialTick, poseStack, buffers, packedLight);
        } else if (entity instanceof DeathGibEntity gib) {
            DeathGibRenderer.render(gib, x, y, z, partialTick, poseStack, buffers, packedLight);
        }
    }
}
