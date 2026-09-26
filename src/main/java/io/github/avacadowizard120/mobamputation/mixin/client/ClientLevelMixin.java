package io.github.avacadowizard120.mobamputation.mixin.client;

import io.github.avacadowizard120.mobamputation.client.GibManager;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
        value = ClientLevel.class
        //? if forge {
        /*, remap = false*/
        //?}
)
public abstract class ClientLevelMixin {
    //? if forge {
    /*@Inject(method = {"addEntity", "m_104739_"}, at = @At("TAIL"))*/
    //?} else {
    @Inject(method = "addEntity", at = @At("TAIL"))
    //?}
    private void mobamputation$attachLocalGibs(Entity entity, CallbackInfo ci) {
        GibManager.onEntityAdded((ClientLevel) (Object) this, entity);
    }
}
