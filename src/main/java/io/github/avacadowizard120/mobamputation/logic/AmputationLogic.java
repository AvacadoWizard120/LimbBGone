package io.github.avacadowizard120.mobamputation.logic;

import io.github.avacadowizard120.mobamputation.api.Limb;
import io.github.avacadowizard120.mobamputation.api.GibProfile;
import io.github.avacadowizard120.mobamputation.api.GibProfileRegistry;
import io.github.avacadowizard120.mobamputation.config.MobAmputationConfig;
import io.github.avacadowizard120.mobamputation.state.SkeletonAmputationAccess;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.player.Player;

/** Common target eligibility and the original server-side consequences. */
public final class AmputationLogic {
    /** Literal modern equivalent of the upstream instanceof predicate. */
    public static boolean eligible(LivingEntity target) {
        if (target.isBaby()) {
            return false;
        }
        if (target instanceof Player) {
            return MobAmputationConfig.get().playerGibs();
        }
        if (target instanceof Creeper) {
            return MobAmputationConfig.get().creeperAmputationEnabled();
        }
        if (target instanceof ZombieVillager) {
            return false;
        }
        if (target instanceof Zombie || target.getType() == EntityType.SKELETON) {
            return true;
        }
        // Reserved experimental seam for future custom-mob work. Registration
        // is not a supported add-on contract until matching model/render hooks
        // and resource definitions exist.
        return GibProfileRegistry.find(target) != null;
    }

    public static boolean supportsLimb(LivingEntity target, Limb limb) {
        if (!eligible(target)) {
            return false;
        }
        if (target instanceof Player && limb != Limb.HEAD) {
            return MobAmputationConfig.get().playerArmAmputation();
        }
        GibProfile profile = GibProfileRegistry.find(target);
        return profile != null && profile.supports(limb);
    }

    public static void finishAmputation(LivingEntity target, Limb limb, Entity attacker) {
        if (target instanceof Player player) {
            MobAmputationConfig.PlayerTrauma trauma = MobAmputationConfig.get().playerTrauma();
            if (limb == Limb.HEAD || limb != Limb.HEAD && trauma.armAmputation()) {
                PlayerTraumaManager.amputate(player, limb, attacker);
            }
        }
        if (limb == Limb.HEAD && MobAmputationConfig.get().headlessDeath() && !(target instanceof Player)) {
            HeadlessDeathManager.schedule(target, attacker instanceof Player player ? player : null);
        }

        // Upstream only changes a normal Skeleton when its type-2/right arm
        // is removed; it does not adapt strays, wither skeletons or handedness.
        if (target instanceof Skeleton skeleton
                && target.getType() == EntityType.SKELETON
                && limb == Limb.RIGHT_ARM) {
            ((SkeletonAmputationAccess) skeleton).mobamputation$removeBowArm();
        }
    }

    private AmputationLogic() {
    }
}
