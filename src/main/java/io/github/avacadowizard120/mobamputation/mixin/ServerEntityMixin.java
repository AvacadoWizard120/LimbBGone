package io.github.avacadowizard120.mobamputation.mixin;

import io.github.avacadowizard120.mobamputation.network.MobAmputationNetworking;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
        value = ServerEntity.class
        //? if forge {
        /*, remap = false*/
        //?}
)
public abstract class ServerEntityMixin {
    @Shadow(aliases = "f_8510_") @Final private Entity entity;

    //? if forge {
    /*@Inject(method = { "addPairing", "m_8541_" }, at = @At("TAIL"))*/
    //?} else {
    @Inject(method = "addPairing", at = @At("TAIL"))
    //?}
    private void mobamputation$sendPlayerAmputationState(ServerPlayer viewer, CallbackInfo ci) {
        MobAmputationNetworking.sendPlayerState(viewer, entity);
    }
}
