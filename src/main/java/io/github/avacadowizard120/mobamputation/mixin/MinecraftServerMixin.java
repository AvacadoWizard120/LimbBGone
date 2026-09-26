package io.github.avacadowizard120.mobamputation.mixin;

import io.github.avacadowizard120.mobamputation.logic.HeadlessDeathManager;
import io.github.avacadowizard120.mobamputation.logic.PlayerTraumaManager;
import io.github.avacadowizard120.mobamputation.logic.ServerAmputationAuthority;
import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
        value = MinecraftServer.class
        //? if forge {
        /*, remap = false*/
        //?}
)
public abstract class MinecraftServerMixin {
    //? if forge {
    /*@Inject(method = {"tickServer", "m_5705_"}, at = @At("TAIL"))*/
    //?} else {
    @Inject(method = "tickServer", at = @At("TAIL"))
    //?}
    private void mobamputation$tickHeadlessDeaths(BooleanSupplier hasTimeLeft, CallbackInfo ci) {
        HeadlessDeathManager.tickServer();
        ServerAmputationAuthority.tick();
        PlayerTraumaManager.tickServer((MinecraftServer) (Object) this);
    }

    //? if forge {
    /*@Inject(method = {"stopServer", "m_7041_"}, at = @At("HEAD"))*/
    //?} else {
    @Inject(method = "stopServer", at = @At("HEAD"))
    //?}
    private void mobamputation$clearHeadlessDeaths(CallbackInfo ci) {
        HeadlessDeathManager.clear();
        ServerAmputationAuthority.clear();
    }
}
