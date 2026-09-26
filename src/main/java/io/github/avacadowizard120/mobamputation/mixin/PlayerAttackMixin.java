package io.github.avacadowizard120.mobamputation.mixin;

import io.github.avacadowizard120.mobamputation.api.Limb;
import io.github.avacadowizard120.mobamputation.logic.ArmorProtectionPolicy;
import io.github.avacadowizard120.mobamputation.logic.LimbHitResolver;
import io.github.avacadowizard120.mobamputation.logic.ServerAmputationAuthority;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Captures the exact weapon for a successful server-handled melee attack. */
@Mixin(
        value = Player.class
        //? if forge {
        /*, remap = false*/
        //?}
)
public abstract class PlayerAttackMixin {
    @Unique private ItemStack mobamputation$weapon = ItemStack.EMPTY;
    @Unique private int mobamputation$previousAttackTimestamp;
    @Unique private ArmorProtectionPolicy.ArmorSnapshot mobamputation$targetArmor;
    @Unique private Limb mobamputation$targetLimb;

    //? if forge {
    /*@Inject(method = {"attack", "m_5706_"}, at = @At("HEAD"))*/
    //?} else {
    @Inject(method = "attack", at = @At("HEAD"))
    //?}
    private void mobamputation$captureAttackWeapon(Entity target, CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (self instanceof ServerPlayer && target instanceof LivingEntity) {
            mobamputation$weapon = self.getMainHandItem().copy();
            mobamputation$previousAttackTimestamp = self.getLastHurtMobTimestamp();
            mobamputation$targetArmor = target instanceof Player playerTarget
                    ? ArmorProtectionPolicy.snapshot(playerTarget)
                    : null;
            mobamputation$targetLimb = LimbHitResolver.melee(self, (LivingEntity) target);
        } else {
            mobamputation$weapon = ItemStack.EMPTY;
            mobamputation$targetArmor = null;
            mobamputation$targetLimb = null;
        }
    }

    //? if forge {
    /*@Inject(method = {"attack", "m_5706_"}, at = @At("RETURN"))*/
    //?} else {
    @Inject(method = "attack", at = @At("RETURN"))
    //?}
    private void mobamputation$recordAcceptedAttack(Entity target, CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (self instanceof ServerPlayer serverPlayer
                && target instanceof LivingEntity living
                && self.getLastHurtMob() == living
                && self.getLastHurtMobTimestamp() != mobamputation$previousAttackTimestamp) {
            ServerAmputationAuthority.recordMelee(
                    serverPlayer,
                    living,
                    mobamputation$weapon,
                    mobamputation$targetArmor,
                    mobamputation$targetLimb
            );
        }
        mobamputation$weapon = ItemStack.EMPTY;
        mobamputation$targetArmor = null;
        mobamputation$targetLimb = null;
    }
}
