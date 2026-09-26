package io.github.avacadowizard120.mobamputation.logic;

import io.github.avacadowizard120.mobamputation.api.GibProfile;
import io.github.avacadowizard120.mobamputation.api.GibProfileRegistry;
import io.github.avacadowizard120.mobamputation.api.Limb;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import io.github.avacadowizard120.mobamputation.network.MobAmputationNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Reconstructs the same attached proxy boxes on the logical server.
 *
 * <p>The proxies themselves intentionally remain client-only, like iChun's
 * original implementation. These calculations let the server verify which
 * proxy a real attack or projectile intersected instead of trusting a limb
 * ordinal supplied by a client.</p>
 */
public final class LimbHitResolver {
    private static final double MELEE_RAY_LENGTH = 8.0D;
    private static final double NORMAL_TOLERANCE = 0.08D;
    private static final double PROJECTILE_FALLBACK_DISTANCE = 0.20D;
    private static final int PLAYER_HISTORY_TICKS = 8;
    private static final Map<ServerPlayer, ArrayDeque<PlayerTransform>> PLAYER_HISTORY = new WeakHashMap<>();

    public static Limb melee(Player attacker, LivingEntity target) {
        if (attacker == null || target == null) {
            return null;
        }
        Vec3 start = attacker.getEyePosition();
        Vec3 end = start.add(attacker.getLookAngle().scale(MELEE_RAY_LENGTH));
        // Prefer the actual proxy geometry. Inflating every box up front made
        // the head overlap both arms, so valid arm clicks were frequently
        // recorded as HEAD and then rejected against the client's arm request.
        Limb exact = firstRayIntersection(target, start, end, 0.0D, true);
        if (exact != null) {
            return exact;
        }
        // Vanilla's attack packet names only the target entity, not the local
        // hit point. Remote players are rendered a few ticks behind their
        // current server transform, so check a short exact server history
        // before adding any geometric tolerance. Vanilla has already accepted
        // the current target/reach; history can select a body part, never make
        // an otherwise-invalid attack valid.
        Limb historical = firstHistoricalRayIntersection(target, start, end, 0.0D);
        if (historical != null) {
            return historical;
        }
        // The client renders an interpolated remote entity. Only when neither
        // current nor previous exact transform intersects do we admit a small
        // bounded tolerance for movement/latency.
        Limb tolerant = firstRayIntersection(target, start, end, NORMAL_TOLERANCE, true);
        return tolerant != null
                ? tolerant
                : firstHistoricalRayIntersection(target, start, end, NORMAL_TOLERANCE);
    }

    /** Records one bounded server transform for lag-tolerant player limb proof. */
    public static void recordPlayerTransform(ServerPlayer player) {
        if (player == null) {
            return;
        }
        if (player.isRemoved() || !player.isAlive()) {
            PLAYER_HISTORY.remove(player);
            return;
        }
        ArrayDeque<PlayerTransform> history = PLAYER_HISTORY.computeIfAbsent(
                player, ignored -> new ArrayDeque<>(PLAYER_HISTORY_TICKS)
        );
        history.addFirst(new PlayerTransform(
                player.level(), player.getX(), player.getBoundingBox().minY, player.getZ(), player.yBodyRot
        ));
        while (history.size() > PLAYER_HISTORY_TICKS) {
            history.removeLast();
        }
    }

    public static void forgetPlayerTransform(ServerPlayer player) {
        if (player != null) {
            PLAYER_HISTORY.remove(player);
        }
    }

    public static void clearPlayerHistory() {
        PLAYER_HISTORY.clear();
    }

