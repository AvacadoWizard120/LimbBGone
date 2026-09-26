package io.github.avacadowizard120.mobamputation.state;

import io.github.avacadowizard120.mobamputation.api.Limb;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

/** Persistent server-owned trauma attached directly to every player. */
public interface PlayerAmputationAccess {
    boolean mobamputation$isDetached(Limb limb);

    int mobamputation$getLimbMask();

    void mobamputation$setDetached(Limb limb, boolean detached);

    int mobamputation$getBleedTicks(Limb limb);

    void mobamputation$setBleedTicks(Limb limb, int ticks);

    int mobamputation$getArmBleedPulseTicks();

    void mobamputation$setArmBleedPulseTicks(int ticks);

    int mobamputation$getHeadBleedPulseTicks();

    void mobamputation$setHeadBleedPulseTicks(int ticks);

    int mobamputation$getFatalHeadTicks();

    void mobamputation$setFatalHeadTicks(int ticks);

    @Nullable UUID mobamputation$getResponsibleAttacker();

    void mobamputation$setResponsibleAttacker(@Nullable UUID attacker);

    void mobamputation$writeTrauma(CompoundTag tag);

    void mobamputation$readTrauma(CompoundTag tag);

    void mobamputation$clearTrauma();

    void mobamputation$copyTraumaFrom(PlayerAmputationAccess previous);
}
