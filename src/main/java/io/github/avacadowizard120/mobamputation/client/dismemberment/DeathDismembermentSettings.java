package io.github.avacadowizard120.mobamputation.client.dismemberment;

import io.github.avacadowizard120.mobamputation.config.MobAmputationConfig;

/**
 * Temporary client settings facade for the death-dismemberment subsystem.
 *
 * <p>The config/UI layer can copy its values into this class without making
 * the client implementation depend on config methods that do not exist yet.</p>
 */
public final class DeathDismembermentSettings {
    public enum TriggerMode {
        DISABLED,
        TRIGGERED,
        ALL
    }

    private static volatile TriggerMode triggerMode = TriggerMode.TRIGGERED;
    private static volatile boolean gibPushing = true;
    private static volatile int gibGroundTime = 100;
    private static volatile int gibLifetime = 1000;
    private static volatile boolean zombieBlood = true;
    private static volatile int zombieBloodCount = 100;

    public static TriggerMode triggerMode() {
        return switch (MobAmputationConfig.get().deathDismemberment().mode()) {
            case DISABLED -> TriggerMode.DISABLED;
            case TRIGGERED -> TriggerMode.TRIGGERED;
            case ALL -> TriggerMode.ALL;
        };
    }

    public static void setTriggerMode(TriggerMode value) {
        triggerMode = value == null ? TriggerMode.TRIGGERED : value;
    }

    public static boolean gibPushing() {
        return MobAmputationConfig.get().deathDismemberment().gibPushing();
    }

    public static void setGibPushing(boolean value) {
        gibPushing = value;
    }

    public static int gibGroundTime() {
        return MobAmputationConfig.get().deathDismemberment().gibGroundTime();
    }

    public static void setGibGroundTime(int value) {
        gibGroundTime = Math.max(0, value);
    }

    public static int gibLifetime() {
        return MobAmputationConfig.get().deathDismemberment().gibTime();
    }

    public static void setGibLifetime(int value) {
        gibLifetime = Math.max(1, value);
    }

    public static boolean zombieBlood() {
        return MobAmputationConfig.get().deathDismemberment().blood();
    }

    public static void setZombieBlood(boolean value) {
        zombieBlood = value;
    }

    public static int zombieBloodCount() {
        return MobAmputationConfig.get().deathDismemberment().bloodCount();
    }

    public static void setZombieBloodCount(int value) {
        zombieBloodCount = Math.max(0, value);
    }

    public static boolean greenBlood() {
        return MobAmputationConfig.get().deathDismemberment().greenBlood();
    }

    public static void resetDefaults() {
        triggerMode = TriggerMode.TRIGGERED;
        gibPushing = true;
        gibGroundTime = 100;
        gibLifetime = 1000;
        zombieBlood = true;
        zombieBloodCount = 100;
    }

    private DeathDismembermentSettings() {
    }
}
