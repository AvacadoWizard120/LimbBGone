package io.github.avacadowizard120.mobamputation.mixin;

import io.github.avacadowizard120.mobamputation.api.Limb;
import io.github.avacadowizard120.mobamputation.state.PlayerAmputationAccess;
import net.minecraft.world.damagesource.DamageSource;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Stores trauma in vanilla player NBT so reconnecting cannot cancel bleeding. */
@Mixin(
        value = Player.class
        //? if forge {
        /*, remap = false*/
        //?}
)
public abstract class PlayerMixin implements PlayerAmputationAccess {
    @Unique private static final String MOBAMPUTATION_TRAUMA = "MobAmputationTrauma";
    @Unique private int mobamputation$limbMask;
    @Unique private int mobamputation$leftArmBleedTicks;
    @Unique private int mobamputation$rightArmBleedTicks;
    @Unique private int mobamputation$armBleedPulseTicks;
    @Unique private int mobamputation$headBleedPulseTicks;
    @Unique private int mobamputation$fatalHeadTicks;
    @Unique private UUID mobamputation$responsibleAttacker;

    @Override
    public boolean mobamputation$isDetached(Limb limb) {
        return (mobamputation$limbMask & Byte.toUnsignedInt(limb.bit())) != 0;
    }

    @Override
    public int mobamputation$getLimbMask() {
        return mobamputation$limbMask;
    }

    @Override
    public void mobamputation$setDetached(Limb limb, boolean detached) {
        int bit = Byte.toUnsignedInt(limb.bit());
        if (detached) {
            mobamputation$limbMask |= bit;
        } else {
            mobamputation$limbMask &= ~bit;
        }
    }

    @Override
    public int mobamputation$getBleedTicks(Limb limb) {
        return limb == Limb.LEFT_ARM ? mobamputation$leftArmBleedTicks
                : limb == Limb.RIGHT_ARM ? mobamputation$rightArmBleedTicks : 0;
    }

    @Override
    public void mobamputation$setBleedTicks(Limb limb, int ticks) {
        int bounded = Math.max(0, ticks);
        if (limb == Limb.LEFT_ARM) {
            mobamputation$leftArmBleedTicks = bounded;
        } else if (limb == Limb.RIGHT_ARM) {
            mobamputation$rightArmBleedTicks = bounded;
        }
    }

    @Override public int mobamputation$getArmBleedPulseTicks() { return mobamputation$armBleedPulseTicks; }
    @Override public void mobamputation$setArmBleedPulseTicks(int ticks) { mobamputation$armBleedPulseTicks = Math.max(0, ticks); }
    @Override public int mobamputation$getHeadBleedPulseTicks() { return mobamputation$headBleedPulseTicks; }
    @Override public void mobamputation$setHeadBleedPulseTicks(int ticks) { mobamputation$headBleedPulseTicks = Math.max(0, ticks); }
    @Override public int mobamputation$getFatalHeadTicks() { return mobamputation$fatalHeadTicks; }
    @Override public void mobamputation$setFatalHeadTicks(int ticks) { mobamputation$fatalHeadTicks = Math.max(0, ticks); }
    @Override public @Nullable UUID mobamputation$getResponsibleAttacker() { return mobamputation$responsibleAttacker; }
    @Override public void mobamputation$setResponsibleAttacker(@Nullable UUID attacker) { mobamputation$responsibleAttacker = attacker; }

    @Override
    public void mobamputation$writeTrauma(CompoundTag root) {
        if (mobamputation$limbMask == 0 && mobamputation$leftArmBleedTicks == 0
                && mobamputation$rightArmBleedTicks == 0 && mobamputation$fatalHeadTicks == 0) {
            root.remove(MOBAMPUTATION_TRAUMA);
            return;
        }
        CompoundTag trauma = new CompoundTag();
        trauma.putInt("Limbs", mobamputation$limbMask);
        trauma.putInt("LeftArmBleed", mobamputation$leftArmBleedTicks);
        trauma.putInt("RightArmBleed", mobamputation$rightArmBleedTicks);
        trauma.putInt("ArmBleedPulse", mobamputation$armBleedPulseTicks);
        trauma.putInt("HeadBleedPulse", mobamputation$headBleedPulseTicks);
        trauma.putInt("FatalHead", mobamputation$fatalHeadTicks);
        if (mobamputation$responsibleAttacker != null) {
            trauma.putUUID("Attacker", mobamputation$responsibleAttacker);
        }
        root.put(MOBAMPUTATION_TRAUMA, trauma);
    }

