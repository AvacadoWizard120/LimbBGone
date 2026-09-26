package io.github.avacadowizard120.mobamputation.client.dismemberment;

import io.github.avacadowizard120.mobamputation.api.Limb;
import io.github.avacadowizard120.mobamputation.api.BloodProperties;
import io.github.avacadowizard120.mobamputation.client.BloodParticle;
import io.github.avacadowizard120.mobamputation.client.GibManager;
import io.github.avacadowizard120.mobamputation.config.MobAmputationConfig;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.vehicle.MinecartTNT;
import net.minecraft.world.phys.Vec3;

/** Owns delayed death detection and the client-local dismemberment entities. */
public final class DeathDismembermentManager {
    private static final Map<LivingEntity, PendingDeath> PENDING = new IdentityHashMap<>();
    private static final Set<LivingEntity> PROCESSED = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final ArrayList<DeathGibEntity> ACTIVE_GIBS = new ArrayList<>();
    private static ClientLevel activeLevel;

    /** Called from the client LivingEntity tick tail. Safe to call repeatedly. */
    public static void observe(LivingEntity entity) {
        if (!entity.level().isClientSide || entity.isAlive() || entity.isBaby()) {
            return;
        }
        TriggerContext context = contextForDeath(entity);
        if (context != null) {
            queue(entity, context);
        }
    }

    /** Captures a creeper's source position before vanilla removes it. */
    public static void observeCreeperExplosion(Creeper creeper) {
        if (!creeper.level().isClientSide || creeper.isBaby()) {
            return;
        }
        DeathDismembermentSettings.TriggerMode mode = DeathDismembermentSettings.triggerMode();
        if (mode == DeathDismembermentSettings.TriggerMode.DISABLED
                || mode == DeathDismembermentSettings.TriggerMode.TRIGGERED
                && !MobAmputationConfig.get().deathDismemberment().explosions()) {
            return;
        }
        queue(creeper, new TriggerContext(true, creeper.position(), creeper.isPowered() ? 6.0D : 3.0D));
    }

    /** Runs at client tick END; the countdown advances only while gameplay does. */
    public static void clientTick(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        if (level == null) {
            if (activeLevel != null) {
                clear();
            }
            return;
        }
        ensureLevel(level);
        ACTIVE_GIBS.removeIf(Entity::isRemoved);
        // The renderer only needs a processed parent until vanilla removes
        // that corpse. Keeping every former mob here for the whole session
        // would retain worlds' worth of dead entities on long-running clients.
        PROCESSED.removeIf(Entity::isRemoved);

        if (DeathDismembermentSettings.triggerMode()
                == DeathDismembermentSettings.TriggerMode.DISABLED) {
            PENDING.clear();
            return;
        }
        if (minecraft.isPaused()) {
            return;
        }

        // The original handler deliberately advances at most one queued
        // corpse per client tick. Besides matching its staggered feel, this
        // prevents an explosion mob farm from creating every body part and
        // thousands of particles in one frame.
        Iterator<Map.Entry<LivingEntity, PendingDeath>> iterator = PENDING.entrySet().iterator();
        if (iterator.hasNext()) {
            Map.Entry<LivingEntity, PendingDeath> entry = iterator.next();
            LivingEntity parent = entry.getKey();
            PendingDeath pending = entry.getValue();

            // Upstream keeps the corpse upright during its two-tick queue.
            parent.hurtTime = 0;
            parent.deathTime = 0;
            if (--pending.ticksRemaining > 0) {
                return;
            }

            iterator.remove();
            if (!parent.isBaby() && isSupported(parent)) {
                spawn(level, parent, pending.context);
            }
        }
    }

    public static boolean shouldSuppressParent(LivingEntity entity) {
        return PROCESSED.contains(entity);
    }

    public static void clear() {
        for (DeathGibEntity gib : ACTIVE_GIBS) {
            if (!gib.isRemoved()) {
                gib.discard();
            }
        }
        ACTIVE_GIBS.clear();
        PENDING.clear();
        PROCESSED.clear();
        activeLevel = null;
    }

    private static void ensureLevel(ClientLevel level) {
        if (activeLevel != level) {
            clear();
            activeLevel = level;
        }
    }

    private static void queue(LivingEntity entity, TriggerContext context) {
        if (entity.level() instanceof ClientLevel level) {
            ensureLevel(level);
        }
        if (!isSupported(entity) || PROCESSED.contains(entity)) {
            return;
        }
        PendingDeath existing = PENDING.get(entity);
        if (existing == null) {
            PENDING.put(entity, new PendingDeath(context));
        } else if (context.explosion && !existing.context.explosion) {
            // Preserve the countdown while upgrading a generic death notice
            // with the more precise explosion context captured later.
            existing.context = context;
        }
    }