    public static Limb projectile(LivingEntity target, Entity projectile, Vec3 impact) {
        if (target == null || projectile == null) {
            return null;
        }
        Vec3 end = impact == null ? projectile.position() : impact;
        Vec3 start = new Vec3(projectile.xOld, projectile.yOld, projectile.zOld);
        if (start.distanceToSqr(end) < 1.0E-8D) {
            Vec3 motion = projectile.getDeltaMovement();
            start = end.subtract(motion.lengthSqr() < 1.0E-8D ? new Vec3(0.0D, 0.0D, 0.01D) : motion);
        }

        Limb intersected = firstRayIntersection(target, start, end, NORMAL_TOLERANCE, false);
        if (intersected != null) {
            return intersected;
        }

        // Damage callbacks can occur after vanilla has advanced the direct
        // entity a fraction past the contact point. Permit only a very small
        // nearest-box tolerance; this must never turn an arbitrary torso hit
        // into a client-selected decapitation.
        Limb closest = null;
        double closestDistance = Double.MAX_VALUE;
        for (Limb limb : supportedLimbs(target)) {
            double distance = distanceToBoxSqr(end, proxyBox(target, limb, 0.0D));
            double allowed = target instanceof Creeper && limb == Limb.HEAD
                    ? 0.35D
                    : PROJECTILE_FALLBACK_DISTANCE;
            if (distance <= allowed * allowed && distance < closestDistance) {
                closest = limb;
                closestDistance = distance;
            }
        }
        return closest;
    }

    /**
     * Resolves a limb from the exact movement segment which produced an
     * {@link net.minecraft.world.phys.EntityHitResult}. Unlike
     * {@link #projectile(LivingEntity, Entity, Vec3)}, this never reaches back
     * to {@code xOld/yOld/zOld}; those coordinates describe the previous tick
     * and can select a limb the accepted damage ray never crossed.
     */
    public static Limb projectileImpact(
            LivingEntity target,
            Vec3 start,
            Vec3 impact
    ) {
        ProjectileIntersection intersection = projectileIntersection(
                target,
                start,
                impact,
                0.30D
        );
        return intersection == null ? null : intersection.limb();
    }

    /**
     * Finds the first still-attached proxy intersected by a server projectile
     * sweep. The 0.3 caller tolerance mirrors ProjectileUtil's entity margin.
     */
    public static ProjectileIntersection projectileIntersection(
            LivingEntity target,
            Vec3 start,
            Vec3 end,
            double tolerance
    ) {
        if (target == null || start == null || end == null) {
            return null;
        }
        Limb closest = null;
        Vec3 closestPoint = null;
        double closestDistance = Double.MAX_VALUE;
        for (Limb limb : supportedLimbs(target)) {
            double inflation = tolerance;
            if (target instanceof Creeper && limb == Limb.HEAD) {
                inflation = Math.max(inflation, 0.3D);
            }
            Optional<Vec3> hit = proxyBox(target, limb, inflation, false).clip(start, end);
            if (hit.isPresent()) {
                double distance = start.distanceToSqr(hit.get());
                if (distance < closestDistance) {
                    closest = limb;
                    closestPoint = hit.get();
                    closestDistance = distance;
                }
            }
        }
        return closest == null ? null : new ProjectileIntersection(closest, closestPoint);
    }

    /**
     * Reconstructs attached proxy interception for the narrow region outside
     * vanilla's parent box. It only substitutes an earlier configured hit;
     * block clipping and the projectile's own can-hit predicate remain those
     * already supplied by vanilla.
     */
    public static EntityHitResult interceptProjectileProxy(
            Level level,
            Entity source,
            Vec3 start,
            Vec3 end,
            AABB searchBox,
            Predicate<Entity> canHit,
            float tolerance,
            EntityHitResult vanillaHit
    ) {
        if (level.isClientSide || !(source instanceof Projectile projectile)) {
            return vanillaHit;
        }

        double closestDistance = vanillaIntersectionDistance(
                start, end, vanillaHit, tolerance
        );
        EntityHitResult result = vanillaHit;
        boolean checkedPolicy = false;
        boolean eligibleProjectile = false;
        for (Entity entity : level.getEntities(source, searchBox, candidate ->
                candidate instanceof LivingEntity && canHit.test(candidate))) {
            LivingEntity target = (LivingEntity) entity;
            if (target.isRemoved()
                    || target.isBaby()
                    || !AmputationLogic.eligible(target)
                    || target instanceof ServerPlayer playerTarget
                    && !MobAmputationNetworking.supportsClient(playerTarget)) {
                continue;
            }
            ProjectileIntersection intersection = projectileIntersection(
                    target, start, end, tolerance
            );
            if (intersection == null) {
                continue;
            }
            double distance = start.distanceToSqr(intersection.location());
            if (distance >= closestDistance) {
                continue;
            }
            if (!checkedPolicy) {
                eligibleProjectile = ProjectileChancePolicy.evaluate(projectile, true).eligible();
                checkedPolicy = true;
            }
            if (!eligibleProjectile) {
                return vanillaHit;
            }
            closestDistance = distance;
            result = new EntityHitResult(target, intersection.location());
        }
        return result;
    }

