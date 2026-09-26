package io.github.avacadowizard120.mobamputation.logic;

import io.github.avacadowizard120.mobamputation.api.Limb;
import io.github.avacadowizard120.mobamputation.config.MobAmputationConfig;
import io.github.avacadowizard120.mobamputation.network.DetachCause;
import io.github.avacadowizard120.mobamputation.network.DetachRequestPayload;
import io.github.avacadowizard120.mobamputation.network.MobAmputationNetworking;
import io.github.avacadowizard120.mobamputation.state.PlayerAmputationAccess;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;

/** Matches sever candidates to real server-observed interactions and rolls once. */
public final class ServerAmputationAuthority {
    private static final int EVIDENCE_TTL_TICKS = 40;
    private static final int MAX_EVIDENCE = 256;
    private static final int MAX_PENDING = 128;
    private static final int MAX_REQUESTS_PER_SECOND = 24;

    private static final Deque<Evidence> evidence = new ArrayDeque<>();
    private static final Deque<ConsumedSource> consumedSources = new ArrayDeque<>();
    private static final Deque<PendingRequest> pending = new ArrayDeque<>();
    private static final Map<LivingEntity, EnumSet<Limb>> detached = new WeakHashMap<>();
    private static final Map<ServerPlayer, RateWindow> rates = new WeakHashMap<>();
    private static final ThreadLocal<Deque<ProjectileImpact>> activeProjectileImpacts =
            ThreadLocal.withInitial(ArrayDeque::new);
    private static Consumer<Resolution> automaticResolutionListener = ignored -> {
    };

    /** Installs the loader-neutral notification seam for server-owned hits. */
    public static void setAutomaticResolutionListener(Consumer<Resolution> listener) {
        automaticResolutionListener = listener == null ? ignored -> {
        } : listener;
    }

    public static void recordMelee(ServerPlayer player, LivingEntity target, ItemStack weapon) {
        recordMelee(
                player,
                target,
                weapon,
                target instanceof Player playerTarget ? ArmorProtectionPolicy.snapshot(playerTarget) : null,
                LimbHitResolver.melee(player, target)
        );
    }

    /** Records a successful melee hit using armor captured before damage and durability changes. */
    public static void recordMelee(
            ServerPlayer player,
            LivingEntity target,
            ItemStack weapon,
            ArmorProtectionPolicy.ArmorSnapshot armorSnapshot
    ) {
        recordMelee(player, target, weapon, armorSnapshot, LimbHitResolver.melee(player, target));
    }

    /** Records both pre-hit armor and the server-observed proxy intersection. */
    public static void recordMelee(
            ServerPlayer player,
            LivingEntity target,
            ItemStack weapon,
            ArmorProtectionPolicy.ArmorSnapshot armorSnapshot,
            Limb hitLimb
    ) {
        record(new Evidence(
                target,
                hitLimb,
                DetachCause.MELEE,
                target.getId(),
                player,
                target.level().getGameTime() + EVIDENCE_TTL_TICKS,
                weapon.copy(),
                null,
                false,
                false,
                armorSnapshot
        ));
    }

    /**
     * Opens a scoped impact before Projectile invokes onHitEntity. Damage
     * callbacks inside that invocation can therefore use the exact
     * EntityHitResult segment instead of the projectile's previous-tick
     * coordinates or a position mutated by the hit implementation.
     */
    public static void beginProjectileImpact(Projectile projectile, EntityHitResult hit) {
        if (projectile.level().isClientSide || !(hit.getEntity() instanceof LivingEntity target)) {
            return;
        }
        activeProjectileImpacts.get().addLast(new ProjectileImpact(
                projectile,
                target,
                hit.getLocation(),
                currentSegmentLimb(projectile, target, hit.getLocation())
        ));
    }

