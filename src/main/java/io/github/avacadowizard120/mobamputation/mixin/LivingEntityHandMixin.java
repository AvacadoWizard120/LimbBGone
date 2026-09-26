package io.github.avacadowizard120.mobamputation.mixin;

import io.github.avacadowizard120.mobamputation.api.Limb;
import io.github.avacadowizard120.mobamputation.state.PlayerAmputationAccess;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Makes amputated player hands authoritative empty hands during game logic. */
@Mixin(
        value = LivingEntity.class
        //? if forge {
        /*, remap = false*/
        //?}
)
public abstract class LivingEntityHandMixin {
    //? if forge {
    /*@Inject(method = {"getMainHandItem", "m_21205_"}, at = @At("HEAD"), cancellable = true)*/
    //?} else {
    @Inject(method = "getMainHandItem", at = @At("HEAD"), cancellable = true)
    //?}
    private void mobamputation$emptyDetachedMainHand(CallbackInfoReturnable<ItemStack> cir) {
        if (mobamputation$isDetachedHand(InteractionHand.MAIN_HAND)) {
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }

    //? if forge {
    /*@Inject(method = {"getOffhandItem", "m_21206_"}, at = @At("HEAD"), cancellable = true)*/
    //?} else {
    @Inject(method = "getOffhandItem", at = @At("HEAD"), cancellable = true)
    //?}
    private void mobamputation$emptyDetachedOffhand(CallbackInfoReturnable<ItemStack> cir) {
        if (mobamputation$isDetachedHand(InteractionHand.OFF_HAND)) {
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }

    //? if forge {
    /*@Inject(method = {"getItemInHand", "m_21120_"}, at = @At("HEAD"), cancellable = true)*/
    //?} else {
    @Inject(method = "getItemInHand", at = @At("HEAD"), cancellable = true)
    //?}
    private void mobamputation$emptyDetachedHand(
            InteractionHand hand,
            CallbackInfoReturnable<ItemStack> cir
    ) {
        if (mobamputation$isDetachedHand(hand)) {
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }

    @Unique
    private boolean mobamputation$isDetachedHand(InteractionHand hand) {
        if (!((Object) this instanceof Player player) || player.level().isClientSide) {
            return false;
        }
        HumanoidArm physicalArm = hand == InteractionHand.MAIN_HAND
                ? player.getMainArm()
                : player.getMainArm().getOpposite();
        Limb limb = physicalArm == HumanoidArm.LEFT ? Limb.LEFT_ARM : Limb.RIGHT_ARM;
        return ((PlayerAmputationAccess) player).mobamputation$isDetached(limb);
    }
}
