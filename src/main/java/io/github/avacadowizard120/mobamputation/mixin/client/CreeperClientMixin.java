package io.github.avacadowizard120.mobamputation.mixin.client;

import io.github.avacadowizard120.mobamputation.client.dismemberment.DeathDismembermentManager;
import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
        value = Creeper.class
        //? if forge {
        /*, remap = false*/
        //?}
)
public abstract class CreeperClientMixin {
    // The client-side explode method returns before creating an Explosion, so
    // capture the powered state and origin while the creeper still exists.
    //? if forge {
    /*@Inject(method = { "explodeCreeper", "m_32315_" }, at = @At("HEAD"))*/
    //?} else {
    @Inject(method = "explodeCreeper", at = @At("HEAD"))
    //?}
    private void mobamputation$captureCreeperExplosion(CallbackInfo ci) {
        DeathDismembermentManager.observeCreeperExplosion((Creeper) (Object) this);
    }
}