    /**
     * Replaces the original independent client RNG with the same uniformly
     * distributed choice on both sides, so a fishing request can be verified.
     */
    public static Limb fishing(LivingEntity target, int hookEntityId) {
        List<Limb> limbs = supportedLimbs(target);
        if (limbs.isEmpty()) {
            return null;
        }
        long mixed = target.getUUID().getMostSignificantBits()
                ^ Long.rotateLeft(target.getUUID().getLeastSignificantBits(), 17)
                ^ (long) hookEntityId * 0x9E3779B97F4A7C15L;
        int hash = (int) (mixed ^ mixed >>> 32);
        return limbs.get(Math.floorMod(hash, limbs.size()));
    }

    private static Limb firstRayIntersection(
            LivingEntity target,
            Vec3 start,
            Vec3 end,
            double tolerance,
            boolean includePreviousTransform
    ) {
        Limb closest = null;
        double closestDistance = Double.MAX_VALUE;
        for (Limb limb : supportedLimbs(target)) {
            double inflation = tolerance;
            // The live Creeper keeps its full vanilla body. Its client head
            // proxy therefore deliberately exposes the same 0.3 pick halo.
            if (target instanceof Creeper && limb == Limb.HEAD) {
                inflation = Math.max(inflation, 0.3D);
            }
            Optional<Vec3> hit = proxyBox(target, limb, inflation, false).clip(start, end);
            if (includePreviousTransform && target.position().distanceToSqr(
                    target.xOld, target.yOld, target.zOld
            ) <= 4.0D) {
                Optional<Vec3> previousHit = proxyBox(target, limb, inflation, true).clip(start, end);
                if (previousHit.isPresent() && (hit.isEmpty()
                        || start.distanceToSqr(previousHit.get()) < start.distanceToSqr(hit.get()))) {
                    hit = previousHit;
                }
            }
            if (hit.isPresent()) {
                double distance = start.distanceToSqr(hit.get());
                if (distance < closestDistance) {
                    closest = limb;
                    closestDistance = distance;
                }
            }
        }
        return closest;
    }

    private static Limb firstHistoricalRayIntersection(
            LivingEntity target,
            Vec3 start,
            Vec3 end,
            double tolerance
    ) {
        if (!(target instanceof ServerPlayer player)) {
            return null;
        }
        ArrayDeque<PlayerTransform> history = PLAYER_HISTORY.get(player);
        if (history == null) {
            return null;
        }
        // Prefer the newest historical transform that intersects. Within one
        // transform, choose the closest proxy exactly as the live resolver does.
        for (PlayerTransform transform : history) {
            if (transform.level() != target.level()) {
                continue;
            }
            Limb closest = null;
            double closestDistance = Double.MAX_VALUE;
            for (Limb limb : supportedLimbs(target)) {
                Optional<Vec3> hit = proxyBox(target, limb, tolerance, transform).clip(start, end);
                if (hit.isPresent()) {
                    double distance = start.distanceToSqr(hit.get());
                    if (distance < closestDistance) {
                        closest = limb;
                        closestDistance = distance;
                    }
                }
            }
            if (closest != null) {
                return closest;
            }
        }
        return null;
    }

