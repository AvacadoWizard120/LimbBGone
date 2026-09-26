package io.github.avacadowizard120.mobamputation.mixin.client;

import io.github.avacadowizard120.mobamputation.client.InventoryPreviewRenderContext;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Limits the first-person override to the inventory paper-doll render call. */
@Mixin(
        value = InventoryScreen.class
        //? if forge {
        /*, remap = false*/
        //?}
)
public abstract class InventoryScreenMixin {
    //? if forge {
    /*@ModifyArg(
            method = {
                    "renderEntityInInventory(Lnet/minecraft/client/gui/GuiGraphics;FFF"
                            + "Lorg/joml/Vector3f;Lorg/joml/Quaternionf;Lorg/joml/Quaternionf;"
                            + "Lnet/minecraft/world/entity/LivingEntity;)V",
                    "m_280432_(Lnet/minecraft/client/gui/GuiGraphics;FFF"
                            + "Lorg/joml/Vector3f;Lorg/joml/Quaternionf;Lorg/joml/Quaternionf;"
                            + "Lnet/minecraft/world/entity/LivingEntity;)V"
            },
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/systems/RenderSystem;runAsFancy(Ljava/lang/Runnable;)V"
            ),
            index = 0
    )*/
    //?} else {
    @ModifyArg(
            method = "renderEntityInInventory(Lnet/minecraft/client/gui/GuiGraphics;FFF"
                    + "Lorg/joml/Vector3f;Lorg/joml/Quaternionf;Lorg/joml/Quaternionf;"
                    + "Lnet/minecraft/world/entity/LivingEntity;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/systems/RenderSystem;runAsFancy(Ljava/lang/Runnable;)V"
            ),
            index = 0
    )
    //?}
    private static Runnable mobamputation$wrapInventoryPreview(Runnable renderCall) {
        return () -> {
            InventoryPreviewRenderContext.begin();
            try {
                renderCall.run();
            } finally {
                InventoryPreviewRenderContext.end();
            }
        };
    }
}
