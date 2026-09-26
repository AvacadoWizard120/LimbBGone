package io.github.avacadowizard120.mobamputation.logic;

import io.github.avacadowizard120.mobamputation.MobAmputation;
import io.github.avacadowizard120.mobamputation.api.Limb;
import io.github.avacadowizard120.mobamputation.config.MobAmputationConfig;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

/** Server-only body-part protection math, including convention-following modded armor. */
public final class ArmorProtectionPolicy {
    public static final ResourceLocation REINFORCEMENT = id("reinforcement");
    public static final ResourceLocation ANATOMICAL_INTEGRITY = id("anatomical_integrity");

    /** Captures every mutable input at the actual server-observed hit. */
    public static ArmorSnapshot snapshot(Player player) {
        MobAmputationConfig.ArmorProtection config = MobAmputationConfig.get().armorProtection();
        if (!config.enabled()) {
            return ArmorSnapshot.DISABLED;
        }
        double global = player.getArmorValue() * config.globalReductionPerArmorPoint()
                + player.getAttributeValue(Attributes.ARMOR_TOUGHNESS) * config.toughnessReductionPerPoint();
        PartProtection head = snapshotPart(player.getItemBySlot(EquipmentSlot.HEAD), EquipmentSlot.HEAD, config);
        PartProtection torso = snapshotPart(player.getItemBySlot(EquipmentSlot.CHEST), EquipmentSlot.CHEST, config);
        return new ArmorSnapshot(true, global, config.maximumReduction(), head, torso);
    }

    public static int protectedChance(ArmorSnapshot snapshot, Limb limb, int unprotectedChance) {
        int chance = Math.max(0, Math.min(100, unprotectedChance));
        if (snapshot == null || !snapshot.enabled()) {
            return chance;
        }
        PartProtection part = limb == Limb.HEAD ? snapshot.head() : snapshot.torso();
        if (part.integrity()) {
            return 0;
        }
        int ordinaryReduction = Math.min(
                snapshot.maximumReduction(),
                Math.max(0, (int) Math.round(snapshot.globalReduction() + part.armorReduction()))
        );
        int reduction = Math.min(95, ordinaryReduction + part.enchantmentReduction());
        return Math.max(0, Math.min(100, (int) Math.round(chance * (100 - reduction) / 100.0D)));
    }

    /** Anatomical Integrity remains an absolute body-part immunity. */
    public static boolean isImmune(ArmorSnapshot snapshot, Limb limb) {
        if (snapshot == null || !snapshot.enabled()) {
            return false;
        }
        PartProtection part = limb == Limb.HEAD ? snapshot.head() : snapshot.torso();
        return part.integrity();
    }

    public static EquipmentSlot coveringSlot(Limb limb) {
        return limb == Limb.HEAD ? EquipmentSlot.HEAD : EquipmentSlot.CHEST;
    }

    private static double coveredArmorPoints(ItemStack stack, EquipmentSlot slot) {
        if (stack.isEmpty()) {
            return 0.0D;
        }
        double[] armor = {0.0D};
        stack.forEachModifier(slot, (attribute, modifier) -> {
            if (attribute.is(Attributes.ARMOR)
                    && modifier.operation() == AttributeModifier.Operation.ADD_VALUE) {
                armor[0] += modifier.amount();
            }
        });
        return Math.max(0.0D, armor[0]);
    }

    private static PartProtection snapshotPart(
            ItemStack stack,
            EquipmentSlot slot,
            MobAmputationConfig.ArmorProtection config
    ) {
        double armorReduction = coveredArmorPoints(stack, slot) * config.coveredReductionPerArmorPoint();
        int enchantmentReduction = 0;
        boolean integrity = false;
        if (config.enchantmentsEnabled()) {
            enchantmentReduction = enchantmentLevel(stack, REINFORCEMENT)
                    * config.reinforcementReductionPerLevel();
            integrity = config.anatomicalIntegrityEnabled()
                    && enchantmentLevel(stack, ANATOMICAL_INTEGRITY) > 0;
        }
        return new PartProtection(armorReduction, enchantmentReduction, integrity);
    }

    private static int enchantmentLevel(ItemStack stack, ResourceLocation id) {
        if (stack.isEmpty()) {
            return 0;
        }
        for (Holder<Enchantment> enchantment : stack.getEnchantments().keySet()) {
            if (enchantment.is(id)) {
                return stack.getEnchantments().getLevel(enchantment);
            }
        }
        return 0;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MobAmputation.MOD_ID, path);
    }

    public record ArmorSnapshot(
            boolean enabled,
            double globalReduction,
            int maximumReduction,
            PartProtection head,
            PartProtection torso
    ) {
        private static final ArmorSnapshot DISABLED = new ArmorSnapshot(
                false, 0.0D, 0,
                new PartProtection(0.0D, 0, false),
                new PartProtection(0.0D, 0, false)
        );
    }

    public record PartProtection(double armorReduction, int enchantmentReduction, boolean integrity) {
    }

    private ArmorProtectionPolicy() {
    }
}