    /** Completes the scoped impact and records collision-only projectile proof. */
    public static void endProjectileImpact(
            Projectile projectile,
            EntityHitResult hit,
            boolean collisionAccepted
    ) {
        if (projectile.level().isClientSide || !(hit.getEntity() instanceof LivingEntity target)) {
            return;
        }
        ProjectileImpact impact = removeActiveProjectileImpact(projectile, target);
        if (!collisionAccepted) {
            return;
        }
        if (impact == null) {
            impact = new ProjectileImpact(
                    projectile,
                    target,
                    hit.getLocation(),
                    currentSegmentLimb(projectile, target, hit.getLocation())
            );
        }
        Entity owner = projectile.getOwner();
        // Accepted damage is recorded synchronously from LivingEntity.hurt
        // before this RETURN injection. It carries the pre-hit armor snapshot
        // and must win over collision-only proof for every player target.
        if (target instanceof Player && hasProjectileEvidence(target, projectile.getId(), true)) {
            return;
        }
        submitProjectile(new Evidence(
                target,
                impact.hitLimb(),
                DetachCause.PROJECTILE,
                projectile.getId(),
                owner,
                target.level().getGameTime() + EVIDENCE_TTL_TICKS,
                ItemStack.EMPTY,
                projectile,
                true,
                false,
                target instanceof Player playerTarget ? ArmorProtectionPolicy.snapshot(playerTarget) : null
        ));
    }

    /** Returns the exact active impact, including a proven no-limb result. */
    public static ProjectileImpact currentProjectileImpact(LivingEntity target, Entity direct) {
        if (target == null || direct == null) {
            return null;
        }
        Iterator<ProjectileImpact> iterator = activeProjectileImpacts.get().descendingIterator();
        while (iterator.hasNext()) {
            ProjectileImpact impact = iterator.next();
            if (impact.projectile() == direct && impact.target() == target) {
                return impact;
            }
        }
        return null;
    }

    public static void recordFishing(FishingHook hook) {
        if (hook.level().isClientSide
                || !(hook.getHookedIn() instanceof LivingEntity target)
                || !(hook.getPlayerOwner() instanceof ServerPlayer owner)) {
            return;
        }
        record(new Evidence(
                target,
                LimbHitResolver.fishing(target, hook.getId()),
                DetachCause.FISHING,
                hook.getId(),
                owner,
                target.level().getGameTime() + EVIDENCE_TTL_TICKS,
                ItemStack.EMPTY,
                null,
                false,
                false,
                target instanceof Player playerTarget ? ArmorProtectionPolicy.snapshot(playerTarget) : null
        ));
    }

    /** Covers modded projectile damage whose direct entity is not Projectile. */
    public static void recordProjectileDamage(LivingEntity target, DamageSource source, boolean accepted) {
        recordProjectileDamage(
                target,
                source,
                accepted,
                target instanceof Player playerTarget ? ArmorProtectionPolicy.snapshot(playerTarget) : null,
                LimbHitResolver.projectile(target, source.getDirectEntity(),
                        source.getDirectEntity() == null ? null : source.getDirectEntity().position())
        );
    }

    /** Records accepted projectile damage using armor captured at LivingEntity.hurt HEAD. */
    public static void recordProjectileDamage(
            LivingEntity target,
            DamageSource source,
            boolean accepted,
            ArmorProtectionPolicy.ArmorSnapshot armorSnapshot
    ) {
        Entity direct = source.getDirectEntity();
        recordProjectileDamage(
                target,
                source,
                accepted,
                armorSnapshot,
                LimbHitResolver.projectile(target, direct, direct == null ? null : direct.position())
        );
    }

    /** Records accepted projectile damage and the impact limb captured at hurt HEAD. */
    public static void recordProjectileDamage(
            LivingEntity target,
            DamageSource source,
            boolean accepted,
            ArmorProtectionPolicy.ArmorSnapshot armorSnapshot,
            Limb hitLimb
    ) {
        Entity direct = source.getDirectEntity();
        if (!accepted
                || target.level().isClientSide
                || direct == null
                || !source.is(DamageTypeTags.IS_PROJECTILE)) {
            return;
        }
        Entity responsible = source.getEntity();
        if (!(responsible instanceof Player)
                && direct instanceof Projectile projectile
                && projectile.getOwner() instanceof Player playerOwner) {
            // A few modded sources expose an intermediary entity through
            // DamageSource#getEntity even though the projectile is genuinely
            // player-owned. Bind those to the shooter, never to an observer.
            responsible = playerOwner;
        } else if (responsible == null && direct instanceof Projectile fallbackProjectile) {
            responsible = fallbackProjectile.getOwner();
        }
        submitProjectile(new Evidence(
                target,
                hitLimb,
                DetachCause.PROJECTILE,
                direct.getId(),
                responsible,
                target.level().getGameTime() + EVIDENCE_TTL_TICKS,
                ItemStack.EMPTY,
                direct,
                true,
                true,
                armorSnapshot
        ));
    }

