package io.github.avacadowizard120.mobamputation.entity;

import io.github.avacadowizard120.mobamputation.api.Limb;
import io.github.avacadowizard120.mobamputation.api.GibProfile;
import io.github.avacadowizard120.mobamputation.api.GibProfileRegistry;
import io.github.avacadowizard120.mobamputation.client.DecapitationCamera;
import io.github.avacadowizard120.mobamputation.client.GibClientEffects;
import io.github.avacadowizard120.mobamputation.client.GibManager;
import io.github.avacadowizard120.mobamputation.client.GibSeveringRules;
import io.github.avacadowizard120.mobamputation.config.MobAmputationConfig;
import io.github.avacadowizard120.mobamputation.logic.ProjectileChancePolicy;
import io.github.avacadowizard120.mobamputation.logic.AmputationLogic;
import io.github.avacadowizard120.mobamputation.network.MobAmputationNetworking;
import io.github.avacadowizard120.mobamputation.network.DetachCause;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The modern client-local counterpart of iChun's {@code EntityGib}.
 *
 * <p>These are deliberately real entities in the client world, not renderer
 * decorations and not registered/networked server entities. That distinction
 * is central to the original mod: each parent gets three independent target
 * boxes and vanilla local entity movement, while only the resulting limb
 * event is sent to a server that has the mod.</p>
 */
public final class GibEntity extends Entity {
    private final LivingEntity parent;
    private final Limb limb;

    private float pitchSpin = 15.0F;
    private float yawSpin = 15.0F;
    private int groundTime;
    private long liveTime;
    private int hitTimeout;
    private boolean attached = true;
    private boolean detach;
    private boolean serverApproved;
    private int pendingRequestId;
    private long pendingRequestExpiresAt;
    private DetachCause pendingCause;
    private boolean beingAttacked;
    private FishingHook fishHook;
    private Entity projectile;
    private Vec3 projectileMotion = Vec3.ZERO;
    private boolean projectileImpactBlood;

    public GibEntity(Level level, LivingEntity parent, Limb limb) {
        // A client-local proxy must not consume a registry entry. MARKER is
        // merely the vanilla type token required by Entity's constructor; all
        // dimensions, ticking, collision and rendering are supplied here.
        super(EntityType.MARKER, level);
        this.parent = parent;
        this.limb = limb;
        this.liveTime = GibManager.clientTicks();
        noCulling = true;
        refreshDimensions();
        setPos(parent.getX(), parent.getY() + parent.getEyeHeight(), parent.getZ());
        syncAttachedTransform();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        // Client-local, exactly like the original EntityGib.
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
        if (limb == Limb.HEAD) {
            if (parent instanceof Skeleton) {
                float width = parent.getBbWidth() + 0.05F;
                return EntityDimensions.fixed(width, parent.getBbWidth() * 0.8F);
            }
            return EntityDimensions.fixed(0.5F, 0.5F);
        }
        return EntityDimensions.fixed(0.2F, 0.25F);
    }

    @Override
    public void tick() {
        // Keep the local camera-only head through the short death/packet
        // window. A lethal decapitation notice can arrive after vanilla has
        // already marked the player dead; respawn cleanup still removes it.
        if (parent == null || (!parent.isAlive() && attached && !GibManager.isLocalCameraHead(this))) {
            discard();
            return;
        }

        super.tick();
        if (hitTimeout > 0) {
            hitTimeout--;
        }

        if (pendingRequestId != 0 && GibManager.clientTicks() > pendingRequestExpiresAt) {
            clearPendingServerDetach();
        }

        if (projectile != null && attached && pendingRequestId == 0) {
            detach = true;
        }
        if (fishHook != null && fishHook.isRemoved() && attached && !detach && pendingRequestId == 0) {
            if (MobAmputationNetworking.serverHasMod()) {
                if (!beginServerDetach(DetachCause.FISHING, fishHook.getId())) {
                    resolveLocalFishingRoll();
                }
            } else {
                resolveLocalFishingRoll();
            }
        }

        if (detach && canFinishDetaching()) {
            finishDetaching();
        }

        if (attached) {
            liveTime = GibManager.clientTicks();
            syncAttachedTransform();
        } else {
            tickDetachedMotion();
            GibClientEffects.tickDetached(this);
        }

        if (!attached && MobAmputationConfig.get().gibPushing()) {
            AABB collisionArea = getBoundingBox().inflate(0.15D, 0.0D, 0.15D);
            List<Entity> entities = level().getEntities(this, collisionArea);
            for (Entity entity : entities) {
                if (entity.isPushable()) {
                    // 1.12 applyEntityCollision was invoked on the other
                    // entity with this gib as its collision partner.
                    entity.push(this);
                }
            }
        }

        boolean preserveCameraHead = DecapitationCamera.isRiding(this)
                && MobAmputationConfig.get().playerTrauma().fatalDecapitation();
        if (onGround()) {
            groundTime++;
            if (!preserveCameraHead && groundTime > MobAmputationConfig.get().gibGroundTime() + 20) {
                discard();
            }
        } else if (groundTime > MobAmputationConfig.get().gibGroundTime()) {
            groundTime--;
        } else {
            groundTime = 0;
        }

        if (!preserveCameraHead
                && liveTime + MobAmputationConfig.get().gibTime() < GibManager.clientTicks()) {
            discard();
        }
    }

