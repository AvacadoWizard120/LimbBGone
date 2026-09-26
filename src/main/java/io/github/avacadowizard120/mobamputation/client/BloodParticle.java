package io.github.avacadowizard120.mobamputation.client;

import io.github.avacadowizard120.mobamputation.api.BloodProperties;
import io.github.avacadowizard120.mobamputation.config.MobAmputationConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Modern rendering shell around the exact upstream blood-particle physics. */
public final class BloodParticle extends TextureSheetParticle {
    private static final double SURFACE_PROBE_DISTANCE = 0.0125D;
    private static final double MAXIMUM_RELEASE_SPEED = 0.12D;
    private static final double ENTITY_QUERY_PADDING = 0.02D;
    private static final double MAXIMUM_ENTITY_SWEEP_DISTANCE = 4.0D;
    private static final double MAXIMUM_ATTACHED_FOLLOW_DISTANCE = 4.0D;
    private static final double ATTACHMENT_SURFACE_OFFSET = 0.008D;
    private static final double MINIMUM_ENTITY_DIMENSION = 1.0E-4D;
    private static final double COLLISION_EPSILON = 1.0E-7D;
    private static final int MAXIMUM_ENTITY_COLLISION_CANDIDATES = 16;
    private static final int MINIMUM_REATTACH_DELAY = 5;
    private static final int REATTACH_DELAY_VARIANCE = 6;

    private final BloodProperties bloodProperties;
    private final boolean dynamicSurfaces;
    private final boolean surfaceDripping;
    private final boolean entityCollisions;
    private final int surfaceCheckInterval;
    private Entity ignoredSource;
    private int nextSurfaceCheckAge;
    private boolean dynamicFalling;
    private Entity supportingEntity;
    private Entity attachedEntity;
    private Entity recentlyDetachedEntity;
    private AttachmentSurface attachmentSurface;
    private double attachmentAngleOffset;
    private double attachmentHeightFraction;
    private double attachmentTopRadiusFraction;
    private double attachedEntityLastX;
    private double attachedEntityLastY;
    private double attachedEntityLastZ;
    private double attachedEntityVelocityX;
    private double attachedEntityVelocityY;
    private double attachedEntityVelocityZ;
    private int attachmentDwellUntilAge;
    private int attachmentReleaseAge;
    private int nextAttachmentMotionAge;
    private int reattachAfterAge;

    private enum SurfaceContact {
        NONE,
        FLOOR,
        CEILING,
        WEST,
        EAST,
        NORTH,
        SOUTH
    }

    private enum AttachmentSurface {
        TOP,
        SIDE,
        BOTTOM
    }

    private BloodParticle(
            ClientLevel level,
            double x,
            double y,
            double z,
            double velocityX,
            double velocityY,
            double velocityZ,
            Entity source,
            BloodProperties properties
    ) {
        super(level, x, y, z, velocityX, velocityY, velocityZ);
        bloodProperties = properties;
        MobAmputationConfig.BloodSurfacePhysics surfacePhysics = MobAmputationConfig.get().bloodSurfacePhysics();
        dynamicSurfaces = surfacePhysics.enabled();
        surfaceDripping = surfacePhysics.dripping();
        entityCollisions = surfacePhysics.entityCollisions();
        surfaceCheckInterval = surfacePhysics.checkIntervalTicks();
        ignoredSource = source;
        gravity = 0.06F * properties.weight();
        rCol = properties.red();
        gCol = properties.green();
        bCol = properties.blue();
        quadSize *= 1.2F;
        setPower(properties.velocityMultiplier());
        yd += random.nextFloat() * 0.15F * properties.drippiness();
        zd *= 0.4F / (random.nextFloat() * 0.9F + 0.1F);
        xd *= 0.4F / (random.nextFloat() * 0.9F + 0.1F);
        lifetime = (int) (200.0F + 20.0F / (random.nextFloat() * 0.9F + 0.1F));
        AABB originalBounds = getBoundingBox();
        setSize(0.01F, 0.01F);
        // Particle#setSize in 1.12 anchored the replacement box at the old
        // minimum corner instead of recentering it. Keep that odd collision
        // offset because it affects the first movement/contact tick.
        setBoundingBox(new AABB(
                originalBounds.minX,
                originalBounds.minY,
                originalBounds.minZ,
                originalBounds.minX + 0.01D,
                originalBounds.minY + 0.01D,
                originalBounds.minZ + 0.01D
        ));
        // Particle indices 19-22 in 1.12.2's particles.png are exactly the
        // four modern splash sprites. Use this particle's own RNG, matching
        // ParticleBlood#setParticleTextureIndex(19 + rand.nextInt(4)).
        ResourceLocation splashId = ResourceLocation.withDefaultNamespace(
                "splash_" + random.nextInt(4)
        );
        AbstractTexture particleTexture = Minecraft.getInstance()
                .getTextureManager()
                .getTexture(TextureAtlas.LOCATION_PARTICLES);
        if (particleTexture instanceof TextureAtlas particleAtlas) {
            setSprite(particleAtlas.getSprite(splashId));
        }
        // Schedule only after every original RNG use so enabling this feature
        // does not change a particle's initial motion, lifetime, or sprite.
        // Disabled particles consume no additional random value at all.
        nextSurfaceCheckAge = dynamicSurfaces
                ? 1 + random.nextInt(surfaceCheckInterval)
                : Integer.MAX_VALUE;
    }