    /** Validates one candidate. The callback also reports an authoritative miss. */
    public static void handle(
            ServerPlayer sender,
            DetachRequestPayload request,
            Consumer<Resolution> callback
    ) {
        if (sender == null || !allowRequest(sender)) {
            return;
        }
        DetachCause cause = DetachCause.fromNetwork(request.cause());
        if (cause == null || request.limb() < 0 || request.limb() >= Limb.values().length) {
            return;
        }
        Entity resolved = sender.level().getEntity(request.parentId());
        if (!(resolved instanceof LivingEntity target)
                || target.isRemoved()
                || target.isBaby()
                || !AmputationLogic.eligible(target)) {
            return;
        }

        Limb limb = Limb.values()[request.limb()];
        if (target instanceof ServerPlayer playerTarget
                && !MobAmputationNetworking.supportsClient(playerTarget)) {
            callback.accept(new Resolution(target, limb, cause, false));
            return;
        }
        if (!AmputationLogic.supportsLimb(target, limb)) {
            callback.accept(new Resolution(target, limb, cause, false));
            return;
        }
        if (target instanceof Player
                && ((PlayerAmputationAccess) target).mobamputation$isDetached(limb)) {
            callback.accept(new Resolution(target, limb, cause, false));
            return;
        }
        EnumSet<Limb> targetLimbs = detached.computeIfAbsent(target, ignored -> EnumSet.noneOf(Limb.class));
        if (targetLimbs.contains(limb)) {
            callback.accept(new Resolution(target, limb, cause, false));
            return;
        }
        if (cause != DetachCause.MELEE && sourceWasConsumed(target, cause, request.sourceEntityId())) {
            callback.accept(new Resolution(target, limb, cause, false));
            return;
        }

        Evidence match = consumeEvidence(sender, target, cause, request.sourceEntityId());
        if (match == null) {
            queuePending(sender, request.requestId(), target, limb, cause, request.sourceEntityId(), callback);
            return;
        }
        resolve(match, target, limb, cause, callback);
    }

    public static void tick() {
        pruneExpiredEvidence();
        Iterator<PendingRequest> iterator = pending.iterator();
        while (iterator.hasNext()) {
            PendingRequest request = iterator.next();
            long gameTime = request.target().level().getGameTime();
            if (request.sender().isRemoved()
                    || request.target().isRemoved()
                    || request.target().level() != request.sender().level()
                    || gameTime > request.expiresAt()) {
                iterator.remove();
                request.callback().accept(new Resolution(
                        request.target(), request.limb(), request.cause(), false
                ));
                continue;
            }

            Evidence match = consumeEvidenceWithoutPrune(
                    request.sender(), request.target(), request.cause(), request.sourceEntityId()
            );
            if (match != null) {
                iterator.remove();
                resolve(match, request.target(), request.limb(), request.cause(), request.callback());
            }
        }

        // A remote Player keeps its full vanilla body box, which occludes the
        // client-only head/arm proxies from ordinary front-facing PvP clicks.
        // Give a genuine proxy request the current tick to consume its evidence
        // first; then resolve an unclaimed, accepted Player melee hit directly
        // from the server-proven limb ray. This performs exactly one roll.
        Iterator<Evidence> meleeIterator = evidence.iterator();
        while (meleeIterator.hasNext()) {
            Evidence candidate = meleeIterator.next();
            if (candidate.cause() != DetachCause.MELEE
                    || !(candidate.target() instanceof ServerPlayer target)
                    || target.level().getGameTime() < candidate.expiresAt() - EVIDENCE_TTL_TICKS + 1L) {
                continue;
            }
            meleeIterator.remove();
            Limb limb = candidate.hitLimb();
            if (limb == null
                    || target.isRemoved()
                    || target.isBaby()
                    || !MobAmputationNetworking.supportsClient(target)
                    || !AmputationLogic.supportsLimb(target, limb)
                    || isDetached(target, limb)) {
                continue;
            }
            resolve(candidate, target, limb, DetachCause.MELEE, automaticResolutionListener);
        }
    }

