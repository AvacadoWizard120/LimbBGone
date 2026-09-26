package io.github.avacadowizard120.mobamputation.network;

import io.github.avacadowizard120.mobamputation.MobAmputation;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client-to-server sever candidate with server-verifiable evidence identifiers. */
public record DetachRequestPayload(int requestId, int parentId, byte limb, byte cause, int sourceEntityId)
        implements CustomPacketPayload {
    public static final Type<DetachRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MobAmputation.MOD_ID, "detach_request_v6")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, DetachRequestPayload> CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarInt(payload.requestId());
                buffer.writeInt(payload.parentId());
                buffer.writeByte(payload.limb());
                buffer.writeByte(payload.cause());
                buffer.writeInt(payload.sourceEntityId());
            },
            buffer -> new DetachRequestPayload(
                    buffer.readVarInt(),
                    buffer.readInt(),
                    buffer.readByte(),
                    buffer.readByte(),
                    buffer.readInt()
            )
    );

    @Override
    public Type<DetachRequestPayload> type() {
        return TYPE;
    }
}
