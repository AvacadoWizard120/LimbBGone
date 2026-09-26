package io.github.avacadowizard120.mobamputation.mixin;

import io.github.avacadowizard120.mobamputation.state.SkeletonAmputationAccess;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(
        value = AbstractSkeleton.class
        //? if forge {
        /*, remap = false*/
        //?}
)
public abstract class AbstractSkeletonMixin extends Monster implements SkeletonAmputationAccess {
    @Shadow(aliases = "f_32130_") @Final private RangedBowAttackGoal<AbstractSkeleton> bowGoal;

    protected AbstractSkeletonMixin(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public void mobamputation$removeBowArm() {
        goalSelector.removeGoal(bowGoal);
        goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.2D, false));
    }
}
