package io.github.avacadowizard120.mobamputation.mixin.client;

import io.github.avacadowizard120.mobamputation.client.GibManager;
import io.github.avacadowizard120.mobamputation.client.dismemberment.DeathDismembermentManager;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
        value = Minecraft.class
        //? if forge {
        /*, remap = false*/
        //?}
)
public abstract class MinecraftMixin {
    // iChunUtil increments its clock at ClientTickEvent.END, including while
    // paused. Gib lifetime deliberately uses this instead of level game time.
    //? if forge {
    /*@Inject(method = {"tick", "m_91398_"}, at = @At("TAIL"))*/
    //?} else {
    @Inject(method = "tick", at = @At("TAIL"))
    //?}
    private void mobamputation$clientTick(CallbackInfo ci) {
        Minecraft minecraft = (Minecraft) (Object) this;
        GibManager.clientTick(minecraft);
        DeathDismembermentManager.clientTick(minecraft);
        if (minecraft.level != null) {
            GibManager.endClientTick(minecraft.level);
        }
    }
}
