package io.github.avacadowizard120.mobamputation.client.dismemberment;

import io.github.avacadowizard120.mobamputation.client.GibManager;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Client-local physical body part created when a supported mob dies. */
public final class DeathGibEntity extends Entity {
    private final LivingEntity parent;
    private final DeathGibPart part;
    private final long liveTime;
    private float pitchSpin;
    private float yawSpin;
    private int groundTime;
    private boolean explosion;

    public DeathGibEntity(Level level, LivingEntity parent, DeathGibPart part) {
        super(EntityType.MARKER, level);
        this.parent = parent;
        this.part = part;
        this.liveTime = GibManager.clientTicks();
        noCulling = true;
        refreshDimensions();

        Vec3 parentMotion = parent.getDeltaMovement();
        setDeltaMovement(
                parentMotion.x + (getRandom().nextDouble() - getRandom().nextDouble()) * 0.25D,
                parentMotion.y,
                parentMotion.z + (getRandom().nextDouble() - getRandom().nextDouble()) * 0.25D
        );

        setYRot(parent.yBodyRotO);
        yRotO = parent.getYRot();
        setXRot(parent.getXRot());
        xRotO = parent.getXRot();
        placeAtOriginalBodyPart();

        float pitch = getRandom().nextInt(45) + 5.0F + getRandom().nextFloat();
        float yaw = getRandom().nextInt(45) + 5.0F + getRandom().nextFloat();
        if (getRandom().nextInt(2) == 0) {
            pitch *= -1.0F;
        }
        if (getRandom().nextInt(2) == 0) {
            yaw *= -1.0F;
        }
        Vec3 motion = getDeltaMovement();
        pitchSpin = pitch * (float) (motion.y + 0.3D);
        // Preserve the upstream sqrt(x*z), including its possible NaN. The
        // rotation setter is guarded during ticking because modern Entity
        // rejects non-finite rotations.
        yawSpin = yaw * (float) (Math.sqrt(motion.x * motion.z) + 0.3D);
    }

    private void placeAtOriginalBodyPart() {
        double x = parent.getX();
        double y = parent.getBoundingBox().minY;
        double z = parent.getZ();
        double yawRadians = Math.toRadians(parent.yBodyRot);

        if (part == DeathGibPart.HEAD) {
            setYRot(parent.yHeadRot);
            y += parent instanceof Creeper ? 1.25D : 1.5D;
        } else if (part.isArm()) {
            double lateral = parent instanceof Skeleton ? 0.3D : 0.35D;
            double longitudinal = -0.25D;
            if (part == DeathGibPart.RIGHT_ARM) {
                lateral *= -1.0D;
            }
            x += lateral * Math.cos(yawRadians) + longitudinal * Math.sin(yawRadians);
            z += lateral * Math.sin(yawRadians) - longitudinal * Math.cos(yawRadians);
            y += 1.25D + (parent instanceof Skeleton ? 0.15D : 0.0D);
            setXRot(-90.0F);
            xRotO = -90.0F;
        } else if (part == DeathGibPart.BODY) {
            y += parent instanceof Creeper ? 0.75D : 1.0D;
        } else if (part.isLeg()) {
            double lateral = part == DeathGibPart.RIGHT_LEG ? -0.125D : 0.125D;
            x += lateral * Math.cos(yawRadians);
            z += lateral * Math.sin(yawRadians);
            y += 0.375D;
        } else {
            double lateral = part.upstreamType() % 2 == 1 ? -0.125D : 0.125D;
            double longitudinal = part.upstreamType() >= 8 ? 0.25D : -0.25D;
            x += lateral * Math.cos(yawRadians) + longitudinal * Math.sin(yawRadians);
            z += lateral * Math.sin(yawRadians) - longitudinal * Math.cos(yawRadians);
            y += 0.3125D;
        }

        setPos(x, y, z);
        xOld = x;
        yOld = y;
        zOld = z;
    }

    /** Applies the exact TNT/creeper launch curve from Mob Dismemberment. */
    public void launchFromExplosion(Vec3 origin, double baseMagnitude) {
        double distance = Math.sqrt(parent.distanceToSqr(origin)) / 2.0D;
        distance = Math.max(distance * distance, 0.1D);
        double magnitude = baseMagnitude / distance;
        magnitude = magnitude * magnitude * 0.2D;

        Vec3 motion = getDeltaMovement();
        setDeltaMovement(
                motion.x * magnitude,
                (getY() - origin.y) * 0.4D + 0.22D,
                motion.z * magnitude
        );
        explosion = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return EntityDimensions.fixed(part.width(), part.height());
    }

    @Override
    public void tick() {
        if (parent == null) {
            discard();
            return;
        }

        if (explosion) {
            Vec3 motion = getDeltaMovement();
            setDeltaMovement(motion.x / 0.92D, motion.y / 0.95D, motion.z / 0.92D);
        }

        super.tick();
        move(MoverType.SELF, getDeltaMovement());
        Vec3 motion = getDeltaMovement();
        setDeltaMovement(motion.x * 0.91D, (motion.y - 0.08D) * 0.98D, motion.z * 0.91D);

        if (isInWater()) {
            Vec3 waterMotion = getDeltaMovement();
            setDeltaMovement(waterMotion.x, 0.3D, waterMotion.z);
            pitchSpin = 0.0F;
            yawSpin = 0.0F;
        }

        boolean resting = onGround() || updateInWaterStateAndDoFluidPushing();
        if (resting) {
            setXRot(getXRot() + (-90.0F - getXRot() % 360.0F) / 2.0F);
            setDeltaMovement(getDeltaMovement().scale(0.8D));
        } else {
            float nextPitch = getXRot() + pitchSpin;
            float nextYaw = getYRot() + yawSpin;
            if (Float.isFinite(nextPitch)) {
                setXRot(nextPitch);
            }
            if (Float.isFinite(nextYaw)) {
                setYRot(nextYaw);
            }
            pitchSpin *= 0.98F;
            yawSpin *= 0.98F;
        }

        if (DeathDismembermentSettings.gibPushing()) {
            pushNearbyEntities();
        }

        boolean groundedOrWetForLifetime = onGround() || updateInWaterStateAndDoFluidPushing();
        if (groundedOrWetForLifetime) {
            groundTime++;
            if (groundTime > DeathDismembermentSettings.gibGroundTime() + 20) {
                discard();
            }
        } else if (groundTime > DeathDismembermentSettings.gibGroundTime()) {
            groundTime--;
        } else {
            groundTime = 0;
        }

        if (liveTime + DeathDismembermentSettings.gibLifetime() < GibManager.clientTicks()) {
            discard();
        }
    }

    private void pushNearbyEntities() {
        AABB area = getBoundingBox().inflate(0.15D, 0.0D, 0.15D);
        List<Entity> entities = level().getEntities(this, area);
        for (Entity entity : entities) {
            if (entity instanceof DeathGibEntity gib && !gib.onGround()) {
                continue;
            }
            if (entity.isPushable()) {
                entity.push(this);
            }
        }
    }

    public LivingEntity parent() {
        return parent;
    }

    public DeathGibPart part() {
        return part;
    }

    public int groundTime() {
        return groundTime;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }
}
