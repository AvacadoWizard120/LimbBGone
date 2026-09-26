package io.github.avacadowizard120.mobamputation.network;

import io.github.avacadowizard120.mobamputation.MobAmputation;
import io.github.avacadowizard120.mobamputation.config.MobAmputationConfig;
import io.github.avacadowizard120.mobamputation.config.RuleTextLimits;
import io.github.avacadowizard120.mobamputation.config.MobAmputationConfig.ArmorProtection;
import io.github.avacadowizard120.mobamputation.config.MobAmputationConfig.CreeperAmputation;
import io.github.avacadowizard120.mobamputation.config.MobAmputationConfig.PlayerTrauma;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Optional presence handshake plus server-owned session settings. */
public record ServerHelloPayload(
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
) implements CustomPacketPayload {
    private static final int MAX_PROJECTILE_RULE_CHARS = RuleTextLimits.MAX_CHARS;
    private static final int MAX_TOOL_RULE_CHARS = RuleTextLimits.MAX_CHARS;
    public static final Type<ServerHelloPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MobAmputation.MOD_ID, "server_hello_v6")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerHelloPayload> CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeBoolean(payload.headlessDeath());
                buffer.writeVarInt(payload.unlistedProjectileChance());
                buffer.writeVarInt(payload.fishingChance());
                buffer.writeUtf(payload.projectileList(), MAX_PROJECTILE_RULE_CHARS);
                buffer.writeBoolean(payload.allowProjectileGibbing());
                buffer.writeUtf(payload.toolRules(), MAX_TOOL_RULE_CHARS);
                buffer.writeBoolean(payload.enchantmentsEnabled());
                buffer.writeBoolean(payload.playerGibs());
                PlayerTrauma trauma = payload.playerTrauma();
                buffer.writeBoolean(trauma.armAmputation());
                buffer.writeBoolean(trauma.fatalDecapitation());
                buffer.writeVarInt(trauma.headBleedoutMinTicks());
                buffer.writeVarInt(trauma.headBleedoutMaxTicks());
                buffer.writeVarInt(trauma.headBleedIntervalTicks());
                buffer.writeVarInt(trauma.headBleedDamageTenths());
                buffer.writeBoolean(trauma.armBleeding());
                buffer.writeVarInt(trauma.armBleedDurationTicks());
                buffer.writeVarInt(trauma.armBleedIntervalTicks());
                buffer.writeVarInt(trauma.armBleedDamageTenths());
                buffer.writeBoolean(trauma.bandagesEnabled());
                buffer.writeVarInt(trauma.bandageCooldownTicks());
                ArmorProtection armor = payload.armorProtection();
                buffer.writeBoolean(armor.enabled());
                buffer.writeVarInt(armor.globalReductionPerArmorPoint());
                buffer.writeVarInt(armor.coveredReductionPerArmorPoint());
                buffer.writeVarInt(armor.toughnessReductionPerPoint());
                buffer.writeVarInt(armor.maximumReduction());
                buffer.writeBoolean(armor.enchantmentsEnabled());
                buffer.writeVarInt(armor.reinforcementReductionPerLevel());
                buffer.writeBoolean(armor.anatomicalIntegrityEnabled());
                buffer.writeBoolean(payload.creeperAmputation().enabled());
                buffer.writeBoolean(payload.creeperAmputation().alwaysGreenBlood());
            },
            buffer -> new ServerHelloPayload(
                    buffer.readBoolean(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readUtf(MAX_PROJECTILE_RULE_CHARS),
                    buffer.readBoolean(),
                    buffer.readUtf(MAX_TOOL_RULE_CHARS),
                    buffer.readBoolean(),
                    buffer.readBoolean(),
                    new PlayerTrauma(
                            buffer.readBoolean(), buffer.readBoolean(), buffer.readVarInt(), buffer.readVarInt(),
                            buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean(), buffer.readVarInt(),
                            buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean(), buffer.readVarInt()
                    ),
                    new ArmorProtection(
                            buffer.readBoolean(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                            buffer.readVarInt(), buffer.readBoolean(), buffer.readVarInt(), buffer.readBoolean()
                    ),
                    new CreeperAmputation(buffer.readBoolean(), buffer.readBoolean())
            )
    );

    public static ServerHelloPayload currentServerConfig() {
        MobAmputationConfig.Snapshot config = MobAmputationConfig.get();
        String configuredProjectileRules = config.projectileList();
        String rules = RuleTextLimits.projectileRules(configuredProjectileRules);
        if (!rules.equals(configuredProjectileRules)) {
            System.err.println("[Mob Amputation] projectileList is " + configuredProjectileRules.length()
                    + " characters; the server session payload supports " + MAX_PROJECTILE_RULE_CHARS
                    + ". Only complete rules through character " + rules.length()
                    + " are active and were transmitted.");
        }
        String toolRules = boundedToolRules(config.toolRules());
        return new ServerHelloPayload(
                config.headlessDeath(),
                config.unlistedProjectileChance(),
                config.fishingChance(),
                rules,
                config.allowProjectileGibbing(),
                toolRules,
                config.enchantmentsEnabled(),
                config.playerGibs(),
                config.playerTrauma(),
                config.armorProtection(),
                config.creeperAmputation()
        );
    }

    private static String boundedToolRules(String configured) {
        String rules = configured == null ? "" : configured;
        String bounded = RuleTextLimits.toolRules(rules);
        if (!bounded.equals(rules)) {
            System.err.println("[Mob Amputation] toolRules is " + rules.length()
                    + " characters; the server session payload supports " + MAX_TOOL_RULE_CHARS
                    + ". Only complete rules through character " + bounded.length()
                    + " are active and were transmitted.");
        }
        return bounded;
    }

    @Override
    public Type<ServerHelloPayload> type() {
        return TYPE;
    }
}