    public static void spawn(
            ClientLevel level,
            double x,
            double y,
            double z,
            double velocityX,
            double velocityY,
            double velocityZ,
            BloodProperties properties
    ) {
        spawn(level, x, y, z, velocityX, velocityY, velocityZ, null, properties);
    }

    /**
     * Spawns blood while allowing it to leave the entity which emitted it.
     * The source becomes a normal collision target after the droplet exits.
     */
    public static void spawn(
            ClientLevel level,
            double x,
            double y,
            double z,
            double velocityX,
            double velocityY,
            double velocityZ,
            Entity source,
            BloodProperties properties
    ) {
        Minecraft.getInstance().particleEngine.add(new BloodParticle(
                level,
                x,
                y,
                z,
                velocityX,
                velocityY,
                velocityZ,
                source,
                properties
        ));
    }

    @Override
    public void tick() {
        if (age++ >= lifetime) {
            remove();
            return;
        }
        xo = x;
        yo = y;
        zo = z;

        if (attachedEntity != null) {
            tickAttachedToEntity();
            return;
        }

        if (supportingEntity != null && !stillSupportedByEntity()) {
            supportingEntity = null;
            if (!touchesBlock(0.0D, -SURFACE_PROBE_DISTANCE, 0.0D)) {
                beginDynamicFall();
            }
        }

        if (dynamicFalling) {
            tickDynamicFall();
            return;
        }

        if (xd != 0.0D && zd != 0.0D && !onGround) {
            yd -= gravity;
            moveWithEntityCollisions(xd, yd, zd);
            if (attachedEntity != null) {
                return;
            }
            xd *= 0.9800000190734863D;
            yd *= 0.9800000190734863D;
            zd *= 0.9800000190734863D;
            if (onGround) {
                double retained = 1.0D - propertiesStickiness();
                xd *= retained;
                zd *= retained;
                y += 0.2D * propertiesDrippiness();
            }
        }

        // Everything above is the exact upstream-compatible path. With the
        // option disabled, no support probes or alternate motion are run.
        if (!dynamicSurfaces || age < nextSurfaceCheckAge) {
            return;
        }
        nextSurfaceCheckAge = age + surfaceCheckInterval;

        // Only stopped particles need surface work. This avoids collision
        // probes for ordinary airborne blood, which already follows upstream.
        if (!onGround && (x != xo || y != yo || z != zo)) {
            return;
        }

        updateSurfaceMotion(findSurfaceContact());
    }

    private void tickDynamicFall() {
        yd -= gravity;
        moveIgnoringParticleStop(xd, yd, zd);
        xd *= 0.9800000190734863D;
        yd *= 0.9800000190734863D;
        zd *= 0.9800000190734863D;
        if (onGround) {
            dynamicFalling = false;
            double retained = 1.0D - propertiesStickiness();
            xd *= retained;
            zd *= retained;
            y += 0.2D * propertiesDrippiness();
        }
    }

    private void updateSurfaceMotion(SurfaceContact contact) {
        if (contact == SurfaceContact.NONE) {
            beginDynamicFall();
            return;
        }
        if (!surfaceDripping || contact == SurfaceContact.FLOOR) {
            return;
        }
        if (contact == SurfaceContact.CEILING) {
            // Thin blood lets go sooner. A capped chance keeps ceiling drops
            // staggered instead of releasing a whole splash on one tick.
            float releaseChance = Math.min(0.45F, 0.12F * propertiesDrippiness());
            if (random.nextFloat() < releaseChance) {
                beginDynamicFall();
            }
            return;
        }

        double dripDistance = (0.025D + random.nextDouble() * 0.025D)
                * Math.min(2.0F, propertiesDrippiness());
        moveIgnoringParticleStop(0.0D, -dripDistance, 0.0D);
        if (attachedEntity != null) {
            return;
        }
        if (onGround) {
            double retained = 1.0D - propertiesStickiness();
            xd *= retained;
            zd *= retained;
            y += 0.2D * propertiesDrippiness();
        } else if (!touchesSurface(contact)) {
            beginDynamicFall();
        }
    }

    private void beginDynamicFall() {
        dynamicFalling = true;
        onGround = false;
        supportingEntity = null;
        // Ceiling-stuck particles continue accumulating upstream gravity even
        // though Minecraft's private collision latch prevents their movement.
        // Clamp that hidden velocity so releasing one never teleports it.
        yd = Math.max(-MAXIMUM_RELEASE_SPEED, Math.min(-0.02D, yd));
    }