    private static List<Limb> supportedLimbs(LivingEntity target) {
        List<Limb> limbs = new ArrayList<>(Limb.values().length);
        int detachedMask = ServerAmputationAuthority.detachedMask(target);
        for (Limb limb : Limb.values()) {
            if (AmputationLogic.supportsLimb(target, limb) && (detachedMask & limb.bit()) == 0) {
                limbs.add(limb);
            }
        }
        return limbs;
    }

    private static AABB proxyBox(LivingEntity target, Limb limb, double inflation) {
        return proxyBox(target, limb, inflation, false);
    }

    private static AABB proxyBox(
            LivingEntity target,
            Limb limb,
            double inflation,
            boolean previousTransform
    ) {
        double x = previousTransform ? target.xOld : target.getX();
        double feetY = previousTransform ? target.yOld : target.getBoundingBox().minY;
        double z = previousTransform ? target.zOld : target.getZ();
        float bodyYaw = previousTransform ? target.yBodyRotO : target.yBodyRot;
        return proxyBox(target, limb, inflation,
                new PlayerTransform(target.level(), x, feetY, z, bodyYaw));
    }

    private static AABB proxyBox(
            LivingEntity target,
            Limb limb,
            double inflation,
            PlayerTransform transform
    ) {
        double x = transform.x();
        double y;
        double z = transform.z();
        double width;
        double height;

        if (limb == Limb.HEAD) {
            GibProfile profile = GibProfileRegistry.find(target);
            y = transform.feetY() + (profile == null ? 1.5D : profile.headHeightFromFeet());
            if (target instanceof Skeleton) {
                // The attached client parent has already been shrunk to 0.4,
                // and the gib refreshes from that post-shrink width.
                double proxyParentWidth = profile != null && profile.shrinkHumanoidParent()
                        ? 0.4D
                        : target.getBbWidth();
                width = proxyParentWidth + 0.05D;
                height = proxyParentWidth * 0.8D;
            } else {
                width = 0.5D;
                height = 0.5D;
            }
        } else {
            double offset = limb == Limb.LEFT_ARM ? 0.350D : -0.350D;
            double radians = Math.toRadians(transform.bodyYaw());
            x += offset * Math.cos(radians);
            z += offset * Math.sin(radians);
            y = transform.feetY() + 1.275D;
            width = 0.2D;
            height = 0.25D;
        }

        return new AABB(
                x - width / 2.0D,
                y,
                z - width / 2.0D,
                x + width / 2.0D,
                y + height,
                z + width / 2.0D
        ).inflate(inflation);
    }

    private static double distanceToBoxSqr(Vec3 point, AABB box) {
        double dx = point.x < box.minX ? box.minX - point.x
                : point.x > box.maxX ? point.x - box.maxX : 0.0D;
        double dy = point.y < box.minY ? box.minY - point.y
                : point.y > box.maxY ? point.y - box.maxY : 0.0D;
        double dz = point.z < box.minZ ? box.minZ - point.z
                : point.z > box.maxZ ? point.z - box.maxZ : 0.0D;
        return dx * dx + dy * dy + dz * dz;
    }

    private static double vanillaIntersectionDistance(
            Vec3 start,
            Vec3 end,
            EntityHitResult hit,
            float tolerance
    ) {
        if (hit == null) {
            return Double.MAX_VALUE;
        }
        Optional<Vec3> intersection = hit.getEntity().getBoundingBox()
                .inflate(tolerance)
                .clip(start, end);
        return intersection.map(start::distanceToSqr)
                .orElseGet(() -> start.distanceToSqr(hit.getLocation()));
    }

    public record ProjectileIntersection(Limb limb, Vec3 location) {
    }

    private record PlayerTransform(Level level, double x, double feetY, double z, float bodyYaw) {
    }

    private LimbHitResolver() {
    }
}
