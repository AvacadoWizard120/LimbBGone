package io.github.avacadowizard120.mobamputation.logic;

import io.github.avacadowizard120.mobamputation.config.MobAmputationConfig;
import io.github.avacadowizard120.mobamputation.config.RuleTextLimits;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.DragonFireball;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.projectile.LlamaSpit;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.entity.projectile.SpectralArrow;
import net.minecraft.world.entity.projectile.ThrownEgg;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.entity.projectile.ThrownExperienceBottle;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.entity.projectile.WitherSkull;

/** Common, side-safe implementation of the projectile rule grammar. */
public final class ProjectileChancePolicy {
    private static final Pattern REGISTRY_ID_RULE = Pattern.compile(
            "^\\s*\\[([^]\\r\\n]+)]\\s*(?::\\s*(.*?))?\\s*$",
            Pattern.DOTALL
    );
    private static final Map<String, String> LEGACY_ENTITY_NAMES = Map.ofEntries(
            Map.entry("arrow", "Arrow"),
            Map.entry("spectral_arrow", "SpectralArrow"),
            Map.entry("snowball", "Snowball"),
            Map.entry("egg", "ThrownEgg"),
            Map.entry("potion", "ThrownPotion"),
            Map.entry("experience_bottle", "ThrownExpBottle"),
            Map.entry("fireball", "Fireball"),
            Map.entry("small_fireball", "SmallFireball"),
            Map.entry("dragon_fireball", "DragonFireball"),
            Map.entry("wither_skull", "WitherSkull"),
            Map.entry("shulker_bullet", "ShulkerBullet"),
            Map.entry("llama_spit", "LlamaSpit"),
            Map.entry("firework_rocket", "FireworksRocketEntity"),
            Map.entry("ender_pearl", "ThrownEnderpearl")
    );
    private static final Map<String, Class<?>> LEGACY_CLASS_ALIASES = Map.ofEntries(
            Map.entry("net.minecraft.entity.projectile.EntityArrow", AbstractArrow.class),
            Map.entry("net.minecraft.entity.projectile.EntityTippedArrow", Arrow.class),
            Map.entry("net.minecraft.entity.projectile.EntitySpectralArrow", SpectralArrow.class),
            Map.entry("net.minecraft.entity.projectile.EntitySnowball", Snowball.class),
            Map.entry("net.minecraft.entity.projectile.EntityEgg", ThrownEgg.class),
            Map.entry("net.minecraft.entity.projectile.EntityPotion", ThrownPotion.class),
            Map.entry("net.minecraft.entity.projectile.EntityExpBottle", ThrownExperienceBottle.class),
            Map.entry("net.minecraft.entity.projectile.EntityLargeFireball", LargeFireball.class),
            Map.entry("net.minecraft.entity.projectile.EntitySmallFireball", SmallFireball.class),
            Map.entry("net.minecraft.entity.projectile.EntityDragonFireball", DragonFireball.class),
            Map.entry("net.minecraft.entity.projectile.EntityWitherSkull", WitherSkull.class),
            Map.entry("net.minecraft.entity.projectile.EntityShulkerBullet", ShulkerBullet.class),
            Map.entry("net.minecraft.entity.projectile.EntityLlamaSpit", LlamaSpit.class),
            Map.entry("net.minecraft.entity.projectile.EntityFireworkRocket", FireworkRocketEntity.class),
            Map.entry("net.minecraft.entity.projectile.EntityEnderPearl", ThrownEnderpearl.class)
    );

    /**
     * Resolves eligibility and percentage without rolling. The boolean says
     * whether a rule (or the generic-projectile fallback) permits this source;
     * percentages deliberately remain unclamped to retain raw-rule semantics.
     */
    public static Decision evaluate(Entity direct, boolean genericProjectile) {
        MobAmputationConfig.Snapshot config = MobAmputationConfig.get();
        String configuredRules = RuleTextLimits.projectileRules(config.projectileList());
        Map<String, ArrayList<String>> registryIdRules = parseRegistryIdRules(configuredRules);
        Map<String, ArrayList<String>> projectileList = parseProjectileList(configuredRules);
        projectileList.computeIfAbsent("Arrow", ignored -> arguments("33"));

        String registryId = BuiltInRegistries.ENTITY_TYPE.getKey(direct.getType()).toString();
        String registryPath = BuiltInRegistries.ENTITY_TYPE.getKey(direct.getType()).getPath();
        String key = LEGACY_ENTITY_NAMES.getOrDefault(registryPath, registryId);

        ArrayList<String> exactRegistryChance = registryIdRules.get(registryId);
        if (exactRegistryChance != null) {
            return new Decision(true, configuredChance(exactRegistryChance, config.unlistedProjectileChance()));
        }

        for (String configured : projectileList.keySet()) {
            try {
                Class<?> configuredClass = LEGACY_CLASS_ALIASES.get(configured);
                if (configuredClass == null) {
                    configuredClass = Class.forName(configured);
                }
                // Preserve the established class-rule quirk: the result is
                // ignored, so the
                // first loadable configured class key wins for every source.
                configuredClass.isInstance(direct);
                key = configured;
                break;
            } catch (ClassNotFoundException ignored) {
            }
        }

        if (!projectileList.containsKey(key)
                && !projectileList.containsKey(registryId)
                && !(genericProjectile && config.allowProjectileGibbing())) {
            return new Decision(false, config.unlistedProjectileChance());
        }

        ArrayList<String> chances = projectileList.get(key);
        if (chances == null) {
            chances = projectileList.get(registryId);
        }
        return new Decision(true, configuredChance(chances, config.unlistedProjectileChance()));
    }

    private static int configuredChance(ArrayList<String> chances, int fallback) {
        if (chances != null && !chances.isEmpty()) {
            try {
                return Integer.parseInt(chances.get(0));
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private static ArrayList<String> arguments(String argument) {
        ArrayList<String> result = new ArrayList<>();
        result.add(argument);
        return result;
    }

    private static Map<String, ArrayList<String>> parseRegistryIdRules(String configured) {
        Map<String, ArrayList<String>> result = new HashMap<>();
        String[] entries = configured.split(", *");
        for (String entry : entries) {
            Matcher matcher = REGISTRY_ID_RULE.matcher(entry);
            if (!matcher.matches()) {
                continue;
            }
            ArrayList<String> arguments = new ArrayList<>();
            if (matcher.group(2) != null) {
                arguments.add(matcher.group(2).trim());
            }
            result.put(matcher.group(1).trim(), arguments);
        }
        return result;
    }

    private static Map<String, ArrayList<String>> parseProjectileList(String configured) {
        Map<String, ArrayList<String>> result = new HashMap<>();
        String[] entries = configured.split(", *");
        if (entries.length == 0 || entries[0].isEmpty()) {
            return result;
        }
        for (String entry : entries) {
            String[] parts = entry.split(": *");
            if (parts.length == 0 || parts[0].isEmpty()) {
                continue;
            }
            ArrayList<String> arguments = new ArrayList<>();
            for (int index = 1; index < parts.length; index++) {
                arguments.add(parts[index]);
            }
            result.put(parts[0], arguments);
        }
        return result;
    }

    public record Decision(boolean eligible, int percentage) {
    }

    private ProjectileChancePolicy() {
    }
}
