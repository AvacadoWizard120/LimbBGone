package io.github.avacadowizard120.mobamputation.logic;

import io.github.avacadowizard120.mobamputation.MobAmputation;
import io.github.avacadowizard120.mobamputation.api.Limb;
import io.github.avacadowizard120.mobamputation.content.MobAmputationContent;
import io.github.avacadowizard120.mobamputation.config.MobAmputationConfig;
import io.github.avacadowizard120.mobamputation.state.PlayerAmputationAccess;
import io.github.avacadowizard120.mobamputation.network.MobAmputationNetworking;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemStack;

/** Authoritative finite bleeding and inevitable delayed player decapitation. */
public final class PlayerTraumaManager {
    /**
     * A terminal decapitation hit may be intercepted by another mod (or by a
     * death-protection mechanic). Keep the wound armed and retry shortly
     * instead of allowing a surviving player to remain permanently headless.
     */
    private static final int TERMINAL_DECAPITATION_RETRY_TICKS = 10;

    public static final ResourceKey<DamageType> BLEEDING = ResourceKey.create(
            Registries.DAMAGE_TYPE, id("bleeding")
    );
    public static final ResourceKey<DamageType> DECAPITATION = ResourceKey.create(
            Registries.DAMAGE_TYPE, id("decapitation")
    );

    public static void amputate(Player player, Limb limb, Entity attacker) {
        if (player.level().isClientSide) {
            return;
        }
        PlayerAmputationAccess trauma = access(player);
        trauma.mobamputation$setDetached(limb, true);
        // Attribution belongs to the newest wound. In particular, an
        // environmental sever must clear the UUID from an older PvP wound so
        // a later bleed-out is not credited to the wrong attacker.
        trauma.mobamputation$setResponsibleAttacker(attacker == null ? null : attacker.getUUID());
        MobAmputationConfig.PlayerTrauma config = MobAmputationConfig.get().playerTrauma();
        if (limb == Limb.HEAD) {
            if (config.fatalDecapitation()) {
                int span = config.headBleedoutMaxTicks() - config.headBleedoutMinTicks() + 1;
                trauma.mobamputation$setFatalHeadTicks(
                        config.headBleedoutMinTicks() + player.getRandom().nextInt(Math.max(1, span))
                );
                trauma.mobamputation$setHeadBleedPulseTicks(config.headBleedIntervalTicks());
            }
        } else {
            if (config.armBleeding()) {
                trauma.mobamputation$setBleedTicks(limb, config.armBleedDurationTicks());
                if (trauma.mobamputation$getArmBleedPulseTicks() <= 0) {
                    trauma.mobamputation$setArmBleedPulseTicks(config.armBleedIntervalTicks());
                }
                refreshBleedingEffect(player, trauma);
            }
            dropFromDetachedHand(player, limb);
        }
    }

    public static boolean hasArmBleeding(Player player) {
        PlayerAmputationAccess trauma = access(player);
        return trauma.mobamputation$getBleedTicks(Limb.LEFT_ARM) > 0
                || trauma.mobamputation$getBleedTicks(Limb.RIGHT_ARM) > 0;
    }

    public static boolean canUseBandage(Player player) {
        return MobAmputationConfig.get().playerTrauma().bandagesEnabled() && hasArmBleeding(player);
    }

    public static void bandage(Player player) {
        PlayerAmputationAccess trauma = access(player);
        if (!MobAmputationConfig.get().playerTrauma().bandagesEnabled()) {
            return;
        }
        trauma.mobamputation$setBleedTicks(Limb.LEFT_ARM, 0);
        trauma.mobamputation$setBleedTicks(Limb.RIGHT_ARM, 0);
        trauma.mobamputation$setArmBleedPulseTicks(0);
        if (!player.level().isClientSide) {
            player.removeEffect(MobAmputationContent.bleeding());
            if (player instanceof ServerPlayer serverPlayer) {
                syncPlayerState(serverPlayer);
            }
        }
    }

    /**
     * Restores the complete authoritative player body in one operation.
     * Respawning and instant-regrowth effects must use this path so server
     * trauma, the visible bleeding effect, and every client's cached limb
     * mask cannot disagree.
     */
    public static void restoreIntactBody(ServerPlayer player) {
        PlayerAmputationAccess trauma = access(player);
        trauma.mobamputation$clearTrauma();
        ServerAmputationAuthority.clearDetachedState(player);
        player.removeEffect(MobAmputationContent.bleeding());
        if (!isIntactBodyState(trauma) || ServerAmputationAuthority.detachedMask(player) != 0) {
            throw new IllegalStateException("Player trauma clear did not produce an intact body");
        }
        MobAmputationNetworking.sendIntactPlayerStateToAllClients(player);
    }

