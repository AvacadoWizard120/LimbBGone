package io.github.avacadowizard120.mobamputation.client;

import io.github.avacadowizard120.mobamputation.api.Limb;
import io.github.avacadowizard120.mobamputation.api.GibProfile;
import io.github.avacadowizard120.mobamputation.api.GibProfileRegistry;
import io.github.avacadowizard120.mobamputation.config.MobAmputationConfig;
import io.github.avacadowizard120.mobamputation.entity.GibEntity;
import io.github.avacadowizard120.mobamputation.logic.AmputationLogic;
import io.github.avacadowizard120.mobamputation.logic.LimbHitResolver;
import io.github.avacadowizard120.mobamputation.network.MobAmputationClientNetworking;
import io.github.avacadowizard120.mobamputation.network.DetachResultPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;

/**
 * Exact client-side ownership model used by the original mod: a strong map
 * from every eligible parent to three real local gib entities.
 */
public final class GibManager {
    private static final int MAX_PENDING_ENTITY_STATES = 256;
    private static final long PENDING_ENTITY_STATE_TTL_TICKS = 20L * 30L;
    private static final Map<LivingEntity, GibEntity[]> AMPUTATION_MAP = new HashMap<>();
    private static final Map<java.util.UUID, PendingEntityState> PENDING_ENTITY_STATES = new HashMap<>();
    private static final Map<java.util.UUID, PendingEntityState> AUTHORITATIVE_ENTITY_STATES = new HashMap<>();
    private static final Map<Integer, Integer> PLAYER_BLEEDING_MASKS = new HashMap<>();
    private static final ArrayList<FishingHook> FISH_HOOKS = new ArrayList<>();
    private static ClientLevel activeLevel;
    private static int nextLocalEntityId = -1;
    private static long clientTicks;

    /** iChunUtil's global client tick counter advances even while paused. */
    public static void clientTick(Minecraft minecraft) {
        clientTicks++;
        prunePendingEntityStates();
        DecapitationCamera.clientTick(minecraft);
        if (minecraft.level == null && activeLevel != null) {
            clear();
        } else if (minecraft.level != null && minecraft.player != null) {
            ensureLevel(minecraft.level);
            syncLocalPlayerGibs(minecraft.level, minecraft.player);
        }
    }

    public static long clientTicks() {
        return clientTicks;
    }

    public static void onEntityAdded(ClientLevel level, Entity entity) {
        ensureLevel(level);
        if (entity instanceof GibEntity) {
            return;
        }
        if (entity instanceof FishingHook hook) {
            FISH_HOOKS.add(hook);
        }
        if (!(entity instanceof LivingEntity living)) {
            return;
        }
        if (living instanceof LocalPlayer localPlayer) {
            syncLocalPlayerGibs(level, localPlayer);
            return;
        }
        if (!AmputationLogic.eligible(living)
                || AMPUTATION_MAP.containsKey(living)) {
            return;
        }
        attachGibs(level, living);
        applyPendingEntityState(living);
    }

    /**
     * The local player only needs a head proxy for the optional camera ride.
     * Keeping the arm slots empty avoids adding invisible local collision
     * proxies and leaves the original player exclusion otherwise intact.
     */
    private static void syncLocalPlayerGibs(ClientLevel level, LocalPlayer player) {
        // This is intentionally independent of playerGibs. That option
        // controls whether this client renders/targets other players; the
        // camera option controls whether a detach notice targeting this
        // client has a local head to follow.
        PendingEntityState authoritative = AUTHORITATIVE_ENTITY_STATES.get(player.getUUID());
        int authoritativeMask = authoritative == null ? 0 : authoritative.limbMask();
        boolean headWanted = MobAmputationConfig.get().decapitationCamera()
                || (authoritativeMask & Limb.HEAD.bit()) != 0;
        boolean armsWanted = MobAmputationConfig.get().playerArmAmputation()
                || (authoritativeMask & (Limb.LEFT_ARM.bit() | Limb.RIGHT_ARM.bit())) != 0;
        boolean wanted = headWanted || armsWanted;
        GibEntity[] existing = AMPUTATION_MAP.get(player);
        if (!wanted) {
            if (existing != null) {
                DecapitationCamera.reset(Minecraft.getInstance());
                AMPUTATION_MAP.remove(player);
                discard(existing);
                player.refreshDimensions();
            }
            return;
        }
        if (existing != null) {
            boolean correct = (existing[Limb.HEAD.ordinal()] != null) == headWanted
                    && (existing[Limb.LEFT_ARM.ordinal()] != null) == armsWanted
                    && (existing[Limb.RIGHT_ARM.ordinal()] != null) == armsWanted;
            if (correct) {
                return;
            }
            DecapitationCamera.reset(Minecraft.getInstance());
            AMPUTATION_MAP.remove(player);
            discard(existing);
        }

        GibEntity[] gibs = new GibEntity[Limb.values().length];
        for (Limb limb : Limb.values()) {
            if (limb == Limb.HEAD ? !headWanted : !armsWanted) {
                continue;
            }
            GibEntity gib = new GibEntity(level, player, limb);
            gib.setId(allocateLocalEntityId(level));
            gibs[limb.ordinal()] = gib;
            level.addEntity(gib);
        }
        AMPUTATION_MAP.put(player, gibs);
        if (authoritative != null) {
            applyEntityState(player.getId(), player.getUUID(), authoritative.limbMask(),
                    authoritative.bleedingLimbMask());
        }
        applyPendingEntityState(player);
    }

