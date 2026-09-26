package io.github.avacadowizard120.mobamputation.mixin;

import io.github.avacadowizard120.mobamputation.state.PlayerAmputationAccess;
import io.github.avacadowizard120.mobamputation.logic.LimbHitResolver;
import io.github.avacadowizard120.mobamputation.network.MobAmputationNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.DimensionTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Carries wounds through non-death player reconstruction, such as dimension transitions. */
@Mixin(
        value = ServerPlayer.class
        //? if forge {
        /*, remap = false*/
        //?}
)
public abstract class ServerPlayerMixin {
    @Unique
    private ServerLevel mobamputation$levelBeforeDimensionChange;

    // ServerPlayer overrides Player#die without invoking that implementation,
    // so the Player mixin's death hook never sees an actual server-player
    // death. Clear the authoritative trauma here as well. The client keeps its
    // detached visuals through the death screen; the zero snapshot is sent
    // only after vanilla has created the replacement player below.
    //? if forge {
    /*@Inject(method = {"die", "m_6667_"}, at = @At("TAIL"))*/
    //?} else {
    @Inject(method = "die", at = @At("TAIL"))
    //?}
    private void mobamputation$clearTraumaOnServerDeath(DamageSource source, CallbackInfo ci) {
        ((PlayerAmputationAccess) this).mobamputation$clearTrauma();
        LimbHitResolver.forgetPlayerTransform((ServerPlayer) (Object) this);
    }

    //? if forge {
    /*@Inject(method = {"tick", "m_8119_"}, at = @At("TAIL"))*/
    //?} else {
    @Inject(method = "tick", at = @At("TAIL"))
    //?}
    private void mobamputation$recordLimbProofTransform(CallbackInfo ci) {
        LimbHitResolver.recordPlayerTransform((ServerPlayer) (Object) this);
    }

    //? if forge {
    /*@Inject(method = {"restoreFrom", "m_9015_"}, at = @At("TAIL"))*/
    //?} else {
    @Inject(method = "restoreFrom", at = @At("TAIL"))
    //?}
    private void mobamputation$copyTrauma(ServerPlayer previous, boolean alive, CallbackInfo ci) {
        if (alive) {
            ((PlayerAmputationAccess) this).mobamputation$copyTraumaFrom((PlayerAmputationAccess) previous);
        } else {
            ((PlayerAmputationAccess) this).mobamputation$clearTrauma();
        }
    }

    // ServerPlayer#changeDimension sends ClientboundRespawnPacket before it
    // moves this same player into the destination level. Capture the source so
    // RETURN can distinguish a real level change from a same-level transition.
    //? if forge {
    /*@Inject(method = {"changeDimension", "m_5489_"}, at = @At("HEAD"))*/
    //?} else {
    @Inject(method = "changeDimension", at = @At("HEAD"))
    //?}
    private void mobamputation$captureDimensionSource(
            DimensionTransition transition,
            CallbackInfoReturnable<Entity> cir
    ) {
        mobamputation$levelBeforeDimensionChange = ((ServerPlayer) (Object) this).serverLevel();
    }

    //? if forge {
    /*@Inject(method = {"changeDimension", "m_5489_"}, at = @At("RETURN"))*/
    //?} else {
    @Inject(method = "changeDimension", at = @At("RETURN"))
    //?}
    private void mobamputation$syncTraumaAfterDimensionChange(
            DimensionTransition transition,
            CallbackInfoReturnable<Entity> cir
    ) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        ServerLevel source = mobamputation$levelBeforeDimensionChange;
        mobamputation$levelBeforeDimensionChange = null;
        if (cir.getReturnValue() == player && source != null && player.serverLevel() != source) {
            // Vanilla has already queued the respawn packet, installed the
            // destination level and teleported the player. Queue our durable
            // state afterward so the new ClientLevel, not the old one, owns it.
            MobAmputationNetworking.sendPlayerState(player, player);
        }
    }
}