    public static void clear() {
        evidence.clear();
        consumedSources.clear();
        pending.clear();
        detached.clear();
        rates.clear();
        activeProjectileImpacts.remove();
        LimbHitResolver.clearPlayerHistory();
    }

    public static int detachedMask(LivingEntity entity) {
        int mask = entity instanceof Player
                ? ((PlayerAmputationAccess) entity).mobamputation$getLimbMask()
                : 0;
        EnumSet<Limb> limbs = detached.get(entity);
        if (limbs != null) {
            for (Limb limb : limbs) {
                mask |= limb.bit();
            }
        }
        return mask;
    }

    /**
     * Forgets every server-authority record that could reassert an old wound.
     * Persistent player trauma is cleared by PlayerTraumaManager; this method
     * owns the separate replay-protection cache and unresolved hit evidence.
     */
    public static void clearDetachedState(LivingEntity entity) {
        detached.remove(entity);
        evidence.removeIf(entry -> entry.target() == entity);
        pending.removeIf(request -> request.target() == entity);
    }

    private static void resolve(
            Evidence match,
            LivingEntity target,
            Limb limb,
            DetachCause cause,
            Consumer<Resolution> callback
    ) {
        if (target instanceof ServerPlayer playerTarget
                && !MobAmputationNetworking.supportsClient(playerTarget)) {
            callback.accept(new Resolution(target, limb, cause, false));
            return;
        }
        // The server observed the real ray/impact before this request arrived.
        // Never let a modified client upgrade a legitimate torso/arm hit into
        // an arbitrary (and potentially fatal) head amputation.
        if (match.hitLimb() == null || match.hitLimb() != limb) {
            callback.accept(new Resolution(target, limb, cause, false));
            return;
        }
        boolean succeeds = switch (cause) {
            case MELEE -> rollMelee(target, limb, match);
            case PROJECTILE -> rollProjectile(target, limb, match);
            case FISHING -> rollInclusive(
                    target,
                    protectedChance(
                            target,
                            limb,
                            match,
                            MobAmputationConfig.get().fishingChance()
                    )
            );
        };
        if (!succeeds) {
            callback.accept(new Resolution(target, limb, cause, false));
            return;
        }

        EnumSet<Limb> targetLimbs = detached.computeIfAbsent(target, ignored -> EnumSet.noneOf(Limb.class));
        if (!targetLimbs.add(limb)) {
            callback.accept(new Resolution(target, limb, cause, false));
            return;
        }
        AmputationLogic.finishAmputation(target, limb, match.responsibleEntity());
        callback.accept(new Resolution(target, limb, cause, true));
    }

    private static boolean rollMelee(LivingEntity target, Limb limb, Evidence match) {
        int baseChance = MeleeChancePolicy.baseChance(match.weapon());
        int bonus = MeleeChancePolicy.enchantmentBonus(limb, match.weapon());
        if (target instanceof Player
                && ArmorProtectionPolicy.isImmune(match.armorSnapshot(), limb)) {
            return false;
        }
        // Offensive enchantments are applied after ordinary armor reduction.
        // They therefore remain useful in armored PvP while Anatomical
        // Integrity above still provides the configured absolute immunity.
        int chance = Math.min(100, protectedChance(target, limb, match, baseChance) + bonus);
        if (chance <= 0) {
            return false;
        }
        return chance >= 100 || target.getRandom().nextFloat() < chance / 100.0F;
    }