    private static TriggerContext contextForDeath(LivingEntity entity) {
        DeathDismembermentSettings.TriggerMode mode = DeathDismembermentSettings.triggerMode();
        if (mode == DeathDismembermentSettings.TriggerMode.DISABLED) {
            return null;
        }

        DamageSource source = entity.getLastDamageSource();
        boolean explosion = source != null && source.is(DamageTypeTags.IS_EXPLOSION);
        Entity direct = source == null ? null : source.getDirectEntity();
        Entity causing = source == null ? null : source.getEntity();
        boolean ironGolem = direct instanceof IronGolem || causing instanceof IronGolem;
        var settings = MobAmputationConfig.get().deathDismemberment();
        boolean allowedExplosion = explosion && settings.explosions();
        boolean allowedIronGolem = ironGolem && settings.ironGolems();
        if (mode == DeathDismembermentSettings.TriggerMode.TRIGGERED
                && !allowedExplosion && !allowedIronGolem) {
            return null;
        }

        if (!allowedExplosion) {
            return TriggerContext.NORMAL;
        }
        Entity explosive = direct != null ? direct : causing;
        Vec3 origin = source.getSourcePosition();
        if (origin == null) {
            origin = explosive != null ? explosive.position() : entity.position();
        }
        return new TriggerContext(true, origin, explosionMagnitude(explosive));
    }

    private static double explosionMagnitude(Entity source) {
        if (source instanceof Creeper creeper) {
            return creeper.isPowered() ? 6.0D : 3.0D;
        }
        if (source instanceof PrimedTnt || source instanceof MinecartTNT) {
            return 4.0D;
        }
        // EntityGib starts at 1.0 before its explicit TNT/creeper branches.
        return 1.0D;
    }

    private static boolean isSupported(LivingEntity entity) {
        EntityType<?> type = entity.getType();
        return type == EntityType.ZOMBIE || type == EntityType.SKELETON || type == EntityType.CREEPER;
    }

    private static void spawn(ClientLevel level, LivingEntity parent, TriggerContext context) {
        EnumSet<Limb> alreadyDetached = EnumSet.noneOf(Limb.class);
        for (Limb limb : Limb.values()) {
            if (GibManager.isDetached(parent, limb)) {
                alreadyDetached.add(limb);
            }
        }

        // Attached proxies are obsolete once the corpse splits. Detached
        // proxies stay alive; their corresponding death parts are skipped.
        GibManager.releaseForDeath(parent);
        PROCESSED.add(parent);

        for (DeathGibPart part : DeathGibPart.forEntity(parent)) {
            if (part == DeathGibPart.HEAD && alreadyDetached.contains(Limb.HEAD)
                    || part == DeathGibPart.LEFT_ARM && alreadyDetached.contains(Limb.LEFT_ARM)
                    || part == DeathGibPart.RIGHT_ARM && alreadyDetached.contains(Limb.RIGHT_ARM)) {
                continue;
            }

            DeathGibEntity gib = new DeathGibEntity(level, parent, part);
            if (context.explosion) {
                gib.launchFromExplosion(context.origin, context.magnitude);
            }
            gib.setId(GibManager.allocateLocalEntityId(level));
            ACTIVE_GIBS.add(gib);
            level.addEntity(gib);
        }

        if (parent instanceof Zombie && DeathDismembermentSettings.zombieBlood()) {
            spawnZombieBlood(level, parent, context.explosion);
        }
    }

    private static void spawnZombieBlood(ClientLevel level, LivingEntity parent, boolean explosion) {
        int count = DeathDismembermentSettings.zombieBloodCount() * (explosion ? 10 : 1);
        for (int index = 0; index < count; index++) {
            float speed = 0.3F;
            float yaw = parent.getYRot() / 180.0F * (float) Math.PI;
            float pitch = parent.getXRot() / 180.0F * (float) Math.PI;
            double extraX = -Mth.sin(yaw) * Mth.cos(pitch) * speed;
            double extraZ = Mth.cos(yaw) * Mth.cos(pitch) * speed;
            double extraY = -Mth.sin(pitch) * speed + 0.1F;

            float angle = parent.getRandom().nextFloat() * (float) Math.PI * 2.0F;
            float scatter = 0.02F * parent.getRandom().nextFloat();
            if (explosion) {
                scatter *= 100.0F;
            }
            extraX += Math.cos(angle) * scatter;
            extraY += (parent.getRandom().nextFloat() - parent.getRandom().nextFloat()) * 0.1F;
            extraZ += Math.sin(angle) * scatter;

            Vec3 motion = parent.getDeltaMovement();
            BloodParticle.spawn(
                    level,
                    parent.getX(),
                    parent.getY() + 0.5D + parent.getRandom().nextDouble() * 0.7D,
                    parent.getZ(),
                    motion.x + extraX,
                    motion.y + extraY,
                    motion.z + extraZ,
                    parent,
                    BloodProperties.original(
                            1.0F,
                            DeathDismembermentSettings.greenBlood() ? 1.0F : 0.0F,
                            0.0F
                    )
            );
        }
    }

    private static final class PendingDeath {
        private int ticksRemaining = 2;
        private TriggerContext context;

        private PendingDeath(TriggerContext context) {
            this.context = context;
        }
    }

    private record TriggerContext(boolean explosion, Vec3 origin, double magnitude) {
        private static final TriggerContext NORMAL = new TriggerContext(false, Vec3.ZERO, 0.0D);
    }

    private DeathDismembermentManager() {
    }
}