    private void tickDetachedMotion() {
        move(MoverType.SELF, getDeltaMovement());

        Vec3 motion = getDeltaMovement().add(0.0D, -0.08D, 0.0D);
        setDeltaMovement(motion.x * 0.91D, motion.y * 0.98D, motion.z * 0.91D);

        if (onGround()) {
            setXRot(getXRot() + (-90.0F - (getXRot() % 360.0F)) / 2.0F);
            setDeltaMovement(getDeltaMovement().scale(0.8D));
        } else if (projectile != null && (horizontalCollision || verticalCollision)) {
            Vec3 collidedMotion = getDeltaMovement();
            if (collidedMotion.x != 0.0D || collidedMotion.z != 0.0D) {
                projectileImpactBlood = true;
            }
            setDeltaMovement(Vec3.ZERO);
        } else {
            // Modern Entity setters reject NaN. Preserve iChun's literal
            // sqrt(x*z) calculation, but keep the last finite yaw when that
            // old formula produces NaN so modern clients do not log every
            // render tick or discard the update.
            xRotO = getXRot();
            yRotO = getYRot();
            setXRot(getXRot() + pitchSpin);
            float nextYaw = getYRot() + yawSpin;
            if (Float.isFinite(nextYaw)) {
                setYRot(nextYaw);
            }
            pitchSpin *= 0.98F;
            yawSpin *= 0.98F;
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (beingAttacked) {
            return false;
        }

        Entity attacker = source.getEntity();
        Entity direct = source.getDirectEntity();
        if (parent.hurtTime > 0 || attacker == parent) {
            if (attacker == parent && direct != parent && direct != null) {
                direct.setDeltaMovement(direct.getDeltaMovement().scale(-10.0D));
                direct.setYRot(direct.getYRot() + 180.0F);
                direct.yRotO += 180.0F;
            }
            return false;
        }

        if (!AmputationLogic.supportsLimb(parent, limb)
                // Player-arm trauma is an authoritative PvP extension. On a
                // vanilla/unmodded server it would otherwise become a fake,
                // one-client-only visual with no bleeding, hand restriction,
                // item drop, or persistence. Upstream likewise rejected all
                // non-head player proxies.
                || parent instanceof Player && limb != Limb.HEAD
                && !MobAmputationNetworking.serverHasMod()
                || parent instanceof Skeleton && limb == Limb.RIGHT_ARM
                && !MobAmputationNetworking.serverHasMod()
                || hitTimeout > 0) {
            return false;
        }

        if (direct instanceof Player player && attached && !detach && pendingRequestId == 0) {
            beingAttacked = true;
            try {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.gameMode != null) {
                    // This is the original forwarding path: one valid attack
                    // packet targets the parent, never this negative-ID proxy.
                    minecraft.gameMode.attack(player, parent);
                }
            } finally {
                beingAttacked = false;
            }
            hitTimeout = 10;

            if (MobAmputationNetworking.serverHasMod()) {
                prepareMeleeSpin();
                if (!beginServerDetach(DetachCause.MELEE, parent.getId())) {
                    if (!GibSeveringRules.rollMelee(this, player.getMainHandItem())) {
                        resetPreparedCause(DetachCause.MELEE);
                        return parent.hurt(source, amount);
                    }
                    detach = true;
                }
            } else {
                if (!GibSeveringRules.rollMelee(this, player.getMainHandItem())) {
                    return parent.hurt(source, amount);
                }
                prepareMeleeSpin();
                detach = true;
            }

            // Upstream performs this second local hurt call after controller
            // forwarding. It sends no second network packet.
            return parent.hurt(source, amount);
        }

        if (direct != null) {
            if (MobAmputationNetworking.serverHasMod()) {
                ProjectileChancePolicy.Decision decision = ProjectileChancePolicy.evaluate(
                        direct,
                        source.is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE)
                );
                if (decision.eligible()) {
                    projectile = direct;
                    projectileMotion = direct.getDeltaMovement();
                    if (!beginServerDetach(DetachCause.PROJECTILE, direct.getId())
                            && !GibSeveringRules.rollProjectile(this, direct, source)) {
                        resetPreparedCause(DetachCause.PROJECTILE);
                    }
                }
            } else if (GibSeveringRules.rollProjectile(this, direct, source)) {
                projectile = direct;
                projectileMotion = direct.getDeltaMovement();
            }
        }