    /**
     * Keeps an adhered droplet on an entity's collision-volume surface without
     * another world query. The normalized anchor follows pose/size changes,
     * while the yaw-relative angle makes stains turn with their carrier.
     */
    private void tickAttachedToEntity() {
        Entity carrier = attachedEntity;
        if (!isValidAttachmentCarrier(carrier)) {
            detachFromEntity(false);
            tickDynamicFall();
            return;
        }

        AABB carrierBounds = carrier.getBoundingBox();
        double carrierDeltaX = carrier.getX() - attachedEntityLastX;
        double carrierDeltaY = carrier.getY() - attachedEntityLastY;
        double carrierDeltaZ = carrier.getZ() - attachedEntityLastZ;
        if (!isFinite(carrierDeltaX, carrierDeltaY, carrierDeltaZ)
                || lengthSqr(carrierDeltaX, carrierDeltaY, carrierDeltaZ)
                > MAXIMUM_ATTACHED_FOLLOW_DISTANCE * MAXIMUM_ATTACHED_FOLLOW_DISTANCE) {
            // Do not draw a one-frame streak across a teleport. The droplet is
            // released at its old position instead of following the jump.
            detachFromEntity(false);
            tickDynamicFall();
            return;
        }

        attachedEntityVelocityX = carrierDeltaX;
        attachedEntityVelocityY = carrierDeltaY;
        attachedEntityVelocityZ = carrierDeltaZ;
        attachedEntityLastX = carrier.getX();
        attachedEntityLastY = carrier.getY();
        attachedEntityLastZ = carrier.getZ();

        Vec3 projected = projectAttachment(carrier, carrierBounds);
        if (!moveToAttachedReference(projected, MAXIMUM_ATTACHED_FOLLOW_DISTANCE)) {
            detachFromEntity(false);
            tickDynamicFall();
            return;
        }

        // Adhesion belongs to entity collisions. Crawling and release remain
        // part of the optional dynamic/dripping surface simulation.
        if (!dynamicSurfaces || !surfaceDripping || age < attachmentDwellUntilAge) {
            return;
        }
        if (age >= attachmentReleaseAge) {
            detachFromEntity(true);
            tickDynamicFall();
            return;
        }
        if (age < nextAttachmentMotionAge) {
            return;
        }
        nextAttachmentMotionAge = age + surfaceCheckInterval;

        advanceAttachment(carrierBounds);
        if (attachedEntity == null) {
            tickDynamicFall();
            return;
        }

        projected = projectAttachment(carrier, carrierBounds);
        if (!moveToAttachedReference(projected, MAXIMUM_ATTACHED_FOLLOW_DISTANCE)) {
            detachFromEntity(false);
            tickDynamicFall();
        }
    }

    private void advanceAttachment(AABB carrierBounds) {
        double drippiness = Math.min(2.0D, propertiesDrippiness());
        double stickRetention = 1.0D - 0.35D * propertiesStickiness();
        double creepDistance = (0.055D + random.nextDouble() * 0.045D)
                * drippiness * stickRetention;

        if (attachmentSurface == AttachmentSurface.TOP) {
            double angle = attachmentAngleOffset + attachmentYawRadians(attachedEntity);
            double radius = horizontalPerimeterRadius(carrierBounds, angle);
            if (!Double.isFinite(radius) || radius <= MINIMUM_ENTITY_DIMENSION) {
                detachFromEntity(false);
                return;
            }
            attachmentTopRadiusFraction = Math.min(
                    1.0D,
                    attachmentTopRadiusFraction + creepDistance / radius
            );
            if (attachmentTopRadiusFraction >= 1.0D) {
                attachmentSurface = AttachmentSurface.SIDE;
                attachmentHeightFraction = 1.0D;
            }
            return;
        }

        if (attachmentSurface == AttachmentSurface.BOTTOM) {
            // Blood clinging beneath an entity hangs briefly, then gravity
            // wins. The hard release age above guarantees eventual release.
            float releaseChance = Math.min(0.75F, 0.18F * propertiesDrippiness());
            if (random.nextFloat() < releaseChance) {
                detachFromEntity(true);
            }
            return;
        }

        double height = carrierBounds.maxY - carrierBounds.minY;
        if (!Double.isFinite(height) || height <= MINIMUM_ENTITY_DIMENSION) {
            detachFromEntity(false);
            return;
        }
        attachmentHeightFraction -= creepDistance / height;
        if (attachmentHeightFraction <= 0.0D) {
            detachFromEntity(true);
        }
    }

