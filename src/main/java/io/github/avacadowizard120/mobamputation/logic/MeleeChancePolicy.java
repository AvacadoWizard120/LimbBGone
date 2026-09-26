package io.github.avacadowizard120.mobamputation.logic;

import io.github.avacadowizard120.mobamputation.MobAmputation;
import io.github.avacadowizard120.mobamputation.api.Limb;
import io.github.avacadowizard120.mobamputation.config.MobAmputationConfig;
import io.github.avacadowizard120.mobamputation.config.RuleTextLimits;
import io.github.avacadowizard120.mobamputation.config.ToolRuleDocument;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

/** Common melee chance calculation shared by visual rolls and server validation. */
public final class MeleeChancePolicy {
    private static final int SPECIALIST_BONUS_PER_LEVEL = 20;
    private static final int SEVERANCE_BONUS_PER_LEVEL = 15;

    public static final ResourceLocation BEHEADING = id("beheading");
    public static final ResourceLocation DISMEMBERMENT = id("dismemberment");
    public static final ResourceLocation SEVERANCE = id("severance");
    public static final TagKey<Item> AMPUTATION_TOOLS = TagKey.create(Registries.ITEM, id("amputation_tools"));
    private static volatile CachedRules cachedRules;

    public static int effectiveChance(Limb limb, ItemStack held) {
        return Math.min(100, baseChance(held) + enchantmentBonus(limb, held));
    }

    /** Configured item/tag rule chance before an offensive enchantment bonus. */
    public static int baseChance(ItemStack held) {
        if (held.isEmpty()) {
            return 0;
        }
        String source = RuleTextLimits.toolRules(MobAmputationConfig.get().toolRules());
        return parsedRules(source).chanceFor(held);
    }

    public static int enchantmentBonus(Limb limb, ItemStack held) {
        if (!MobAmputationConfig.get().enchantmentsEnabled()
                || held.isEmpty()
                || !held.is(AMPUTATION_TOOLS)) {
            return 0;
        }

        ResourceLocation specialist = limb == Limb.HEAD ? BEHEADING : DISMEMBERMENT;
        int specialistBonus = enchantmentLevel(held, specialist) * SPECIALIST_BONUS_PER_LEVEL;
        int severanceBonus = enchantmentLevel(held, SEVERANCE) * SEVERANCE_BONUS_PER_LEVEL;
        return Math.max(specialistBonus, severanceBonus);
    }

    private static int enchantmentLevel(ItemStack stack, ResourceLocation id) {
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

    private static CachedRules parsedRules(String source) {
        String safeSource = source == null ? "" : source;
        CachedRules cached = cachedRules;
        if (cached != null && cached.source().equals(safeSource)) {
            return cached;
        }
        synchronized (MeleeChancePolicy.class) {
            cached = cachedRules;
            if (cached == null || !cached.source().equals(safeSource)) {
                cached = compileRules(safeSource);
                cachedRules = cached;
            }
            return cached;
        }
    }

    private static CachedRules compileRules(String source) {
        ToolRuleDocument document = ToolRuleDocument.parse(source);
        List<ExactRule> exactRules = new ArrayList<>();
        List<TagRule> tagRules = new ArrayList<>();
        int fallback = 0;
        boolean foundFallback = false;
        for (ToolRuleDocument.Rule rule : document.rules()) {
            if (!rule.isValid()) {
                continue;
            }
            switch (rule.targetType()) {
                case ITEM -> exactRules.add(new ExactRule(rule.targetId(), rule.chance()));
                case TAG -> tagRules.add(new TagRule(
                        TagKey.create(Registries.ITEM, rule.targetId()), rule.chance()
                ));
                case FALLBACK -> {
                    if (!foundFallback) {
                        fallback = rule.chance();
                        foundFallback = true;
                    }
                }
                case RAW -> {
                }
            }
        }
        return new CachedRules(source, List.copyOf(exactRules), List.copyOf(tagRules), fallback);
    }

    private record CachedRules(
            String source,
            List<ExactRule> exactRules,
            List<TagRule> tagRules,
            int fallback
    ) {
        private int chanceFor(ItemStack held) {
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(held.getItem());
            for (ExactRule rule : exactRules) {
                if (rule.itemId().equals(itemId)) {
                    return rule.chance();
                }
            }
            for (TagRule rule : tagRules) {
                if (held.is(rule.tag())) {
                    return rule.chance();
                }
            }
            return fallback;
        }
    }

    private record ExactRule(ResourceLocation itemId, int chance) {
    }

    private record TagRule(TagKey<Item> tag, int chance) {
    }

    private MeleeChancePolicy() {
    }
}