        // Projectile hits are consumed by the gib and never forwarded to the
        // parent, even when the sever roll fails.
        return true;
    }

    private boolean canFinishDetaching() {
        return parent.hurtTime < parent.hurtDuration - 3
                || parent instanceof Player
                || fishHook != null && fishHook.isRemoved()
                || projectile != null;
    }

    private void finishDetaching() {
        detach = false;
        attached = false;

        if (!(parent instanceof Player) && limb == Limb.LEFT_ARM) {
            parent.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        } else if (!(parent instanceof Player) && limb == Limb.RIGHT_ARM) {
            parent.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }

        Vec3 velocity = parent.getDeltaMovement().scale(1.05D)
                .add(0.0D, getRandom().nextDouble() * 0.2D, 0.0D);
        if (fishHook != null && fishHook.isRemoved() && fishHook.getPlayerOwner() != null) {
            Player angler = fishHook.getPlayerOwner();
            velocity = new Vec3(
                    (angler.getX() - parent.getX()) * 0.1D,
                    (angler.getY() - parent.getY()) * 0.1D + getRandom().nextDouble() * 0.4D,
                    (angler.getZ() - parent.getZ()) * 0.1D
            );
        }
        if (projectile != null) {
            velocity = projectileMotion.scale(0.8D);
        }
        setDeltaMovement(velocity);
        hasImpulse = true;

        // This is the first point at which the head has its final launch
        // velocity and is definitively detached. The optional local camera
        // ride therefore starts from the same completed physics state.
        DecapitationCamera.onDetached(this);

        // Negotiated-server candidates are sent before their one authoritative
        // roll. Client-only detaches need no packet. The initial burst remains
        // at the same point in the local detach sequence as upstream.
        GibClientEffects.onDetached(this);
    }

    private void syncAttachedTransform() {
        double x = parent.getX();
        double y = parent.getBoundingBox().minY + 1.275D;
        double z = parent.getZ();
        double oldX = parent.xOld;
        double oldY = y;
        double oldZ = parent.zOld;

        if (limb == Limb.HEAD) {
            GibProfile profile = GibProfileRegistry.find(parent);
            double headHeight = profile == null ? 1.5D : profile.headHeightFromFeet();
            y = parent.getBoundingBox().minY + headHeight;
            oldY = y;
            setYRot(parent.yHeadRot);
            setXRot(parent.getXRot());
            yRotO = parent.yHeadRotO;
            xRotO = parent.xRotO;
        } else {
            double offset = limb == Limb.LEFT_ARM ? 0.350D : -0.350D;
            double radians = Math.toRadians(parent.yBodyRot);
            double offsetX = offset * Math.cos(radians);
            double offsetZ = offset * Math.sin(radians);
            x += offsetX;
            z += offsetZ;
            oldX += offsetX;
            oldZ += offsetZ;
            // Upstream intentionally uses the parent's previous body yaw for
            // both current and previous arm rotation.
            setYRot(parent.yBodyRotO);
            yRotO = parent.yBodyRotO;
            setXRot(-90.0F);
            xRotO = -90.0F;
        }

        xOld = oldX;
        yOld = oldY;
        zOld = oldZ;
        setPos(x, y, z);
        setDeltaMovement(Vec3.ZERO);
    }

    private float signedRandomSpin() {
        float spin = 55.0F * getRandom().nextFloat() + 20.0F;
        return getRandom().nextFloat() < 0.5F ? -spin : spin;
    }

    private void prepareMeleeSpin() {
        float pitch = signedRandomSpin();
        float yaw = signedRandomSpin();
        pitchSpin = pitch * (float) (parent.getDeltaMovement().y + 0.3D);
        // Preserve iChun's literal sqrt(x*z), including NaN for a negative
        // product. Modern rendering keeps the last finite yaw later on.
        yawSpin = yaw
                * (float) (Math.sqrt(parent.getDeltaMovement().x * parent.getDeltaMovement().z) + 0.3D);
    }

    private void resolveLocalFishingRoll() {
        if (GibSeveringRules.rollFishing(this)) {
            detach = true;
        } else {
            fishHook = null;
        }
    }

    private boolean beginServerDetach(DetachCause cause, int sourceEntityId) {
        int requestId = MobAmputationNetworking.sendDetachRequest(parent.getId(), limb, cause, sourceEntityId);
        if (requestId == 0) {
            return false;
        }
        pendingRequestId = requestId;
        pendingRequestExpiresAt = GibManager.clientTicks() + 60L;
        pendingCause = cause;
        return true;
    }

    /** Applies only the response corresponding to this gib's live candidate. */
    public void resolveServerDetach(int requestId, boolean accepted) {
        if (!attached || requestId == 0 || requestId != pendingRequestId) {
            return;
        }
        DetachCause resolvedCause = pendingCause;
        pendingRequestId = 0;
        pendingRequestExpiresAt = 0L;
        pendingCause = null;
        if (accepted) {
            // This request is already consumed server-side. Finish without
            // echoing another C2S packet from finishDetaching().
            serverApproved = true;
            detach = true;
        } else {
            resetPreparedCause(resolvedCause);
        }
    }

    private void clearPendingServerDetach() {
        DetachCause abandonedCause = pendingCause;
        pendingRequestId = 0;
        pendingRequestExpiresAt = 0L;
        pendingCause = null;
        resetPreparedCause(abandonedCause);
    }

    private void resetPreparedCause(DetachCause cause) {
        if (cause == DetachCause.PROJECTILE) {
            projectile = null;
            projectileMotion = Vec3.ZERO;
        } else if (cause == DetachCause.FISHING) {
            fishHook = null;
        } else if (cause == DetachCause.MELEE) {
            pitchSpin = 15.0F;
            yawSpin = 15.0F;
        }
    }

    public void setFishHook(FishingHook hook) {
        fishHook = hook;
    }

    /** Sets the same flag as the original server-to-client packet. */
    public void requestRemoteDetach() {
        if (attached) {
            // The requester's explicit result arrives before the observer
            // broadcast. Do not replace its cause-specific launch/spin with
            // the default remote values when that broadcast follows.
            if (serverApproved) {
                return;
            }
            clearPendingServerDetach();
            // A server notice is the terminal acknowledgement of another
            // client's sever event. Never echo it back as a fresh C2S request.
            detach = true;
        }
    }

    /**
     * Applies a persisted state snapshot without replaying the sever event.
     * No launch, blood burst, equipment mutation, packet, or camera takeover
     * occurs; the proxy simply becomes an already-missing body part.
     */
    public void applyDetachedSnapshot() {
        // A live result/notice and its durable snapshot are delivered in the
        // same network batch.  Preserve the queued live sever so the next
        // entity tick still performs the cause-specific launch, blood burst,
        // equipment update, and camera takeover.  Snapshots are silent only
        // for clients that did not receive a live event.
        if (!attached || detach || serverApproved) {
            return;
        }
        clearPendingServerDetach();
        attached = false;
        detach = false;
        setDeltaMovement(Vec3.ZERO);
        setPos(parent.getX(), parent.getBoundingBox().minY, parent.getZ());
        setInvisible(true);
        noPhysics = true;
    }

    public LivingEntity parent() {
        return parent;
    }

    public Limb limb() {
        return limb;
    }

    public boolean isAttached() {
        return attached;
    }

    public int groundTime() {
        return groundTime;
    }

    public boolean consumeProjectileImpactBlood() {
        boolean result = projectileImpactBlood;
        projectileImpactBlood = false;
        return result;
    }

    @Override
    public boolean isPickable() {
        return isAlive() && !isInvisible() && !GibManager.isLocalProxy(this) && GibManager.shouldRender(this);
    }

    @Override
    public float getPickRadius() {
        // A Creeper keeps its vanilla collision box, so its attached head
        // proxy needs a modest selection halo to remain reachable above the
        // torso without altering movement/collision semantics.
        return parent instanceof net.minecraft.world.entity.monster.Creeper && attached ? 0.3F : 0.0F;
    }

    @Override
    public boolean canBeCollidedWith() {
        // The new local head exists only so a detach notice has something to
        // hand to the camera. While attached it must not add an invisible
        // collision body inside the full-size local player.
        return isAlive() && (!attached || !GibManager.isLocalProxy(this));
    }

    @Override
    public boolean isPushable() {
        return isAlive() && (!attached || !GibManager.isLocalProxy(this));
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }
}