    private Vec3 projectAttachment(Entity carrier, AABB bounds) {
        if (!isValidBounds(bounds)) {
            return null;
        }

        double centerX = (bounds.minX + bounds.maxX) * 0.5D;
        double centerZ = (bounds.minZ + bounds.maxZ) * 0.5D;
        double angle = attachmentAngleOffset + attachmentYawRadians(carrier);
        if (!Double.isFinite(angle)) {
            return null;
        }
        double directionX = Math.cos(angle);
        double directionZ = Math.sin(angle);
        double radius = horizontalPerimeterRadius(bounds, angle);
        if (!Double.isFinite(radius)) {
            return null;
        }

        if (attachmentSurface == AttachmentSurface.SIDE) {
            double height = bounds.maxY - bounds.minY;
            return new Vec3(
                    centerX + directionX * (radius + ATTACHMENT_SURFACE_OFFSET),
                    bounds.minY + clamp01(attachmentHeightFraction) * height,
                    centerZ + directionZ * (radius + ATTACHMENT_SURFACE_OFFSET)
            );
        }

        double topRadius = radius * clamp01(attachmentTopRadiusFraction);
        double targetY = attachmentSurface == AttachmentSurface.TOP
                ? bounds.maxY + ATTACHMENT_SURFACE_OFFSET
                : bounds.minY - getBoundingBox().getYsize() - ATTACHMENT_SURFACE_OFFSET;
        return new Vec3(
                centerX + directionX * topRadius,
                targetY,
                centerZ + directionZ * topRadius
        );
    }

    /**
     * Moves the collision box and visible position by the same delta. Blood's
     * legacy render position deliberately differs from its tiny collision box,
     * so rebuilding x/y/z from the box here would cause a visible snap.
     */
    private boolean moveToAttachedReference(Vec3 target, double maximumDistance) {
        if (target == null || !isFinite(target.x, target.y, target.z)) {
            return false;
        }
        AABB bounds = getBoundingBox();
        double deltaX = target.x - (bounds.minX + bounds.maxX) * 0.5D;
        double deltaY = target.y - bounds.minY;
        double deltaZ = target.z - (bounds.minZ + bounds.maxZ) * 0.5D;
        if (!isFinite(deltaX, deltaY, deltaZ)
                || lengthSqr(deltaX, deltaY, deltaZ) > maximumDistance * maximumDistance) {
            return false;
        }
        if (deltaX != 0.0D || deltaY != 0.0D || deltaZ != 0.0D) {
            setBoundingBox(bounds.move(deltaX, deltaY, deltaZ));
            x += deltaX;
            y += deltaY;
            z += deltaZ;
        }
        return true;
    }

    private boolean isValidAttachmentCarrier(Entity carrier) {
        return carrier != null
                && !carrier.isRemoved()
                && carrier.isAlive()
                && !carrier.isSpectator()
                && carrier.level() == level
                && isEligibleEntityType(carrier)
                && isFinite(carrier.getX(), carrier.getY(), carrier.getZ())
                && isValidBounds(carrier.getBoundingBox());
    }

