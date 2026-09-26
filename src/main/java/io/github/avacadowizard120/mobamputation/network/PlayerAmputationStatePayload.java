package io.github.avacadowizard120.mobamputation.network;

import io.github.avacadowizard120.mobamputation.MobAmputation;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Transient authoritative state used for late tracking and reconnects. */
public record PlayerAmputationStatePayload(int entityId, java.util.UUID entityUuid, int limbMask, int bleedingLimbMask)
        implements CustomPacketPayload {
    public static final Type<PlayerAmputationStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MobAmputation.MOD_ID, "player_state_v6")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerAmputationStatePayload> CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarInt(payload.entityId());
                buffer.writeUUID(payload.entityUuid());
                buffer.writeVarInt(payload.limbMask());
                buffer.writeVarInt(payload.bleedingLimbMask());
            },
            buffer -> new PlayerAmputationStatePayload(
                    buffer.readVarInt(), buffer.readUUID(), buffer.readVarInt(), buffer.readVarInt()
            )
    );

    @Override
    public Type<PlayerAmputationStatePayload> type() {
        return TYPE;
    }
}
