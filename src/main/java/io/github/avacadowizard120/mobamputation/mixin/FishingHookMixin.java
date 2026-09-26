package io.github.avacadowizard120.mobamputation.mixin;

import io.github.avacadowizard120.mobamputation.logic.ServerAmputationAuthority;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Records a real server fishing retrieval before vanilla discards the hook. */
@Mixin(
        value = FishingHook.class
        //? if forge {
        /*, remap = false*/
        //?}
)
public abstract class FishingHookMixin {
    //? if forge {
    /*@Inject(method = {"retrieve", "m_37156_"}, at = @At("HEAD"))*/
    //?} else {
    @Inject(method = "retrieve", at = @At("HEAD"))
    //?}
    private void mobamputation$recordFishingRetrieval(ItemStack rod, CallbackInfoReturnable<Integer> cir) {
        ServerAmputationAuthority.recordFishing((FishingHook) (Object) this);
    }
}