    private boolean tryAttachToEntity(EntityContact contact, Vec3 incomingVelocity) {
        if (!entityCollisions || attachedEntity != null || contact == null) {
            return false;
        }
        float chance = propertiesStickiness();
        if (chance <= 0.0F || random.nextFloat() >= chance) {
            return false;
        }

        Entity carrier = contact.entity();
        if (!isValidAttachmentCarrier(carrier)) {
            return false;
        }
        AABB carrierBounds = carrier.getBoundingBox();
        AABB particleBounds = getBoundingBox();
        double carrierCenterX = (carrierBounds.minX + carrierBounds.maxX) * 0.5D;
        double carrierCenterZ = (carrierBounds.minZ + carrierBounds.maxZ) * 0.5D;
        double particleCenterX = (particleBounds.minX + particleBounds.maxX) * 0.5D;
        double particleCenterZ = (particleBounds.minZ + particleBounds.maxZ) * 0.5D;
        double relativeX = particleCenterX - carrierCenterX;
        double relativeZ = particleCenterZ - carrierCenterZ;

        double worldAngle;
        if (relativeX * relativeX + relativeZ * relativeZ > COLLISION_EPSILON * COLLISION_EPSILON) {
            worldAngle = Math.atan2(relativeZ, relativeX);
        } else if (incomingVelocity.x * incomingVelocity.x + incomingVelocity.z * incomingVelocity.z
                > COLLISION_EPSILON * COLLISION_EPSILON) {
            worldAngle = Math.atan2(-incomingVelocity.z, -incomingVelocity.x);
        } else {
            worldAngle = attachmentYawRadians(carrier) + random.nextDouble() * Math.PI * 2.0D;
        }

        attachmentAngleOffset = normalizeRadians(worldAngle - attachmentYawRadians(carrier));
        attachmentSurface = contact.surface();
        double height = carrierBounds.maxY - carrierBounds.minY;
        attachmentHeightFraction = clamp01((particleBounds.minY - carrierBounds.minY) / height);
        double perimeterRadius = horizontalPerimeterRadius(carrierBounds, worldAngle);
        double horizontalDistance = Math.sqrt(relativeX * relativeX + relativeZ * relativeZ);
        attachmentTopRadiusFraction = perimeterRadius <= MINIMUM_ENTITY_DIMENSION
                ? 0.0D
                : clamp01(horizontalDistance / perimeterRadius);

        Entity previousSupportingEntity = supportingEntity;
        boolean previousDynamicFalling = dynamicFalling;
        boolean previousOnGround = onGround;
        double previousVelocityX = xd;
        double previousVelocityY = yd;
        double previousVelocityZ = zd;
        attachedEntity = carrier;
        supportingEntity = null;
        dynamicFalling = false;
        onGround = false;
        xd = 0.0D;
        yd = 0.0D;
        zd = 0.0D;
        attachedEntityLastX = carrier.getX();
        attachedEntityLastY = carrier.getY();
        attachedEntityLastZ = carrier.getZ();
        attachedEntityVelocityX = 0.0D;
        attachedEntityVelocityY = 0.0D;
        attachedEntityVelocityZ = 0.0D;

        if (dynamicSurfaces && surfaceDripping) {
            double drippiness = Math.min(2.0D, propertiesDrippiness());
            int dwellTicks = (int) Math.ceil(
                    (5 + random.nextInt(7) + 10.0D * propertiesStickiness()) / drippiness
            );
            int attachedTicks = (int) Math.ceil(
                    (55 + random.nextInt(36) + 40.0D * propertiesStickiness()) / drippiness
            );
            attachmentDwellUntilAge = age + Math.max(4, Math.min(80, dwellTicks));
            attachmentReleaseAge = age + Math.max(25, Math.min(180, attachedTicks));
            nextAttachmentMotionAge = attachmentDwellUntilAge
                    + random.nextInt(Math.max(1, surfaceCheckInterval));
        } else {
            attachmentDwellUntilAge = Integer.MAX_VALUE;
            attachmentReleaseAge = Integer.MAX_VALUE;
            nextAttachmentMotionAge = Integer.MAX_VALUE;
        }

        Vec3 projected = projectAttachment(carrier, carrierBounds);
        if (!moveToAttachedReference(projected, 0.5D)) {
            clearAttachment();
            supportingEntity = previousSupportingEntity;
            dynamicFalling = previousDynamicFalling;
            onGround = previousOnGround;
            xd = previousVelocityX;
            yd = previousVelocityY;
            zd = previousVelocityZ;
            return false;
        }
        return true;
    }

    private void detachFromEntity(boolean inheritCarrierMotion) {
        Entity formerCarrier = attachedEntity;
        if (formerCarrier == null) {
            beginDynamicFall();
            return;
        }

        recentlyDetachedEntity = formerCarrier;
        reattachAfterAge = age + MINIMUM_REATTACH_DELAY + random.nextInt(REATTACH_DELAY_VARIANCE);
        double inheritedX = inheritCarrierMotion ? attachedEntityVelocityX * 0.35D : 0.0D;
        double inheritedY = inheritCarrierMotion ? attachedEntityVelocityY * 0.20D : 0.0D;
        double inheritedZ = inheritCarrierMotion ? attachedEntityVelocityZ * 0.35D : 0.0D;
        clearAttachment();
        xd = Math.max(-MAXIMUM_RELEASE_SPEED, Math.min(MAXIMUM_RELEASE_SPEED, inheritedX));
        yd = Math.max(-MAXIMUM_RELEASE_SPEED, Math.min(-0.02D, inheritedY - 0.02D));
        zd = Math.max(-MAXIMUM_RELEASE_SPEED, Math.min(MAXIMUM_RELEASE_SPEED, inheritedZ));
        beginDynamicFall();
    }

    private void clearAttachment() {
        attachedEntity = null;
        attachmentSurface = null;
        attachedEntityVelocityX = 0.0D;
        attachedEntityVelocityY = 0.0D;
        attachedEntityVelocityZ = 0.0D;
    }

    private SurfaceContact findSurfaceContact() {
        if (touchesCollision(0.0D, -SURFACE_PROBE_DISTANCE, 0.0D)) {
            return SurfaceContact.FLOOR;
        }
        if (touchesCollision(0.0D, SURFACE_PROBE_DISTANCE, 0.0D)) {
            return SurfaceContact.CEILING;
        }
        if (touchesCollision(-SURFACE_PROBE_DISTANCE, 0.0D, 0.0D)) {
            return SurfaceContact.WEST;
        }
        if (touchesCollision(SURFACE_PROBE_DISTANCE, 0.0D, 0.0D)) {
            return SurfaceContact.EAST;
        }
        if (touchesCollision(0.0D, 0.0D, -SURFACE_PROBE_DISTANCE)) {
            return SurfaceContact.NORTH;
        }
        if (touchesCollision(0.0D, 0.0D, SURFACE_PROBE_DISTANCE)) {
            return SurfaceContact.SOUTH;
        }
        return SurfaceContact.NONE;
    }