    @Override
    public void mobamputation$readTrauma(CompoundTag root) {
        mobamputation$clearTrauma();
        if (!root.contains(MOBAMPUTATION_TRAUMA)) {
            return;
        }
        CompoundTag trauma = root.getCompound(MOBAMPUTATION_TRAUMA);
        mobamputation$limbMask = trauma.getInt("Limbs");
        mobamputation$leftArmBleedTicks = Math.max(0, trauma.getInt("LeftArmBleed"));
        mobamputation$rightArmBleedTicks = Math.max(0, trauma.getInt("RightArmBleed"));
        // The single legacy key is accepted so dev/test worlds made during
        // the pre-release implementation do not lose their active timer.
        int legacyPulse = Math.max(0, trauma.getInt("BleedPulse"));
        mobamputation$armBleedPulseTicks = trauma.contains("ArmBleedPulse")
                ? Math.max(0, trauma.getInt("ArmBleedPulse")) : legacyPulse;
        mobamputation$headBleedPulseTicks = trauma.contains("HeadBleedPulse")
                ? Math.max(0, trauma.getInt("HeadBleedPulse")) : legacyPulse;
        mobamputation$fatalHeadTicks = Math.max(0, trauma.getInt("FatalHead"));
        mobamputation$responsibleAttacker = trauma.hasUUID("Attacker") ? trauma.getUUID("Attacker") : null;
    }

    @Override
    public void mobamputation$clearTrauma() {
        mobamputation$limbMask = 0;
        mobamputation$leftArmBleedTicks = 0;
        mobamputation$rightArmBleedTicks = 0;
        mobamputation$armBleedPulseTicks = 0;
        mobamputation$headBleedPulseTicks = 0;
        mobamputation$fatalHeadTicks = 0;
        mobamputation$responsibleAttacker = null;
    }

    @Override
    public void mobamputation$copyTraumaFrom(PlayerAmputationAccess previous) {
        mobamputation$limbMask = 0;
        for (Limb limb : Limb.values()) {
            mobamputation$setDetached(limb, previous.mobamputation$isDetached(limb));
        }
        mobamputation$leftArmBleedTicks = previous.mobamputation$getBleedTicks(Limb.LEFT_ARM);
        mobamputation$rightArmBleedTicks = previous.mobamputation$getBleedTicks(Limb.RIGHT_ARM);
        mobamputation$armBleedPulseTicks = previous.mobamputation$getArmBleedPulseTicks();
        mobamputation$headBleedPulseTicks = previous.mobamputation$getHeadBleedPulseTicks();
        mobamputation$fatalHeadTicks = previous.mobamputation$getFatalHeadTicks();
        mobamputation$responsibleAttacker = previous.mobamputation$getResponsibleAttacker();
    }

    //? if forge {
    /*@Inject(method = {"addAdditionalSaveData", "m_7380_"}, at = @At("TAIL"))*/
    //?} else {
    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    //?}
    private void mobamputation$saveTrauma(CompoundTag tag, CallbackInfo ci) {
        mobamputation$writeTrauma(tag);
    }

    //? if forge {
    /*@Inject(method = {"readAdditionalSaveData", "m_7378_"}, at = @At("TAIL"))*/
    //?} else {
    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    //?}
    private void mobamputation$loadTrauma(CompoundTag tag, CallbackInfo ci) {
        mobamputation$readTrauma(tag);
    }

    // Clear after a completed death so a cancellable death hook from another
    // mod cannot heal a surviving player. This still happens before any later
    // save of the dead player on the death screen.
    //? if forge {
    /*@Inject(method = {"die", "m_6667_"}, at = @At("TAIL"))*/
    //?} else {
    @Inject(method = "die", at = @At("TAIL"))
    //?}
    private void mobamputation$clearTraumaOnDeath(DamageSource source, CallbackInfo ci) {
        mobamputation$clearTrauma();
    }
}