    private static void attachGibs(ClientLevel level, LivingEntity parent) {
        GibProfile profile = GibProfileRegistry.find(parent);
        if (profile == null) {
            return;
        }
        GibEntity[] gibs = new GibEntity[Limb.values().length];
        for (Limb limb : Limb.values()) {
            PendingEntityState authoritative = AUTHORITATIVE_ENTITY_STATES.get(parent.getUUID());
            boolean alreadyDetached = authoritative != null
                    && (authoritative.limbMask() & limb.bit()) != 0;
            // Current config gates new interactions. A durable server state
            // must still get a proxy so an already-missing limb remains
            // hidden if an administrator later disables new amputations.
            if ((!AmputationLogic.supportsLimb(parent, limb) && !alreadyDetached) || !profile.supports(limb)) {
                continue;
            }
            GibEntity gib = new GibEntity(level, parent, limb);
            gib.setId(allocateLocalEntityId(level));
            gibs[limb.ordinal()] = gib;
            level.addEntity(gib);
        }

        // The parent becomes a 0.4 x 1.5 torso only once all three proxy
        // entities exist, matching attachGibs as a single operation.
        AMPUTATION_MAP.put(parent, gibs);
        parent.refreshDimensions();
        parent.setPos(parent.getX(), parent.getY(), parent.getZ());
        for (GibEntity gib : gibs) {
            if (gib != null) {
                gib.refreshDimensions();
            }
        }
        PendingEntityState authoritative = AUTHORITATIVE_ENTITY_STATES.get(parent.getUUID());
        if (authoritative != null) {
            applyEntityState(parent.getId(), parent.getUUID(), authoritative.limbMask(),
                    authoritative.bleedingLimbMask());
        }
    }

    public static void endClientTick(ClientLevel level) {
        ensureLevel(level);

        Iterator<Map.Entry<LivingEntity, GibEntity[]>> entries = AMPUTATION_MAP.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<LivingEntity, GibEntity[]> entry = entries.next();
            LivingEntity parent = entry.getKey();
            if (!parent.isRemoved() && !parent.isBaby()) {
                continue;
            }
            discard(entry.getValue());
            entries.remove();
            // A respawn replaces the Player object but deliberately keeps its
            // UUID (and normally its entity id). The replacement can already
            // exist by the time this old object is retired, so do not let the
            // old object's cleanup erase the replacement's newer snapshot.
            if (!hasLiveReplacement(level, parent)) {
                AUTHORITATIVE_ENTITY_STATES.remove(parent.getUUID());
            }
            Entity current = level.getEntity(parent.getId());
            if (!isSameLivingIdentity(current, parent)) {
                // Vanilla reuses the id on respawn, in which case this entry
                // already belongs to the replacement. A mod may choose a new
                // id instead; do not retain the retired player's old-id mask.
                PLAYER_BLEEDING_MASKS.remove(parent.getId());
            }
            // Pending states are specifically packets that arrived before
            // their entity. They are not owned by this removed object and are
            // allowed to expire through the bounded TTL instead.
            parent.refreshDimensions();
        }