    private boolean touchesSurface(SurfaceContact contact) {
        return switch (contact) {
            case FLOOR -> touchesCollision(0.0D, -SURFACE_PROBE_DISTANCE, 0.0D);
            case CEILING -> touchesCollision(0.0D, SURFACE_PROBE_DISTANCE, 0.0D);
            case WEST -> touchesCollision(-SURFACE_PROBE_DISTANCE, 0.0D, 0.0D);
            case EAST -> touchesCollision(SURFACE_PROBE_DISTANCE, 0.0D, 0.0D);
            case NORTH -> touchesCollision(0.0D, 0.0D, -SURFACE_PROBE_DISTANCE);
            case SOUTH -> touchesCollision(0.0D, 0.0D, SURFACE_PROBE_DISTANCE);
            case NONE -> false;
        };
    }

    private boolean touchesCollision(double offsetX, double offsetY, double offsetZ) {
        AABB probe = getBoundingBox().move(offsetX, offsetY, offsetZ);
        return level.getBlockCollisions(null, probe).iterator().hasNext()
                || entityCollisions && touchesEntity(probe);
    }

    private boolean touchesBlock(double offsetX, double offsetY, double offsetZ) {
        AABB probe = getBoundingBox().move(offsetX, offsetY, offsetZ);
        return level.getBlockCollisions(null, probe).iterator().hasNext();
    }

    private boolean stillSupportedByEntity() {
        return !supportingEntity.isRemoved()
                && supportingEntity.isAlive()
                && supportingEntity.level() == level
                && getBoundingBox().move(0.0D, -SURFACE_PROBE_DISTANCE, 0.0D)
                .intersects(supportingEntity.getBoundingBox());
    }

    /**
     * Keeps vanilla particle/block movement untouched unless an eligible
     * entity is actually close enough to the droplet's swept path.
     */
    private void moveWithEntityCollisions(double velocityX, double velocityY, double velocityZ) {
        if (!entityCollisions) {
            move(velocityX, velocityY, velocityZ);
            return;
        }

        Vec3 requested = new Vec3(velocityX, velocityY, velocityZ);
        EntityCollisionBatch collisions = collectEntityCollisions(requested);
        if (collisions.shapes().isEmpty()) {
            move(velocityX, velocityY, velocityZ);
            return;
        }

        Vec3 entityAllowed = collideWithShapes(requested, getBoundingBox(), collisions.shapes());
        move(entityAllowed.x, entityAllowed.y, entityAllowed.z);

        if (entityCollisions) {
            EntityContact contact = findEntityContact(collisions.entities(), requested, entityAllowed);
            if (tryAttachToEntity(contact, requested)) {
                return;
            }
        }

        if (velocityX != entityAllowed.x) {
            xd = 0.0D;
        }
        if (velocityZ != entityAllowed.z) {
            zd = 0.0D;
        }
        if (velocityY != entityAllowed.y && velocityY < 0.0D) {
            onGround = true;
            supportingEntity = findSupportingEntity(collisions.entities());
        }
    }

    /**
     * Particle#move permanently latches after a vertical collision. Dynamic
     * blood needs to move again after that supporting block disappears, so it
     * performs the same swept block collision without using that latch.
     */
    private void moveIgnoringParticleStop(double velocityX, double velocityY, double velocityZ) {
        Vec3 requested = new Vec3(velocityX, velocityY, velocityZ);
        EntityCollisionBatch collisions = collectEntityCollisions(requested);
        Vec3 entityAllowed = collisions.shapes().isEmpty()
                ? requested
                : collideWithShapes(requested, getBoundingBox(), collisions.shapes());
        Vec3 allowed = Entity.collideBoundingBox(null, entityAllowed, getBoundingBox(), level, List.of());
        if (allowed.x != 0.0D || allowed.y != 0.0D || allowed.z != 0.0D) {
            setBoundingBox(getBoundingBox().move(allowed));
            setLocationFromBoundingbox();
        }
        if (entityCollisions) {
            EntityContact contact = findEntityContact(collisions.entities(), requested, entityAllowed);
            if (tryAttachToEntity(contact, requested)) {
                return;
            }
        }
        onGround = velocityY != allowed.y && velocityY < 0.0D;
        if (velocityY != entityAllowed.y && velocityY < 0.0D) {
            supportingEntity = findSupportingEntity(collisions.entities());
        } else if (velocityY != allowed.y) {
            supportingEntity = null;
        }
        if (velocityX != allowed.x) {
            xd = 0.0D;
        }
        if (velocityZ != allowed.z) {
            zd = 0.0D;
        }
    }

