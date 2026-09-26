package io.github.avacadowizard120.mobamputation.mixin.client;

import io.github.avacadowizard120.mobamputation.entity.GibEntity;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
        value = MultiPlayerGameMode.class
        //? if forge {
        /*, remap = false*/
        //?}
)
public abstract class MultiPlayerGameModeMixin {
    // Intercept before ServerboundInteractPacket is constructed: local gib
    // IDs are intentionally negative and must never be sent to the server.
    // Player.attack still builds the vanilla DamageSource/amount and invokes
    // GibEntity.hurt, whose upstream-equivalent path forwards to the parent.
    //? if forge {
    /*@Inject(method = {"attack", "m_105223_"}, at = @At("HEAD"), cancellable = true)*/
    //?} else {
    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    //?}
    private void mobamputation$attackLocalGib(Player player, Entity target, CallbackInfo ci) {
        if (!(target instanceof GibEntity gib)) {
            return;
        }
        if (!player.isSpectator()) {
            player.attack(gib);
            player.resetAttackStrengthTicker();
        }
        ci.cancel();
    }
}