    private static boolean rollProjectile(LivingEntity target, Limb limb, Evidence match) {
        if (match.projectileSource() == null) {
            return false;
        }
        ProjectileChancePolicy.Decision decision = ProjectileChancePolicy.evaluate(
                match.projectileSource(), match.genericProjectile()
        );
        return decision.eligible()
                && rollInclusive(target, protectedChance(target, limb, match, decision.percentage()));
    }

    private static int protectedChance(LivingEntity target, Limb limb, Evidence match, int chance) {
        return target instanceof Player
                ? ArmorProtectionPolicy.protectedChance(match.armorSnapshot(), limb, chance)
                : chance;
    }

    private static boolean rollInclusive(LivingEntity target, int chance) {
        if (chance <= 0) {
            return false;
        }
        if (chance >= 100) {
            return true;
        }
        return target.getRandom().nextFloat() <= chance / 100.0F;
    }

    private static void submitProjectile(Evidence candidate) {
        pruneExpiredEvidence();
        if (sourceWasConsumed(candidate)) {
            return;
        }

        // A collision is not proof that a player took damage: shields,
        // invulnerability and other mods may reject the hit. NPCs retain the
        // original proxy-collision behavior, including harmless snowballs.
        if (candidate.target() instanceof Player && !candidate.acceptedDamageEvidence()) {
            return;
        }

        if (!(candidate.responsibleEntity() instanceof Player)) {
            resolveAutomaticProjectile(candidate);
        } else {
            record(candidate);
        }
    }

    private static void resolveAutomaticProjectile(Evidence candidate) {
        markSourceConsumed(candidate);
        evidence.removeIf(existing -> sameSource(existing, candidate));

        // Requests for mob/environment-owned sources are never authority.
        // Resolve them as misses now rather than letting a nearby observer
        // race the server-owned roll or sit pending for two seconds.
        Iterator<PendingRequest> pendingIterator = pending.iterator();
        while (pendingIterator.hasNext()) {
            PendingRequest request = pendingIterator.next();
            if (request.target() == candidate.target()
                    && request.cause() == candidate.cause()
                    && request.sourceEntityId() == candidate.sourceEntityId()) {
                pendingIterator.remove();
                request.callback().accept(new Resolution(
                        request.target(), request.limb(), request.cause(), false
                ));
            }
        }

        LivingEntity target = candidate.target();
        Limb limb = candidate.hitLimb();
        if (limb == null
                || target.isRemoved()
                || target.isBaby()
                || !AmputationLogic.supportsLimb(target, limb)
                || isDetached(target, limb)
                || target instanceof ServerPlayer playerTarget
                && !MobAmputationNetworking.supportsClient(playerTarget)) {
            return;
        }
        resolve(candidate, target, limb, DetachCause.PROJECTILE, automaticResolutionListener);
    }

    private static ProjectileImpact removeActiveProjectileImpact(
            Projectile projectile,
            LivingEntity target
    ) {
        Deque<ProjectileImpact> impacts = activeProjectileImpacts.get();
        Iterator<ProjectileImpact> iterator = impacts.descendingIterator();
        ProjectileImpact result = null;
        while (iterator.hasNext()) {
            ProjectileImpact candidate = iterator.next();
            if (candidate.projectile() == projectile && candidate.target() == target) {
                iterator.remove();
                result = candidate;
                break;
            }
        }
        if (impacts.isEmpty()) {
            activeProjectileImpacts.remove();
        }
        return result;
    }

    private static Limb currentSegmentLimb(
            Projectile projectile,
            LivingEntity target,
            net.minecraft.world.phys.Vec3 reportedImpact
    ) {
        net.minecraft.world.phys.Vec3 start = projectile.position();
        net.minecraft.world.phys.Vec3 motion = projectile.getDeltaMovement();
        net.minecraft.world.phys.Vec3 end = motion.lengthSqr() < 1.0E-8D
                ? reportedImpact
                : start.add(motion);
        if (motion.lengthSqr() >= 1.0E-8D) {
            double progress = reportedImpact.subtract(start).dot(motion) / motion.lengthSqr();
            if (progress >= -1.0E-6D && progress <= 1.000001D
                    && start.add(motion.scale(progress)).distanceToSqr(reportedImpact) <= 1.0E-8D) {
                // Proxy interception preserves its exact clipped impact point;
                // vanilla's entity-only result usually reports entity.position
                // and therefore fails this collinearity test.
                end = reportedImpact;
            }
        }
        return LimbHitResolver.projectileImpact(target, start, end);
    }