    private EntityCollisionBatch collectEntityCollisions(Vec3 requested) {
        if (!entityCollisions) {
            return EntityCollisionBatch.EMPTY;
        }

        AABB currentBounds = getBoundingBox();
        updateIgnoredSource(currentBounds);
        updateRecentlyDetachedEntity();
        Vec3 boundedSweep = new Vec3(
                clampSweep(requested.x),
                clampSweep(requested.y),
                clampSweep(requested.z)
        );
        AABB searchBounds = currentBounds.expandTowards(boundedSweep).inflate(ENTITY_QUERY_PADDING);
        int[] accepted = {0};
        List<Entity> entities = level.getEntities((Entity) null, searchBounds, entity -> {
            if (accepted[0] >= MAXIMUM_ENTITY_COLLISION_CANDIDATES
                    || !isBloodCollidable(entity, currentBounds)) {
                return false;
            }
            accepted[0]++;
            return true;
        });
        if (entities.isEmpty()) {
            return EntityCollisionBatch.EMPTY;
        }

        List<VoxelShape> shapes = new ArrayList<>(entities.size());
        for (Entity entity : entities) {
            shapes.add(Shapes.create(entity.getBoundingBox()));
        }
        return new EntityCollisionBatch(entities, shapes);
    }

    private boolean touchesEntity(AABB probe) {
        AABB currentBounds = getBoundingBox();
        updateIgnoredSource(currentBounds);
        updateRecentlyDetachedEntity();
        int[] accepted = {0};
        return !level.getEntities((Entity) null, probe, entity -> {
            if (accepted[0] >= 1 || !isBloodCollidable(entity, currentBounds)) {
                return false;
            }
            accepted[0]++;
            return true;
        }).isEmpty();
    }

    private boolean isBloodCollidable(Entity entity, AABB currentBounds) {
        if (entity == ignoredSource
                || entity == recentlyDetachedEntity
                || entity.isRemoved()
                || !entity.isAlive()
                || entity.isSpectator()) {
            return false;
        }
        // A droplet can spawn inside its source or a newly-created gib. Do not
        // turn an existing overlap into an invisible cage; collision starts as
        // soon as the droplet has moved outside that particular box.
        if (currentBounds.intersects(entity.getBoundingBox())) {
            return false;
        }
        return isEligibleEntityType(entity);
    }

    private void updateIgnoredSource(AABB currentBounds) {
        if (ignoredSource != null && !currentBounds.intersects(ignoredSource.getBoundingBox())) {
            ignoredSource = null;
        }
    }

    private void updateRecentlyDetachedEntity() {
        if (recentlyDetachedEntity != null
                && (age >= reattachAfterAge || recentlyDetachedEntity.isRemoved())) {
            recentlyDetachedEntity = null;
        }
    }

    private EntityContact findEntityContact(
            List<Entity> candidates,
            Vec3 requested,
            Vec3 entityAllowed
    ) {
        boolean clippedX = Math.abs(requested.x - entityAllowed.x) > COLLISION_EPSILON;
        boolean clippedY = Math.abs(requested.y - entityAllowed.y) > COLLISION_EPSILON;
        boolean clippedZ = Math.abs(requested.z - entityAllowed.z) > COLLISION_EPSILON;
        if (!clippedX && !clippedY && !clippedZ) {
            return null;
        }

        AABB bounds = getBoundingBox();
        EntityContact best = null;
        double bestGap = Double.POSITIVE_INFINITY;
        for (Entity candidate : candidates) {
            AABB candidateBounds = candidate.getBoundingBox();
            if (clippedY && requested.y != 0.0D) {
                double direction = Math.copySign(SURFACE_PROBE_DISTANCE, requested.y);
                if (bounds.move(0.0D, direction, 0.0D).intersects(candidateBounds)) {
                    double gap = requested.y < 0.0D
                            ? Math.abs(bounds.minY - candidateBounds.maxY)
                            : Math.abs(candidateBounds.minY - bounds.maxY);
                    if (gap < bestGap) {
                        bestGap = gap;
                        best = new EntityContact(
                                candidate,
                                requested.y < 0.0D ? AttachmentSurface.TOP : AttachmentSurface.BOTTOM
                        );
                    }
                }
            }
            if (clippedX && requested.x != 0.0D) {
                double direction = Math.copySign(SURFACE_PROBE_DISTANCE, requested.x);
                if (bounds.move(direction, 0.0D, 0.0D).intersects(candidateBounds)) {
                    double gap = requested.x < 0.0D
                            ? Math.abs(bounds.minX - candidateBounds.maxX)
                            : Math.abs(candidateBounds.minX - bounds.maxX);
                    if (gap < bestGap) {
                        bestGap = gap;
                        best = new EntityContact(candidate, AttachmentSurface.SIDE);
                    }
                }
            }
            if (clippedZ && requested.z != 0.0D) {
                double direction = Math.copySign(SURFACE_PROBE_DISTANCE, requested.z);
                if (bounds.move(0.0D, 0.0D, direction).intersects(candidateBounds)) {
                    double gap = requested.z < 0.0D
                            ? Math.abs(bounds.minZ - candidateBounds.maxZ)
                            : Math.abs(candidateBounds.minZ - bounds.maxZ);
                    if (gap < bestGap) {
                        bestGap = gap;
                        best = new EntityContact(candidate, AttachmentSurface.SIDE);
                    }
                }
            }
        }
        return best;
    }