    public static void tickServer(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            tickPlayer(player);
        }
    }

    public static void tickPlayer(ServerPlayer player) {
        PlayerAmputationAccess trauma = access(player);
        MobAmputationConfig.PlayerTrauma config = MobAmputationConfig.get().playerTrauma();
        if (!player.isAlive() || player.isSpectator()) {
            if (!player.isAlive()) {
                trauma.mobamputation$clearTrauma();
            }
            return;
        }

        // A selected hotbar slot and the offhand are mutable after the initial
        // sever. Enforce the physical restriction every server tick so swaps,
        // inventory clicks, commands, and other mods cannot leave a usable but
        // invisible stack in an amputated hand.
        enforceDetachedHands(player, trauma);

        int headTicks = trauma.mobamputation$getFatalHeadTicks();
        if (!config.fatalDecapitation() && headTicks > 0) {
            headTicks = 0;
            trauma.mobamputation$setFatalHeadTicks(0);
            trauma.mobamputation$setHeadBleedPulseTicks(0);
            syncPlayerState(player);
        }
        if (headTicks > 0) {
            headTicks--;
            trauma.mobamputation$setFatalHeadTicks(headTicks);
            if (headTicks == 0) {
                // Arm the retry before firing the damage event. Player#die
                // clears all trauma when the hit succeeds; if anything
                // cancels or survives it, the fatal state remains active.
                trauma.mobamputation$setFatalHeadTicks(TERMINAL_DECAPITATION_RETRY_TICKS);
                player.hurt(damageSource(player, DECAPITATION), Float.MAX_VALUE);
                // Whether death succeeded or not, do not also apply an
                // ordinary bleed pulse in the terminal-hit tick.
                return;
            }
        }

        int previousBleedingMask = armBleedingMask(trauma);
        int left = config.armBleeding() ? decrement(trauma, Limb.LEFT_ARM) : clearWound(trauma, Limb.LEFT_ARM);
        int right = config.armBleeding() ? decrement(trauma, Limb.RIGHT_ARM) : clearWound(trauma, Limb.RIGHT_ARM);
        if (previousBleedingMask != armBleedingMask(trauma)) {
            syncPlayerState(player);
        }
        boolean armBleeding = left > 0 || right > 0;
        boolean headBleeding = headTicks > 0;
        if (!armBleeding && !headBleeding) {
            trauma.mobamputation$setArmBleedPulseTicks(0);
            trauma.mobamputation$setHeadBleedPulseTicks(0);
            player.removeEffect(MobAmputationContent.bleeding());
            return;
        }

        int activeArms = (left > 0 ? 1 : 0) + (right > 0 ? 1 : 0);
        if (armBleeding) {
            ensureBleedingEffect(player, Math.max(left, right), activeArms - 1);
        } else {
            trauma.mobamputation$setArmBleedPulseTicks(0);
            player.removeEffect(MobAmputationContent.bleeding());
        }

        float damage = 0.0F;
        if (armBleeding) {
            int armPulse = trauma.mobamputation$getArmBleedPulseTicks() - 1;
            if (armPulse <= 0) {
                damage += activeArms * config.armBleedDamageTenths() / 10.0F;
                armPulse = config.armBleedIntervalTicks();
            }
            trauma.mobamputation$setArmBleedPulseTicks(armPulse);
        }
        if (headBleeding) {
            int headPulse = trauma.mobamputation$getHeadBleedPulseTicks() - 1;
            if (headPulse <= 0) {
                damage += config.headBleedDamageTenths() / 10.0F;
                headPulse = config.headBleedIntervalTicks();
            }
            trauma.mobamputation$setHeadBleedPulseTicks(headPulse);
        } else {
            trauma.mobamputation$setHeadBleedPulseTicks(0);
        }
        if (damage > 0.0F) {
            player.hurt(damageSource(player, BLEEDING), damage);
        }
    }

    private static int decrement(PlayerAmputationAccess trauma, Limb limb) {
        int remaining = trauma.mobamputation$getBleedTicks(limb);
        if (remaining > 0) {
            trauma.mobamputation$setBleedTicks(limb, remaining - 1);
        }
        return remaining;
    }

    private static int clearWound(PlayerAmputationAccess trauma, Limb limb) {
        trauma.mobamputation$setBleedTicks(limb, 0);
        return 0;
    }

    private static int armBleedingMask(PlayerAmputationAccess trauma) {
        int mask = 0;
        if (trauma.mobamputation$getBleedTicks(Limb.LEFT_ARM) > 0) {
            mask |= Byte.toUnsignedInt(Limb.LEFT_ARM.bit());
        }
        if (trauma.mobamputation$getBleedTicks(Limb.RIGHT_ARM) > 0) {
            mask |= Byte.toUnsignedInt(Limb.RIGHT_ARM.bit());
        }
        return mask;
    }

    /** Pure postcondition used to guard the shared full-restoration path. */
    private static boolean isIntactBodyState(PlayerAmputationAccess trauma) {
        if (trauma.mobamputation$getLimbMask() != 0
                || trauma.mobamputation$getFatalHeadTicks() != 0
                || trauma.mobamputation$getArmBleedPulseTicks() != 0
                || trauma.mobamputation$getHeadBleedPulseTicks() != 0
                || trauma.mobamputation$getResponsibleAttacker() != null) {
            return false;
        }
        for (Limb limb : Limb.values()) {
            if (trauma.mobamputation$isDetached(limb)
                    || trauma.mobamputation$getBleedTicks(limb) != 0) {
                return false;
            }
        }
        return true;
    }

    private static void syncPlayerState(ServerPlayer player) {
        for (ServerPlayer viewer : player.server.getPlayerList().getPlayers()) {
            if (viewer.level() == player.level()) {
                MobAmputationNetworking.sendPlayerState(viewer, player);
            }
        }
    }

    private static EquipmentSlot equipmentSlot(Player player, Limb limb) {
        boolean mainArm = limb == Limb.RIGHT_ARM
                ? player.getMainArm() == HumanoidArm.RIGHT
                : player.getMainArm() == HumanoidArm.LEFT;
        return mainArm ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
    }

    private static void enforceDetachedHands(ServerPlayer player, PlayerAmputationAccess trauma) {
        if (trauma.mobamputation$isDetached(Limb.LEFT_ARM)) {
            dropFromDetachedHand(player, Limb.LEFT_ARM);
        }
        if (trauma.mobamputation$isDetached(Limb.RIGHT_ARM)) {
            dropFromDetachedHand(player, Limb.RIGHT_ARM);
        }
    }

    private static void dropFromDetachedHand(Player player, Limb limb) {
        EquipmentSlot severedHand = equipmentSlot(player, limb);
        ItemStack held = player.getItemBySlot(severedHand);
        if (held.isEmpty()) {
            return;
        }
        // Transfer the same stack object out of the inventory before spawning
        // the item entity. Copying here would duplicate components/counts.
        player.setItemSlot(severedHand, ItemStack.EMPTY);
        player.drop(held, false, true);
    }

    private static void refreshBleedingEffect(Player player, PlayerAmputationAccess trauma) {
        int left = trauma.mobamputation$getBleedTicks(Limb.LEFT_ARM);
        int right = trauma.mobamputation$getBleedTicks(Limb.RIGHT_ARM);
        int duration = Math.max(left, right);
        if (duration <= 0 || player.level().isClientSide) {
            return;
        }
        int amplifier = left > 0 && right > 0 ? 1 : 0;
        ensureBleedingEffect(player, duration, amplifier);
    }

    private static void ensureBleedingEffect(Player player, int duration, int amplifier) {
        MobEffectInstance existing = player.getEffect(MobAmputationContent.bleeding());
        if (existing == null || existing.getDuration() < duration - 2 || existing.getAmplifier() != amplifier) {
            player.addEffect(new MobEffectInstance(
                    MobAmputationContent.bleeding(), duration, amplifier, false, true, true
            ));
        }
    }

    private static DamageSource damageSource(ServerPlayer player, ResourceKey<DamageType> type) {
        Entity attacker = responsibleAttacker(player);
        var damageType = player.registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(type);
        return attacker == null
                ? new DamageSource(damageType)
                : new DamageSource(damageType, attacker);
    }

    private static Entity responsibleAttacker(ServerPlayer victim) {
        UUID uuid = access(victim).mobamputation$getResponsibleAttacker();
        if (uuid == null) {
            return null;
        }
        ServerLevel level = victim.serverLevel();
        Entity local = level.getEntity(uuid);
        if (local != null) {
            return local;
        }
        return victim.getServer() == null ? null : victim.getServer().getPlayerList().getPlayer(uuid);
    }

    private static PlayerAmputationAccess access(Player player) {
        return (PlayerAmputationAccess) player;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MobAmputation.MOD_ID, path);
    }

    private PlayerTraumaManager() {
    }
}