    private static boolean isDetached(LivingEntity target, Limb limb) {
        if (target instanceof Player
                && ((PlayerAmputationAccess) target).mobamputation$isDetached(limb)) {
            return true;
        }
        EnumSet<Limb> targetLimbs = detached.get(target);
        return targetLimbs != null && targetLimbs.contains(limb);
    }

    private static Evidence consumeEvidence(
            ServerPlayer sender,
            LivingEntity target,
            DetachCause cause,
            int sourceEntityId
    ) {
        pruneExpiredEvidence();
        return consumeEvidenceWithoutPrune(sender, target, cause, sourceEntityId);
    }

    private static Evidence consumeEvidenceWithoutPrune(
            ServerPlayer sender,
            LivingEntity target,
            DetachCause cause,
            int sourceEntityId
    ) {
        Iterator<Evidence> iterator = evidence.iterator();
        while (iterator.hasNext()) {
            Evidence candidate = iterator.next();
            if (candidate.target() != target
                    || candidate.cause() != cause
                    || candidate.sourceEntityId() != sourceEntityId
                    || candidate.target().level() != sender.level()) {
                continue;
            }
            Player responsible = candidate.responsibleEntity() instanceof Player player ? player : null;
            if (cause == DetachCause.PROJECTILE && responsible != sender
                    || cause != DetachCause.PROJECTILE && responsible != null && responsible != sender
                    || cause != DetachCause.PROJECTILE && responsible == null
                    && sender.distanceToSqr(target) > 4096.0D) {
                continue;
            }
            iterator.remove();
            if (cause != DetachCause.MELEE) {
                consumedSources.addLast(new ConsumedSource(
                        candidate.target(), candidate.cause(), candidate.sourceEntityId(), candidate.expiresAt()
                ));
                while (consumedSources.size() > MAX_EVIDENCE) {
                    consumedSources.removeFirst();
                }
            }
            return candidate;
        }
        return null;
    }

    private static boolean allowRequest(ServerPlayer sender) {
        long second = sender.serverLevel().getGameTime() / 20L;
        RateWindow window = rates.get(sender);
        if (window == null || window.second() != second) {
            rates.put(sender, new RateWindow(second, 1));
            return true;
        }
        if (window.count() >= MAX_REQUESTS_PER_SECOND) {
            return false;
        }
        rates.put(sender, new RateWindow(second, window.count() + 1));
        return true;
    }

    private static void record(Evidence candidate) {
        pruneExpiredEvidence();
        if (candidate.cause() != DetachCause.MELEE) {
            if (sourceWasConsumed(candidate)) {
                return;
            }
            // For players, a collision alone is insufficient: armor, shields,
            // invulnerability and other mods may reject the actual damage.
            // Only LivingEntityDamageMixin's accepted hit may authorize PvP
            // trauma. Non-player behavior retains the original collision path.
            if (candidate.target() instanceof Player
                    && candidate.cause() == DetachCause.PROJECTILE
                    && !candidate.acceptedDamageEvidence()) {
                return;
            }
            // A vanilla projectile may produce collision plus one or more
            // damage callbacks. Keep the first accepted-damage proof because
            // it owns the true pre-hit armor snapshot; only upgrade an older
            // collision-only proof. Melee evidence remains independently
            // queued for simultaneous attackers.
            Evidence duplicate = evidence.stream()
                    .filter(existing -> sameSource(existing, candidate))
                    .findFirst()
                    .orElse(null);
            if (duplicate != null) {
                if (duplicate.acceptedDamageEvidence()
                        || !candidate.acceptedDamageEvidence()) {
                    return;
                }
                evidence.remove(duplicate);
            }
        }
        evidence.addLast(candidate);
        while (evidence.size() > MAX_EVIDENCE) {
            evidence.removeFirst();
        }
    }