    private Entity findSupportingEntity(List<Entity> candidates) {
        AABB floorProbe = getBoundingBox().move(0.0D, -SURFACE_PROBE_DISTANCE, 0.0D);
        for (Entity candidate : candidates) {
            if (floorProbe.intersects(candidate.getBoundingBox())) {
                return candidate;
            }
        }
        return null;
    }

    private static Vec3 collideWithShapes(Vec3 requested, AABB bounds, List<VoxelShape> shapes) {
        if (shapes.isEmpty()) {
            return requested;
        }

        double allowedX = requested.x;
        double allowedY = requested.y;
        double allowedZ = requested.z;
        if (allowedY != 0.0D) {
            allowedY = Shapes.collide(Direction.Axis.Y, bounds, shapes, allowedY);
            if (allowedY != 0.0D) {
                bounds = bounds.move(0.0D, allowedY, 0.0D);
            }
        }

        boolean zFirst = Math.abs(allowedX) < Math.abs(allowedZ);
        if (zFirst && allowedZ != 0.0D) {
            allowedZ = Shapes.collide(Direction.Axis.Z, bounds, shapes, allowedZ);
            if (allowedZ != 0.0D) {
                bounds = bounds.move(0.0D, 0.0D, allowedZ);
            }
        }
        if (allowedX != 0.0D) {
            allowedX = Shapes.collide(Direction.Axis.X, bounds, shapes, allowedX);
            if (!zFirst && allowedX != 0.0D) {
                bounds = bounds.move(allowedX, 0.0D, 0.0D);
            }
        }
        if (!zFirst && allowedZ != 0.0D) {
            allowedZ = Shapes.collide(Direction.Axis.Z, bounds, shapes, allowedZ);
        }
        return new Vec3(allowedX, allowedY, allowedZ);
    }

    private static double clampSweep(double movement) {
        return Math.max(-MAXIMUM_ENTITY_SWEEP_DISTANCE, Math.min(MAXIMUM_ENTITY_SWEEP_DISTANCE, movement));
    }

    private static boolean isEligibleEntityType(Entity entity) {
        return entity instanceof LivingEntity
                || entity instanceof ItemEntity
                || entity.canBeCollidedWith();
    }

    private static boolean isValidBounds(AABB bounds) {
        return bounds != null
                && Double.isFinite(bounds.minX)
                && Double.isFinite(bounds.minY)
                && Double.isFinite(bounds.minZ)
                && Double.isFinite(bounds.maxX)
                && Double.isFinite(bounds.maxY)
                && Double.isFinite(bounds.maxZ)
                && bounds.maxX - bounds.minX > MINIMUM_ENTITY_DIMENSION
                && bounds.maxY - bounds.minY > MINIMUM_ENTITY_DIMENSION
                && bounds.maxZ - bounds.minZ > MINIMUM_ENTITY_DIMENSION;
    }

    private static double attachmentYawRadians(Entity entity) {
        return Math.toRadians(entity.getVisualRotationYInDegrees());
    }

    private static double horizontalPerimeterRadius(AABB bounds, double angle) {
        double directionX = Math.cos(angle);
        double directionZ = Math.sin(angle);
        double halfWidth = (bounds.maxX - bounds.minX) * 0.5D;
        double halfDepth = (bounds.maxZ - bounds.minZ) * 0.5D;
        double xRadius = Math.abs(directionX) <= COLLISION_EPSILON
                ? Double.POSITIVE_INFINITY
                : halfWidth / Math.abs(directionX);
        double zRadius = Math.abs(directionZ) <= COLLISION_EPSILON
                ? Double.POSITIVE_INFINITY
                : halfDepth / Math.abs(directionZ);
        return Math.min(xRadius, zRadius);
    }

    private static double normalizeRadians(double angle) {
        double fullTurn = Math.PI * 2.0D;
        angle %= fullTurn;
        if (angle > Math.PI) {
            angle -= fullTurn;
        } else if (angle < -Math.PI) {
            angle += fullTurn;
        }
        return angle;
    }

    private static double clamp01(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    private static double lengthSqr(double x, double y, double z) {
        return x * x + y * y + z * z;
    }

    private static boolean isFinite(double x, double y, double z) {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
    }

    private record EntityContact(Entity entity, AttachmentSurface surface) {
    }

    private record EntityCollisionBatch(List<Entity> entities, List<VoxelShape> shapes) {
        private static final EntityCollisionBatch EMPTY = new EntityCollisionBatch(List.of(), List.of());
    }

    private float propertiesStickiness() {
        return bloodProperties.stickiness();
    }

    private float propertiesDrippiness() {
        return bloodProperties.drippiness();
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
    }
}
