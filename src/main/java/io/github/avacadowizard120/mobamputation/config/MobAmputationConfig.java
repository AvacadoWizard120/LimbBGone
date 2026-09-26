package io.github.avacadowizard120.mobamputation.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public final class MobAmputationConfig {
    private static final Path FILE = Paths.get("config", "mobamputation.properties");
    private static volatile Snapshot current = Snapshot.defaults();
    private static volatile Snapshot session;

    public static Snapshot get() {
        Snapshot override = session;
        return override == null ? current : override;
    }

    /** True while a connected modded server owns gameplay/session settings. */
    public static boolean hasSessionOverride() {
        return session != null;
    }

    public static synchronized void load() {
        Properties properties = new Properties();
        if (Files.isRegularFile(FILE)) {
            try (Reader reader = Files.newBufferedReader(FILE)) {
                properties.load(reader);
            } catch (IOException exception) {
                System.err.println("[Mob Amputation] Could not read " + FILE + ": " + exception.getMessage());
            }
        }
        current = Snapshot.from(properties);
        session = null;
        write(current);
    }

    /** Applies server-owned gameplay settings while retaining local visuals. */
    public static synchronized void applySession(
            boolean headlessDeath,
            int unlistedProjectileChance,
            int fishingChance,
            String projectileList,
            boolean allowProjectileGibbing,
            String toolRules,
            boolean enchantmentsEnabled
    ) {
        Snapshot local = current;
        applySession(
                headlessDeath,
                unlistedProjectileChance,
                fishingChance,
                projectileList,
                allowProjectileGibbing,
                toolRules,
                enchantmentsEnabled,
                local.playerGibs(),
                local.playerTrauma(),
                local.armorProtection(),
                local.creeperAmputation()
        );
    }

    /** Applies every server-owned gameplay field while retaining local visuals. */
    public static synchronized void applySession(
            boolean headlessDeath,
            int unlistedProjectileChance,
            int fishingChance,
            String projectileList,
            boolean allowProjectileGibbing,
            String toolRules,
            boolean enchantmentsEnabled,
            boolean playerGibs,
            PlayerTrauma playerTrauma,
            ArmorProtection armorProtection,
            CreeperAmputation creeperAmputation
    ) {
        Snapshot local = current;
        session = new Snapshot(
                local.gibTime(),
                local.gibGroundTime(),
                local.blood(),
                local.bloodCount(),
                local.bloodSplurt(),
                local.greenBlood(),
                playerGibs,
                local.gibPushing(),
                headlessDeath,
                clampChance(unlistedProjectileChance),
                clampChance(fishingChance),
                projectileList == null ? "" : projectileList,
                allowProjectileGibbing,
                toolRules == null ? "" : toolRules,
                enchantmentsEnabled,
                local.decapitationCamera(),
                playerTrauma.normalized(),
                armorProtection.normalized(),
                creeperAmputation,
                local.deathDismemberment(),
                local.bloodSurfacePhysics()
        );
    }

    public static synchronized void clearSession() {
        session = null;
    }

    /** Saves local configuration without copying a connected server's gameplay settings. */
    public static synchronized void save(Snapshot snapshot) {
        Snapshot requested = snapshot.normalized();
        Snapshot override = session;
        if (override != null) {
            Snapshot local = current;
            // Never copy a server's gameplay/session values into the local
            // file. Only client-owned visual and camera settings are editable
            // for the duration of this connection.
            current = new Snapshot(
                    requested.gibTime(),
                    requested.gibGroundTime(),
                    requested.blood(),
                    requested.bloodCount(),
                    requested.bloodSplurt(),
                    requested.greenBlood(),
                    local.playerGibs(),
                    requested.gibPushing(),
                    local.headlessDeath(),
                    local.unlistedProjectileChance(),
                    local.fishingChance(),
                    local.projectileList(),
                    local.allowProjectileGibbing(),
                    local.toolRules(),
                    local.enchantmentsEnabled(),
                    requested.decapitationCamera(),
                    local.playerTrauma(),
                    local.armorProtection(),
                    local.creeperAmputation(),
                    requested.deathDismemberment(),
                    requested.bloodSurfacePhysics()
            );
            // Gameplay values remain server-owned for this connection. Local
            // visual and camera settings must still update immediately when
            // changed from the config screen.
            session = new Snapshot(
                    current.gibTime(),
                    current.gibGroundTime(),
                    current.blood(),
                    current.bloodCount(),
                    current.bloodSplurt(),
                    current.greenBlood(),
                    override.playerGibs(),
                    current.gibPushing(),
                    override.headlessDeath(),
                    override.unlistedProjectileChance(),
                    override.fishingChance(),
                    override.projectileList(),
                    override.allowProjectileGibbing(),
                    override.toolRules(),
                    override.enchantmentsEnabled(),
                    current.decapitationCamera(),
                    override.playerTrauma(),
                    override.armorProtection(),
                    override.creeperAmputation(),
                    current.deathDismemberment(),
                    current.bloodSurfacePhysics()
            );
        } else {
            current = requested;
        }
        write(current);
    }

    private static void write(Snapshot snapshot) {
        Properties properties = snapshot.toProperties();
        try {
            Files.createDirectories(FILE.getParent());
            try (Writer writer = Files.newBufferedWriter(FILE)) {
                properties.store(writer, "Mob Amputation configuration");
            }
        } catch (IOException exception) {
            System.err.println("[Mob Amputation] Could not write " + FILE + ": " + exception.getMessage());
        }
    }

    private static String value(Properties properties, String primaryKey, String legacyKey) {
        String value = properties.getProperty(primaryKey);
        return value != null ? value : properties.getProperty(legacyKey);
    }

    private static boolean bool(
            Properties properties,
            String primaryKey,
            String legacyKey,
            boolean fallback
    ) {
        String value = value(properties, primaryKey, legacyKey);
        if (value == null) {
            return fallback;
        }
        String normalized = value.trim();
        // Older configuration files used 0/1 booleans. Accept those values
        // as well as true/false.
        if ("1".equals(normalized)) {
            return true;
        }
        if ("0".equals(normalized)) {
            return false;
        }
        return Boolean.parseBoolean(normalized);
    }

    private static int integer(
            Properties properties,
            String primaryKey,
            String legacyKey,
            int fallback,
            int minimum,
            int maximum
    ) {
        String value = value(properties, primaryKey, legacyKey);
        if (value == null) {
            return fallback;
        }
        try {
            return Math.max(minimum, Math.min(maximum, Integer.parseInt(value.trim())));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static boolean hasArrowRule(String projectileList) {
        if (projectileList == null || projectileList.isEmpty()) {
            return false;
        }
        boolean legacyEntriesDisabled = projectileList.startsWith(",");
        for (String rawEntry : projectileList.split(", *")) {
            String entry = rawEntry.trim();
            if (entry.startsWith("[")) {
                int close = entry.indexOf(']');
                String remainder = close < 0 ? "" : entry.substring(close + 1).trim();
                if (close > 1
                        && "minecraft:arrow".equals(entry.substring(1, close).trim())
                        && (remainder.isEmpty() || remainder.startsWith(":"))) {
                    return true;
                }
                continue;
            }
            if (legacyEntriesDisabled) {
                continue;
            }
            int separator = entry.indexOf(':');
            String key = separator < 0 ? entry : entry.substring(0, separator).trim();
            if ("Arrow".equals(key)
                    || "net.minecraft.entity.projectile.EntityArrow".equals(key)
                    || "net.minecraft.entity.projectile.EntityTippedArrow".equals(key)
                    || "net.minecraft.world.entity.projectile.AbstractArrow".equals(key)
                    || "net.minecraft.world.entity.projectile.Arrow".equals(key)) {
                return true;
            }
        }
        return false;
    }

    private static String appendProjectileRule(String projectileList, String rule) {
        return projectileList == null || projectileList.isBlank()
                ? rule
                : projectileList + ", " + rule;
    }

    private static int clampChance(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private static String intBool(boolean value) {
        return value ? "1" : "0";
    }

    public record Snapshot(
            int gibTime,
            int gibGroundTime,
            boolean blood,
            int bloodCount,
            boolean bloodSplurt,
            boolean greenBlood,
            boolean playerGibs,
            boolean gibPushing,
            boolean headlessDeath,
            int unlistedProjectileChance,
            int fishingChance,
            String projectileList,
            boolean allowProjectileGibbing,
            String toolRules,
            boolean enchantmentsEnabled,
            boolean decapitationCamera,
            PlayerTrauma playerTrauma,
            ArmorProtection armorProtection,
            CreeperAmputation creeperAmputation,
            DeathDismemberment deathDismemberment,
            BloodSurfacePhysics bloodSurfacePhysics
    ) {
        private static Snapshot defaults() {
            return new Snapshot(
                    1000, 100, true, 20, true, false, true, true,
                    true, 100, 33, "", true, ToolRuleDocument.defaultText(), true, true,
                    PlayerTrauma.defaults(),
                    ArmorProtection.defaults(),
                    CreeperAmputation.defaults(),
                    DeathDismemberment.defaults(),
                    BloodSurfacePhysics.defaults()
            );
        }

        private static Snapshot from(Properties properties) {
            Snapshot defaults = defaults();
            String projectileList = value(properties, "projectileList", "projectile_list");
            boolean hasToolRules = properties.containsKey("toolRules") || properties.containsKey("tool_rules");
            boolean legacyToolEffect = bool(properties, "toolEffect", "tool_effect", true);
            int legacyProjectileChance = integer(
                    properties, "gibChance", "gib_chance", defaults.unlistedProjectileChance, 0, 100
            );
            int unlistedProjectileChance = integer(
                    properties,
                    "unlistedProjectileChance",
                    "unlisted_projectile_chance",
                    legacyProjectileChance,
                    0,
                    100
            );
            int fishingChance = integer(
                    properties,
                    "fishingChance",
                    "fishing_chance",
                    legacyToolEffect ? defaults.fishingChance : legacyProjectileChance,
                    0,
                    100
            );
            String toolRules = value(properties, "toolRules", "tool_rules");
            if (!hasToolRules) {
                toolRules = legacyToolEffect
                        ? ToolRuleDocument.fromLegacyChances(
                                integer(properties, "swordChance", "sword_chance", 50, 0, 100),
                                integer(properties, "axeChance", "axe_chance", 50, 0, 100),
                                integer(properties, "pickaxeChance", "pickaxe_chance", 33, 0, 100),
                                integer(properties, "shovelChance", "shovel_chance", 25, 0, 100),
                                integer(properties, "otherItemChance", "other_item_chance", 0, 0, 100)
                        )
                        : "*=" + legacyProjectileChance;
                if (!legacyToolEffect && !hasArrowRule(projectileList)) {
                    projectileList = appendProjectileRule(
                            projectileList,
                            (projectileList != null && projectileList.startsWith(",")
                                    ? "[minecraft:arrow]: "
                                    : "Arrow: ") + legacyProjectileChance
                    );
                }
            }
            return new Snapshot(
                    integer(properties, "gibTime", "gib_time", defaults.gibTime, 0, Integer.MAX_VALUE),
                    integer(properties, "gibGroundTime", "gib_ground_time", defaults.gibGroundTime, 0, Integer.MAX_VALUE),
                    bool(properties, "blood", "blood", defaults.blood),
                    integer(properties, "bloodCount", "blood_count", defaults.bloodCount, 1, 1000),
                    bool(properties, "bloodSplurt", "blood_splurt", defaults.bloodSplurt),
                    bool(properties, "greenBlood", "green_blood", defaults.greenBlood),
                    bool(properties, "playerGibs", "player_gibs", defaults.playerGibs),
                    bool(properties, "gibPushing", "gib_pushing", defaults.gibPushing),
                    bool(properties, "headlessDeath", "headless_death", defaults.headlessDeath),
                    unlistedProjectileChance,
                    fishingChance,
                    // Whitespace, duplicate order, and malformed entries can
                    // affect the projectile grammar. Keep the
                    // loaded value lossless so the guided editor can round-trip
                    // untouched developer rules byte-for-byte.
                    projectileList == null ? defaults.projectileList : projectileList,
                    bool(
                            properties,
                            "allowProjectileGibbing",
                            "allow_projectile_gibbing",
                            defaults.allowProjectileGibbing
                    ),
                    toolRules == null ? defaults.toolRules : toolRules,
                    bool(properties, "enchantmentsEnabled", "enchantments_enabled", defaults.enchantmentsEnabled),
                    bool(properties, "decapitationCamera", "decapitation_camera", defaults.decapitationCamera),
                    PlayerTrauma.from(properties),
                    ArmorProtection.from(properties),
                    CreeperAmputation.from(properties),
                    DeathDismemberment.from(properties),
                    BloodSurfacePhysics.from(properties)
            );
        }

        private Properties toProperties() {
            Properties properties = new Properties();
            properties.setProperty("gibTime", Integer.toString(gibTime));
            properties.setProperty("gibGroundTime", Integer.toString(gibGroundTime));
            properties.setProperty("blood", intBool(blood));
            properties.setProperty("bloodCount", Integer.toString(bloodCount));
            properties.setProperty("bloodSplurt", intBool(bloodSplurt));
            properties.setProperty("greenBlood", intBool(greenBlood));
            properties.setProperty("playerGibs", intBool(playerGibs));
            properties.setProperty("gibPushing", intBool(gibPushing));
            properties.setProperty("headlessDeath", intBool(headlessDeath));
            properties.setProperty("unlistedProjectileChance", Integer.toString(unlistedProjectileChance));
            properties.setProperty("fishingChance", Integer.toString(fishingChance));
            properties.setProperty("projectileList", projectileList);
            properties.setProperty("allowProjectileGibbing", intBool(allowProjectileGibbing));
            properties.setProperty("toolRules", toolRules);
            properties.setProperty("enchantmentsEnabled", intBool(enchantmentsEnabled));
            properties.setProperty("decapitationCamera", intBool(decapitationCamera));
            playerTrauma.write(properties);
            armorProtection.write(properties);
            creeperAmputation.write(properties);
            deathDismemberment.write(properties);
            bloodSurfacePhysics.write(properties);
            return properties;
        }

        public Snapshot normalized() {
            return new Snapshot(
                    Math.max(0, gibTime),
                    Math.max(0, gibGroundTime),
                    blood,
                    Math.max(1, Math.min(1000, bloodCount)),
                    bloodSplurt,
                    greenBlood,
                    playerGibs,
                    gibPushing,
                    headlessDeath,
                    clampChance(unlistedProjectileChance),
                    clampChance(fishingChance),
                    projectileList == null ? "" : projectileList,
                    allowProjectileGibbing,
                    toolRules == null ? "" : toolRules,
                    enchantmentsEnabled,
                    decapitationCamera,
                    playerTrauma == null ? PlayerTrauma.defaults() : playerTrauma.normalized(),
                    armorProtection == null ? ArmorProtection.defaults() : armorProtection.normalized(),
                    creeperAmputation == null ? CreeperAmputation.defaults() : creeperAmputation,
                    deathDismemberment == null ? DeathDismemberment.defaults() : deathDismemberment.normalized(),
                    bloodSurfacePhysics == null ? BloodSurfacePhysics.defaults() : bloodSurfacePhysics.normalized()
            );
        }

        public boolean playerArmAmputation() {
            return playerTrauma.armAmputation();
        }

        public boolean fatalPlayerDecapitation() {
            return playerTrauma.fatalDecapitation();
        }

        public boolean creeperAmputationEnabled() {
            return creeperAmputation.enabled();
        }

        public Snapshot withToolRules(String toolRules) {
            return new Snapshot(
                    gibTime, gibGroundTime, blood, bloodCount, bloodSplurt, greenBlood, playerGibs, gibPushing,
                    headlessDeath, unlistedProjectileChance, fishingChance, projectileList, allowProjectileGibbing,
                    toolRules == null ? "" : toolRules, enchantmentsEnabled, decapitationCamera, playerTrauma,
                    armorProtection, creeperAmputation, deathDismemberment, bloodSurfacePhysics
            );
        }

    }

    public record PlayerTrauma(
            boolean armAmputation,
            boolean fatalDecapitation,
            int headBleedoutMinTicks,
            int headBleedoutMaxTicks,
            int headBleedIntervalTicks,
            int headBleedDamageTenths,
            boolean armBleeding,
            int armBleedDurationTicks,
            int armBleedIntervalTicks,
            int armBleedDamageTenths,
            boolean bandagesEnabled,
            int bandageCooldownTicks
    ) {
        public static PlayerTrauma defaults() {
            return new PlayerTrauma(true, true, 40, 99, 10, 20, true, 200, 20, 10, true, 20);
        }

        private static PlayerTrauma from(Properties properties) {
            PlayerTrauma defaults = defaults();
            return new PlayerTrauma(
                    bool(properties, "playerArmAmputation", "player_arm_amputation", defaults.armAmputation),
                    bool(properties, "fatalPlayerDecapitation", "fatal_player_decapitation", defaults.fatalDecapitation),
                    integer(properties, "headBleedoutMinTicks", "head_bleedout_min_ticks", defaults.headBleedoutMinTicks, 1, 72000),
                    integer(properties, "headBleedoutMaxTicks", "head_bleedout_max_ticks", defaults.headBleedoutMaxTicks, 1, 72000),
                    integer(properties, "headBleedIntervalTicks", "head_bleed_interval_ticks", defaults.headBleedIntervalTicks, 1, 1200),
                    integer(properties, "headBleedDamageTenths", "head_bleed_damage_tenths", defaults.headBleedDamageTenths, 0, 10000),
                    bool(properties, "armBleeding", "arm_bleeding", defaults.armBleeding),
                    integer(properties, "armBleedDurationTicks", "arm_bleed_duration_ticks", defaults.armBleedDurationTicks, 1, 72000),
                    integer(properties, "armBleedIntervalTicks", "arm_bleed_interval_ticks", defaults.armBleedIntervalTicks, 1, 1200),
                    integer(properties, "armBleedDamageTenths", "arm_bleed_damage_tenths", defaults.armBleedDamageTenths, 0, 10000),
                    bool(properties, "bandagesEnabled", "bandages_enabled", defaults.bandagesEnabled),
                    integer(properties, "bandageCooldownTicks", "bandage_cooldown_ticks", defaults.bandageCooldownTicks, 0, 1200)
            ).normalized();
        }

        private void write(Properties properties) {
            properties.setProperty("playerArmAmputation", intBool(armAmputation));
            properties.setProperty("fatalPlayerDecapitation", intBool(fatalDecapitation));
            properties.setProperty("headBleedoutMinTicks", Integer.toString(headBleedoutMinTicks));
            properties.setProperty("headBleedoutMaxTicks", Integer.toString(headBleedoutMaxTicks));
            properties.setProperty("headBleedIntervalTicks", Integer.toString(headBleedIntervalTicks));
            properties.setProperty("headBleedDamageTenths", Integer.toString(headBleedDamageTenths));
            properties.setProperty("armBleeding", intBool(armBleeding));
            properties.setProperty("armBleedDurationTicks", Integer.toString(armBleedDurationTicks));
            properties.setProperty("armBleedIntervalTicks", Integer.toString(armBleedIntervalTicks));
            properties.setProperty("armBleedDamageTenths", Integer.toString(armBleedDamageTenths));
            properties.setProperty("bandagesEnabled", intBool(bandagesEnabled));
            properties.setProperty("bandageCooldownTicks", Integer.toString(bandageCooldownTicks));
        }

        public PlayerTrauma normalized() {
            int minimum = Math.max(1, headBleedoutMinTicks);
            int maximum = Math.max(minimum, headBleedoutMaxTicks);
            return new PlayerTrauma(
                    armAmputation, fatalDecapitation, minimum, maximum,
                    Math.max(1, headBleedIntervalTicks), Math.max(0, headBleedDamageTenths),
                    armBleeding, Math.max(1, armBleedDurationTicks), Math.max(1, armBleedIntervalTicks),
                    Math.max(0, armBleedDamageTenths), bandagesEnabled, Math.max(0, bandageCooldownTicks)
            );
        }
    }

    public record ArmorProtection(
            boolean enabled,
            int globalReductionPerArmorPoint,
            int coveredReductionPerArmorPoint,
            int toughnessReductionPerPoint,
            int maximumReduction,
            boolean enchantmentsEnabled,
            int reinforcementReductionPerLevel,
            boolean anatomicalIntegrityEnabled
    ) {
        public static ArmorProtection defaults() {
            return new ArmorProtection(true, 1, 5, 1, 80, true, 10, true);
        }

        private static ArmorProtection from(Properties properties) {
            ArmorProtection defaults = defaults();
            return new ArmorProtection(
                    bool(properties, "armorAmputationProtection", "armor_amputation_protection", defaults.enabled),
                    integer(properties, "armorGlobalReductionPerPoint", "armor_global_reduction_per_point", defaults.globalReductionPerArmorPoint, 0, 100),
                    integer(properties, "armorCoveredReductionPerPoint", "armor_covered_reduction_per_point", defaults.coveredReductionPerArmorPoint, 0, 100),
                    integer(properties, "armorToughnessReductionPerPoint", "armor_toughness_reduction_per_point", defaults.toughnessReductionPerPoint, 0, 100),
                    integer(properties, "armorProtectionCap", "armor_protection_cap", defaults.maximumReduction, 0, 100),
                    bool(properties, "armorProtectionEnchantments", "armor_protection_enchantments", defaults.enchantmentsEnabled),
                    integer(properties, "reinforcementReductionPerLevel", "reinforcement_reduction_per_level", defaults.reinforcementReductionPerLevel, 0, 100),
                    bool(properties, "anatomicalIntegrityEnabled", "anatomical_integrity_enabled", defaults.anatomicalIntegrityEnabled)
            ).normalized();
        }

        private void write(Properties properties) {
            properties.setProperty("armorAmputationProtection", intBool(enabled));
            properties.setProperty("armorGlobalReductionPerPoint", Integer.toString(globalReductionPerArmorPoint));
            properties.setProperty("armorCoveredReductionPerPoint", Integer.toString(coveredReductionPerArmorPoint));
            properties.setProperty("armorToughnessReductionPerPoint", Integer.toString(toughnessReductionPerPoint));
            properties.setProperty("armorProtectionCap", Integer.toString(maximumReduction));
            properties.setProperty("armorProtectionEnchantments", intBool(enchantmentsEnabled));
            properties.setProperty("reinforcementReductionPerLevel", Integer.toString(reinforcementReductionPerLevel));
            properties.setProperty("anatomicalIntegrityEnabled", intBool(anatomicalIntegrityEnabled));
        }

        public ArmorProtection normalized() {
            return new ArmorProtection(
                    enabled,
                    clampChance(globalReductionPerArmorPoint),
                    clampChance(coveredReductionPerArmorPoint),
                    clampChance(toughnessReductionPerPoint),
                    clampChance(maximumReduction),
                    enchantmentsEnabled,
                    clampChance(reinforcementReductionPerLevel),
                    anatomicalIntegrityEnabled
            );
        }
    }

    public record CreeperAmputation(boolean enabled, boolean alwaysGreenBlood) {
        public static CreeperAmputation defaults() {
            return new CreeperAmputation(true, true);
        }

        private static CreeperAmputation from(Properties properties) {
            CreeperAmputation defaults = defaults();
            return new CreeperAmputation(
                    bool(properties, "creeperAmputation", "creeper_amputation", defaults.enabled),
                    bool(properties, "creeperGreenBlood", "creeper_green_blood", defaults.alwaysGreenBlood)
            );
        }

        private void write(Properties properties) {
            properties.setProperty("creeperAmputation", intBool(enabled));
            properties.setProperty("creeperGreenBlood", intBool(alwaysGreenBlood));
        }
    }

    /** Optional client-only collision and settled-surface motion for blood. */
    public record BloodSurfacePhysics(
            boolean enabled,
            boolean dripping,
            int checkIntervalTicks,
            boolean entityCollisions
    ) {
        private static final int MINIMUM_CHECK_INTERVAL = 2;
        private static final int MAXIMUM_CHECK_INTERVAL = 40;

        public static BloodSurfacePhysics defaults() {
            return new BloodSurfacePhysics(true, true, 8, true);
        }

        private static BloodSurfacePhysics from(Properties properties) {
            BloodSurfacePhysics defaults = defaults();
            return new BloodSurfacePhysics(
                    bool(properties, "dynamicBloodSurfaces", "dynamic_blood_surfaces", defaults.enabled),
                    bool(properties, "bloodSurfaceDripping", "blood_surface_dripping", defaults.dripping),
                    integer(properties, "bloodSurfaceCheckIntervalTicks", "blood_surface_check_interval_ticks",
                            defaults.checkIntervalTicks, MINIMUM_CHECK_INTERVAL, MAXIMUM_CHECK_INTERVAL),
                    bool(properties, "bloodEntityCollisions", "blood_entity_collisions", defaults.entityCollisions)
            );
        }

        private void write(Properties properties) {
            properties.setProperty("dynamicBloodSurfaces", intBool(enabled));
            properties.setProperty("bloodSurfaceDripping", intBool(dripping));
            properties.setProperty("bloodSurfaceCheckIntervalTicks", Integer.toString(checkIntervalTicks));
            properties.setProperty("bloodEntityCollisions", intBool(entityCollisions));
        }

        public BloodSurfacePhysics normalized() {
            return new BloodSurfacePhysics(
                    enabled,
                    dripping,
                    Math.max(MINIMUM_CHECK_INTERVAL, Math.min(MAXIMUM_CHECK_INTERVAL, checkIntervalTicks)),
                    entityCollisions
            );
        }
    }

    public enum DeathDismembermentMode {
        DISABLED,
        TRIGGERED,
        ALL;

        private static DeathDismembermentMode parse(String value) {
            if (value == null) {
                return TRIGGERED;
            }
            try {
                return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return TRIGGERED;
            }
        }
    }

    public record DeathDismemberment(
            DeathDismembermentMode mode,
            boolean explosions,
            boolean ironGolems,
            int gibTime,
            int gibGroundTime,
            boolean blood,
            int bloodCount,
            boolean greenBlood,
            boolean gibPushing
    ) {
        public static DeathDismemberment defaults() {
            return new DeathDismemberment(
                    DeathDismembermentMode.TRIGGERED,
                    true,
                    true,
                    1000,
                    100,
                    true,
                    100,
                    false,
                    true
            );
        }

        private static DeathDismemberment from(Properties properties) {
            DeathDismemberment defaults = defaults();
            return new DeathDismemberment(
                    DeathDismembermentMode.parse(value(properties, "deathDismembermentMode", "death_dismemberment_mode")),
                    bool(properties, "deathDismembermentExplosions", "death_dismemberment_explosions", defaults.explosions),
                    bool(properties, "deathDismembermentIronGolems", "death_dismemberment_iron_golems", defaults.ironGolems),
                    integer(properties, "deathDismembermentGibTime", "death_dismemberment_gib_time",
                            defaults.gibTime, 0, Integer.MAX_VALUE),
                    integer(properties, "deathDismembermentGibGroundTime", "death_dismemberment_gib_ground_time",
                            defaults.gibGroundTime, 0, Integer.MAX_VALUE),
                    bool(properties, "deathDismembermentBlood", "death_dismemberment_blood", defaults.blood),
                    integer(properties, "deathDismembermentBloodCount", "death_dismemberment_blood_count",
                            defaults.bloodCount, 1, 1000),
                    bool(properties, "deathDismembermentGreenBlood", "death_dismemberment_green_blood",
                            defaults.greenBlood),
                    bool(properties, "deathDismembermentGibPushing", "death_dismemberment_gib_pushing",
                            defaults.gibPushing)
            ).normalized();
        }

        private void write(Properties properties) {
            properties.setProperty("deathDismembermentMode", mode.name().toLowerCase(java.util.Locale.ROOT));
            properties.setProperty("deathDismembermentExplosions", intBool(explosions));
            properties.setProperty("deathDismembermentIronGolems", intBool(ironGolems));
            properties.setProperty("deathDismembermentGibTime", Integer.toString(gibTime));
            properties.setProperty("deathDismembermentGibGroundTime", Integer.toString(gibGroundTime));
            properties.setProperty("deathDismembermentBlood", intBool(blood));
            properties.setProperty("deathDismembermentBloodCount", Integer.toString(bloodCount));
            properties.setProperty("deathDismembermentGreenBlood", intBool(greenBlood));
            properties.setProperty("deathDismembermentGibPushing", intBool(gibPushing));
        }

        public DeathDismemberment normalized() {
            return new DeathDismemberment(
                    mode == null ? DeathDismembermentMode.TRIGGERED : mode,
                    explosions,
                    ironGolems,
                    Math.max(0, gibTime),
                    Math.max(0, gibGroundTime),
                    blood,
                    Math.max(1, Math.min(1000, bloodCount)),
                    greenBlood,
                    gibPushing
            );
        }

        public DeathDismemberment withMode(DeathDismembermentMode value) {
            return new DeathDismemberment(value, explosions, ironGolems, gibTime, gibGroundTime,
                    blood, bloodCount, greenBlood, gibPushing);
        }

        public DeathDismemberment withTriggers(boolean explosionValue, boolean ironGolemValue) {
            return new DeathDismemberment(mode, explosionValue, ironGolemValue, gibTime, gibGroundTime,
                    blood, bloodCount, greenBlood, gibPushing);
        }

        public DeathDismemberment withClientSettings(
                int lifetime,
                int groundedLifetime,
                boolean bloodValue,
                int bloodAmount,
                boolean greenBloodValue,
                boolean pushing
        ) {
            return new DeathDismemberment(mode, explosions, ironGolems, lifetime, groundedLifetime,
                    bloodValue, bloodAmount, greenBloodValue, pushing);
        }
    }

    private MobAmputationConfig() {
    }
}
