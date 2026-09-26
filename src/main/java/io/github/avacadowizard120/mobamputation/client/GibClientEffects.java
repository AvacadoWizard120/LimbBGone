package io.github.avacadowizard120.mobamputation.client;

import io.github.avacadowizard120.mobamputation.api.Limb;
import io.github.avacadowizard120.mobamputation.api.BloodProperties;
import io.github.avacadowizard120.mobamputation.api.GibProfile;
import io.github.avacadowizard120.mobamputation.api.GibProfileRegistry;
import io.github.avacadowizard120.mobamputation.config.MobAmputationConfig;
import io.github.avacadowizard120.mobamputation.entity.GibEntity;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class GibClientEffects {
    public static void onDetached(GibEntity gib) {
        LivingEntity parent = gib.parent();
        if (parent == null) {
            return;
        }
        if (parent.isAlive()) {
            spawnBurst(gib, gib.getX(), gib.getY(), gib.getZ(), MobAmputationConfig.get().bloodCount());
        }
    }

    public static void tickDetached(GibEntity gib) {
        LivingEntity parent = gib.parent();
        if (parent == null || !parent.isAlive()) {
            return;
        }
        MobAmputationConfig.Snapshot config = MobAmputationConfig.get();
        if (gib.consumeProjectileImpactBlood()) {
            spawnBurst(gib, gib.getX(), gib.getY(), gib.getZ(), config.bloodCount());
        }
        if (config.bloodSplurt() && GibManager.isBleeding(parent, gib.limb())
                && gib.getRandom().nextFloat() < 0.1F) {
            Vec3 wound = woundPosition(parent, gib.limb());
            spawnBurst(gib, wound.x, wound.y, wound.z, config.bloodCount() / 2);
        }
    }

    private static Vec3 woundPosition(LivingEntity parent, Limb limb) {
        AABB bounds = parent.getBoundingBox();
        double x = parent.getX();
        double y = bounds.minY + 1.35D;
        double z = parent.getZ();
        if (limb == Limb.HEAD) {
            GibProfile profile = GibProfileRegistry.find(parent);
            y = bounds.minY + (profile == null ? 1.5D : profile.headHeightFromFeet()) + 0.075D;
        } else {
            double offset = limb == Limb.LEFT_ARM ? 0.320D : -0.320D;
            double radians = Math.toRadians(parent.yBodyRot);
            x += offset * Math.cos(radians);
            z += offset * Math.sin(radians);
        }
        return new Vec3(x, y, z);
    }

    private static void spawnBurst(GibEntity gib, double x, double y, double z, int count) {
        LivingEntity parent = gib.parent();
        MobAmputationConfig.Snapshot config = MobAmputationConfig.get();
        if (!(gib.level() instanceof ClientLevel level)
                || parent == null
                || !config.blood()) {
            return;
        }
        GibProfile profile = GibProfileRegistry.find(parent);
        if (profile == null || profile.blood() == null) {
            return;
        }
        BloodProperties blood = profile.blood();
        if (parent instanceof Creeper && !config.creeperAmputation().alwaysGreenBlood()) {
            blood = BloodProperties.original(1.0F, 0.0F, 0.0F);
        }
        // Preserve the original optional yellow-green override for existing
        // non-player red-blooded mobs. Creepers keep their explicit true-green
        // physical profile unless their dedicated option is turned off.
        if (!(parent instanceof Player) && config.greenBlood()
                && blood.red() == 1.0F && blood.green() == 0.0F) {
            blood = BloodProperties.original(1.0F, 1.0F, 0.0F);
        }

        double yaw = Math.toRadians(parent.getYRot());
        double pitch = Math.toRadians(parent.getXRot());
        Vec3 parentMotion = parent.getDeltaMovement();
        for (int index = 0; index < count; index++) {
            double velocityX = -Math.sin(yaw) * Math.cos(pitch) * 0.3D;
            double velocityZ = Math.cos(yaw) * Math.cos(pitch) * 0.3D;
            double velocityY = -Math.sin(pitch) * 0.3D + 0.1D;
            float angle = parent.getRandom().nextFloat() * (float) Math.PI * 2.0F;
            float radius = 0.02F * parent.getRandom().nextFloat();
            velocityX += Math.cos(angle) * radius;
            velocityY += (parent.getRandom().nextFloat() - parent.getRandom().nextFloat()) * 0.1F;
            velocityZ += Math.sin(angle) * radius;

            BloodParticle.spawn(
                    level,
                    x,
                    y + gib.getRandom().nextDouble() * 0.2D,
                    z,
                    parentMotion.x + velocityX,
                    parentMotion.y + velocityY,
                    parentMotion.z + velocityZ,
                    parent,
                    blood
            );
        }
    }

    private GibClientEffects() {
    }
}