        for (int index = FISH_HOOKS.size() - 1; index >= 0; index--) {
            FishingHook hook = FISH_HOOKS.get(index);
            if (hook.isRemoved()) {
                FISH_HOOKS.remove(index);
                continue;
            }
            Entity caught = hook.getHookedIn();
            LivingEntity caughtLiving = caught instanceof LivingEntity living ? living : null;
            GibEntity[] gibs = caughtLiving == null ? null : AMPUTATION_MAP.get(caughtLiving);
            if (caught != null) {
                if (gibs != null) {
                    Limb selectedLimb = LimbHitResolver.fishing(caughtLiving, hook.getId());
                    GibEntity gib = selectedLimb == null ? null : gibs[selectedLimb.ordinal()];
                    if (gib != null) {
                        gib.setFishHook(hook);
                    }
                }
                // Upstream stops tracking a hook as soon as it catches any
                // entity, even when that entity is not an eligible parent.
                FISH_HOOKS.remove(index);
            }
        }
    }

    /** Called by the faithful server-to-client detach broadcast. */
    public static void detachRemote(int parentId, Limb limb) {
        ClientLevel level = activeLevel;
        if (level == null) {
            return;
        }
        Entity entity = level.getEntity(parentId);
        if (entity instanceof LocalPlayer localPlayer && !AMPUTATION_MAP.containsKey(localPlayer)) {
            syncLocalPlayerGibs(level, localPlayer);
        }
        GibEntity[] gibs = entity instanceof LivingEntity living ? AMPUTATION_MAP.get(living) : null;
        if (gibs != null && limb.ordinal() >= 0 && limb.ordinal() < gibs.length) {
            GibEntity gib = gibs[limb.ordinal()];
            if (gib != null) {
                gib.requestRemoteDetach();
            }
        }
    }

    /** Resolves only the exact local candidate named by the server. */
    public static void resolveServerDetach(DetachResultPayload result) {
        ClientLevel level = activeLevel;
        if (level == null || result.limb() < 0 || result.limb() >= Limb.values().length) {
            return;
        }
        Entity entity = level.getEntity(result.parentId());
        GibEntity[] gibs = entity instanceof LivingEntity living ? AMPUTATION_MAP.get(living) : null;
        if (gibs != null) {
            GibEntity gib = gibs[result.limb()];
            if (gib != null) {
                gib.resolveServerDetach(result.requestId(), result.accepted());
            }
        }
    }

    public static boolean hasGibs(LivingEntity entity) {
        return AMPUTATION_MAP.containsKey(entity);
    }

    public static boolean isDetached(LivingEntity entity, Limb limb) {
        GibEntity[] gibs = AMPUTATION_MAP.get(entity);
        GibEntity gib = gibs == null ? null : gibs[limb.ordinal()];
        return gib != null && !gib.isAttached();
    }

    /** Applies an authoritative player limb mask received from the server. */
    public static void applyEntityState(
            int entityId,
            java.util.UUID entityUuid,
            int limbMask,
            int bleedingLimbMask
    ) {
        // A vanilla respawn packet can replace Minecraft.level before our
        // ordinary entity/tick hooks have advanced activeLevel. Always bind
        // an incoming snapshot to the actual current level first.
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        ensureLevel(level);
        PendingEntityState state = new PendingEntityState(limbMask, bleedingLimbMask);
        // UUID is stable across respawn while the numeric id and Player object
        // are in flight. Record the newer authority before trying either.
        AUTHORITATIVE_ENTITY_STATES.put(entityUuid, state);
        Entity entity = level.getEntity(entityId);
        LocalPlayer localPlayer = Minecraft.getInstance().player;
        if (localPlayer != null
                && localPlayer.getUUID().equals(entityUuid)
                && entity != localPlayer) {
            // During LocalPlayer reconstruction vanilla can expose the new
            // player through Minecraft while ClientLevel still indexes the
            // old same-UUID object under its reused id. Prefer the live local
            // player; UUID is the authoritative identity in that window.
            entity = localPlayer;
        }
        if (!(entity instanceof LivingEntity living) || !living.getUUID().equals(entityUuid)) {
            rememberPendingEntityState(entityUuid, limbMask, bleedingLimbMask);
            return;
        }
        if (living instanceof LocalPlayer local) {
            syncLocalPlayerGibs(level, local);
        }
        GibEntity[] gibs = AMPUTATION_MAP.get(living);
        if (gibs == null) {
            attachGibs(level, living);
            gibs = AMPUTATION_MAP.get(living);
        }
        if (gibs == null) {
            return;
        }

        // A remote Player can be spawned before their authoritative snapshot.
        // If arm amputations were disabled since that player lost an arm, the
        // initial proxy array contains only its head. Materialize masked
        // profile limbs here so persisted wounds remain visually missing even
        // though no new amputation of that limb is currently allowed.
        GibProfile profile = GibProfileRegistry.find(living);
        if (profile != null) {
            for (Limb limb : Limb.values()) {
                if ((limbMask & limb.bit()) != 0
                        && gibs[limb.ordinal()] == null
                        && profile.supports(limb)) {
                    GibEntity gib = new GibEntity(level, living, limb);
                    gib.setId(allocateLocalEntityId(level));
                    gibs[limb.ordinal()] = gib;
                    level.addEntity(gib);
                }
            }
        }
        for (Limb limb : Limb.values()) {
            if ((limbMask & limb.bit()) != 0 && gibs[limb.ordinal()] != null) {
                GibEntity gib = gibs[limb.ordinal()];
                gib.applyDetachedSnapshot();
            } else if ((limbMask & limb.bit()) == 0 && gibs[limb.ordinal()] != null
                    && !gibs[limb.ordinal()].isAttached()) {
                // A zero/newer snapshot (notably respawn) replaces stale
                // missing-part state by rebuilding the local proxies once.
                if (limb == Limb.HEAD && living instanceof LocalPlayer) {
                    // Regrowth happens in the existing level, so there is no
                    // vanilla respawn transition to reclaim camera ownership.
                    DecapitationCamera.reset(Minecraft.getInstance());
                }
                AUTHORITATIVE_ENTITY_STATES.put(entityUuid,
                        new PendingEntityState(limbMask, bleedingLimbMask));
                discard(gibs);
                AMPUTATION_MAP.remove(living);
                if (living instanceof LocalPlayer local) {
                    syncLocalPlayerGibs(level, local);
                } else {
                    attachGibs(level, living);
                }
                return;
            }
        }
        PENDING_ENTITY_STATES.remove(entityUuid);
        if (living instanceof Player) {
            PLAYER_BLEEDING_MASKS.put(entityId, bleedingLimbMask);
        }
    }

    private static void applyPendingEntityState(LivingEntity entity) {
        PendingEntityState state = PENDING_ENTITY_STATES.remove(entity.getUUID());
        if (state != null) {
            applyEntityState(entity.getId(), entity.getUUID(), state.limbMask(), state.bleedingLimbMask());
        }
    }

    private static void rememberPendingEntityState(
            java.util.UUID entityUuid,
            int limbMask,
            int bleedingLimbMask
    ) {
        if (!PENDING_ENTITY_STATES.containsKey(entityUuid)
                && PENDING_ENTITY_STATES.size() >= MAX_PENDING_ENTITY_STATES) {
            java.util.UUID oldestUuid = null;
            long oldestTick = Long.MAX_VALUE;
            for (Map.Entry<java.util.UUID, PendingEntityState> entry : PENDING_ENTITY_STATES.entrySet()) {
                if (entry.getValue().receivedAtTick() < oldestTick) {
                    oldestUuid = entry.getKey();
                    oldestTick = entry.getValue().receivedAtTick();
                }
            }
            if (oldestUuid != null) {
                PENDING_ENTITY_STATES.remove(oldestUuid);
            }
        }
        PENDING_ENTITY_STATES.put(entityUuid,
                new PendingEntityState(limbMask, bleedingLimbMask, clientTicks));
    }

    private static void prunePendingEntityStates() {
        PENDING_ENTITY_STATES.entrySet().removeIf(entry ->
                clientTicks - entry.getValue().receivedAtTick() > PENDING_ENTITY_STATE_TTL_TICKS);
    }

    private static boolean hasLiveReplacement(ClientLevel level, LivingEntity retired) {
        Entity indexed = level.getEntity(retired.getId());
        if (isSameLivingIdentity(indexed, retired)) {
            return true;
        }
        if (isSameLivingIdentity(Minecraft.getInstance().player, retired)) {
            return true;
        }
        for (LivingEntity candidate : AMPUTATION_MAP.keySet()) {
            if (isSameLivingIdentity(candidate, retired)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSameLivingIdentity(Entity candidate, LivingEntity retired) {
        return candidate instanceof LivingEntity living
                && living != retired
                && !living.isRemoved()
                && living.getUUID().equals(retired.getUUID());
    }

    public static boolean isBleeding(LivingEntity entity, Limb limb) {
        if (!(entity instanceof Player)) {
            return true;
        }
        return (PLAYER_BLEEDING_MASKS.getOrDefault(entity.getId(), 0) & limb.bit()) != 0;
    }

    /**
     * Hands a dying parent over to Mob Dismemberment without duplicating any
     * limb that was already severed. Attached hitbox proxies are discarded;
     * detached physical gibs are deliberately left alive.
     */
    public static void releaseForDeath(LivingEntity parent) {
        GibEntity[] gibs = AMPUTATION_MAP.remove(parent);
        if (gibs == null) {
            return;
        }
        for (GibEntity gib : gibs) {
            if (gib != null && gib.isAttached() && !gib.isRemoved()) {
                gib.discard();
            }
        }
        PENDING_ENTITY_STATES.remove(parent.getUUID());
        AUTHORITATIVE_ENTITY_STATES.remove(parent.getUUID());
        PLAYER_BLEEDING_MASKS.remove(parent.getId());
        parent.refreshDimensions();
    }

    public static boolean shouldRenderAmputations(LivingEntity entity) {
        if (!(entity instanceof Player) || Minecraft.getInstance().player != entity) {
            return true;
        }
        return !Minecraft.getInstance().options.getCameraType().isFirstPerson();
    }

    /**
     * Includes the inventory paper doll without enabling local gib rendering
     * or parent hitbox shrinking during that isolated GUI render.
     */
    public static boolean shouldRenderModelAmputations(LivingEntity entity) {
        return InventoryPreviewRenderContext.isActive() || shouldRenderAmputations(entity);
    }

    public static boolean shouldRender(GibEntity gib) {
        return shouldRenderAmputations(gib.parent());
    }

    public static boolean shouldShrinkParent(LivingEntity entity) {
        GibProfile profile = GibProfileRegistry.find(entity);
        return profile != null && profile.shrinkHumanoidParent()
                && hasGibs(entity) && shouldRenderAmputations(entity);
    }

    public static boolean isLocalProxy(GibEntity gib) {
        return gib.parent() == Minecraft.getInstance().player;
    }

    public static Limb limbForHand(Player player, InteractionHand hand) {
        HumanoidArm arm = hand == InteractionHand.MAIN_HAND
                ? player.getMainArm()
                : player.getMainArm().getOpposite();
        return arm == HumanoidArm.LEFT ? Limb.LEFT_ARM : Limb.RIGHT_ARM;
    }

    /** True only for the invisible proxy reserved for this client's camera. */
    public static boolean isLocalCameraHead(GibEntity gib) {
        Minecraft minecraft = Minecraft.getInstance();
        if (gib.limb() != Limb.HEAD || gib.parent() != minecraft.player) {
            return false;
        }
        GibEntity[] gibs = AMPUTATION_MAP.get(gib.parent());
        return gibs != null && gibs[Limb.HEAD.ordinal()] == gib;
    }

    public static void clear() {
        clearInternal();
        MobAmputationClientNetworking.resetSession();
    }

    private static void clearInternal() {
        DecapitationCamera.reset(Minecraft.getInstance());
        ArrayList<LivingEntity> parents = new ArrayList<>(AMPUTATION_MAP.keySet());
        for (Map.Entry<LivingEntity, GibEntity[]> entry : AMPUTATION_MAP.entrySet()) {
            discard(entry.getValue());
        }
        AMPUTATION_MAP.clear();
        for (LivingEntity parent : parents) {
            parent.refreshDimensions();
        }
        FISH_HOOKS.clear();
        PENDING_ENTITY_STATES.clear();
        AUTHORITATIVE_ENTITY_STATES.clear();
        PLAYER_BLEEDING_MASKS.clear();
        activeLevel = null;
        nextLocalEntityId = -1;
    }

    private static void ensureLevel(ClientLevel level) {
        if (activeLevel != level) {
            clearInternal();
            activeLevel = level;
        }
    }

    public static int allocateLocalEntityId(ClientLevel level) {
        while (nextLocalEntityId >= 0 || level.getEntity(nextLocalEntityId) != null) {
            nextLocalEntityId--;
            if (nextLocalEntityId == Integer.MIN_VALUE) {
                nextLocalEntityId = -1;
            }
        }
        return nextLocalEntityId--;
    }

    private static void discard(GibEntity[] gibs) {
        for (GibEntity gib : gibs) {
            if (gib != null && !gib.isRemoved()) {
                gib.discard();
            }
        }
    }

    private GibManager() {
    }

    private record PendingEntityState(int limbMask, int bleedingLimbMask, long receivedAtTick) {
        private PendingEntityState(int limbMask, int bleedingLimbMask) {
            this(limbMask, bleedingLimbMask, clientTicks);
        }
    }
}
