package io.github.avacadowizard120.mobamputation.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.avacadowizard120.mobamputation.client.GibManager;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hides the physical first-person arm and held item after that arm is lost. */
@Mixin(
        value = ItemInHandRenderer.class
        //? if forge {
        /*, remap = false*/
        //?}
)
public abstract class ItemInHandRendererMixin {
    //? if forge {
    /*@Inject(method = {
            "renderArmWithItem",
            "m_109371_"
    }, at = @At("HEAD"), cancellable = true)*/
    //?} else {
    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    //?}
    private void mobamputation$hideDetachedFirstPersonArm(
            AbstractClientPlayer player,
            float partialTick,
            float pitch,
            InteractionHand hand,
            float swingProgress,
            ItemStack stack,
            float equippedProgress,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight,
            CallbackInfo ci
    ) {
        if (GibManager.isDetached(player, GibManager.limbForHand(player, hand))) {
            ci.cancel();
        }
    }
}