    private static boolean sourceWasConsumed(Evidence candidate) {
        return consumedSources.stream().anyMatch(existing -> sameSource(existing, candidate));
    }

    private static boolean sourceWasConsumed(
            LivingEntity target,
            DetachCause cause,
            int sourceEntityId
    ) {
        return consumedSources.stream().anyMatch(existing -> existing.target() == target
                && existing.cause() == cause
                && existing.sourceEntityId() == sourceEntityId);
    }

    private static boolean hasProjectileEvidence(
            LivingEntity target,
            int sourceEntityId,
            boolean acceptedDamage
    ) {
        return evidence.stream().anyMatch(existing -> existing.target() == target
                && existing.cause() == DetachCause.PROJECTILE
                && existing.sourceEntityId() == sourceEntityId
                && (!acceptedDamage || existing.acceptedDamageEvidence()));
    }

    private static boolean sameSource(ConsumedSource existing, Evidence candidate) {
        return existing.target() == candidate.target()
                && existing.cause() == candidate.cause()
                && existing.sourceEntityId() == candidate.sourceEntityId();
    }

    private static boolean sameSource(Evidence existing, Evidence candidate) {
        return existing.target() == candidate.target()
                && existing.cause() == candidate.cause()
                && existing.sourceEntityId() == candidate.sourceEntityId();
    }

    private static void markSourceConsumed(Evidence candidate) {
        consumedSources.addLast(new ConsumedSource(
                candidate.target(), candidate.cause(), candidate.sourceEntityId(), candidate.expiresAt()
        ));
        while (consumedSources.size() > MAX_EVIDENCE) {
            consumedSources.removeFirst();
        }
    }

    private static void pruneExpiredEvidence() {
        evidence.removeIf(entry -> entry.target().isRemoved()
                || entry.target().level().getGameTime() > entry.expiresAt());
        consumedSources.removeIf(entry -> entry.target().isRemoved()
                || entry.target().level().getGameTime() > entry.expiresAt());
    }

    private static void queuePending(
            ServerPlayer sender,
            int requestId,
            LivingEntity target,
            Limb limb,
            DetachCause cause,
            int sourceEntityId,
            Consumer<Resolution> callback
    ) {
        pending.removeIf(existing -> existing.sender() == sender && existing.requestId() == requestId);
        pending.addLast(new PendingRequest(
                sender,
                requestId,
                target,
                limb,
                cause,
                sourceEntityId,
                target.level().getGameTime() + EVIDENCE_TTL_TICKS,
                callback
        ));
        while (pending.size() > MAX_PENDING) {
            PendingRequest evicted = pending.removeFirst();
            evicted.callback().accept(new Resolution(
                    evicted.target(), evicted.limb(), evicted.cause(), false
            ));
        }
    }

    public record Resolution(LivingEntity target, Limb limb, DetachCause cause, boolean accepted) {
    }

    public record ProjectileImpact(
            Projectile projectile,
            LivingEntity target,
            net.minecraft.world.phys.Vec3 location,
            Limb hitLimb
    ) {
    }

    private record Evidence(
            LivingEntity target,
            Limb hitLimb,
            DetachCause cause,
            int sourceEntityId,
            Entity responsibleEntity,
            long expiresAt,
            ItemStack weapon,
            Entity projectileSource,
            boolean genericProjectile,
            boolean acceptedDamageEvidence,
            ArmorProtectionPolicy.ArmorSnapshot armorSnapshot
    ) {
    }

    private record ConsumedSource(
            LivingEntity target,
            DetachCause cause,
            int sourceEntityId,
            long expiresAt
    ) {
    }

    private record PendingRequest(
            ServerPlayer sender,
            int requestId,
            LivingEntity target,
            Limb limb,
            DetachCause cause,
            int sourceEntityId,
            long expiresAt,
            Consumer<Resolution> callback
    ) {
    }

    private record RateWindow(long second, int count) {
    }

    private ServerAmputationAuthority() {
    }
}
