package io.github.avacadowizard120.mobamputation.mixin;

import io.github.avacadowizard120.mobamputation.logic.PlayerTraumaManager;
import io.github.avacadowizard120.mobamputation.network.MobAmputationNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Sends the replacement player's body state after vanilla's respawn packet. */
@Mixin(
        value = PlayerList.class
        //? if forge {
        /*, remap = false*/
        //?}
)
public abstract class PlayerListMixin {
    //? if forge {
    /*@Inject(method = {"respawn", "m_11236_"}, at = @At("RETURN"))*/
    //?} else {
    @Inject(method = "respawn", at = @At("RETURN"))
    //?}
    private void mobamputation$syncRestoredBody(
            ServerPlayer previous,
            boolean keepEverything,
            Entity.RemovalReason removalReason,
            CallbackInfoReturnable<ServerPlayer> cir
    ) {
        ServerPlayer respawned = cir.getReturnValue();
        if (respawned == null) {
            return;
        }
        if (removalReason == Entity.RemovalReason.KILLED) {
            // Belt-and-suspenders protection for death paths supplied by other
            // mods: a true respawn always starts with an intact body, and all
            // connected clients revoke any same-UUID state cached before the
            // old player was removed or moved between dimensions.
            PlayerTraumaManager.restoreIntactBody(respawned);
            return;
        }
        // restoreFrom runs before ClientboundRespawnPacket. Sending there can
        // update the soon-to-be-replaced LocalPlayer and then be lost. This
        // RETURN hook is ordered after vanilla has installed and announced the
        // replacement entity, so its preserved state reaches the new client
        // player instead.
        MobAmputationNetworking.sendPlayerState(respawned, respawned);
    }
}
