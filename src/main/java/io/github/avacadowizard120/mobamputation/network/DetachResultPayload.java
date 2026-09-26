package io.github.avacadowizard120.mobamputation.network;

import io.github.avacadowizard120.mobamputation.MobAmputation;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server decision for one client-side sever candidate. */
public record DetachResultPayload(int requestId, int parentId, byte limb, boolean accepted)
        implements CustomPacketPayload {
    public static final Type<DetachResultPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MobAmputation.MOD_ID, "detach_result_v6")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, DetachResultPayload> CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarInt(payload.requestId());
                buffer.writeInt(payload.parentId());
                buffer.writeByte(payload.limb());
                buffer.writeBoolean(payload.accepted());
            },
            buffer -> new DetachResultPayload(
                    buffer.readVarInt(),
                    buffer.readInt(),
                    buffer.readByte(),
                    buffer.readBoolean()
            )
    );

    @Override
    public Type<DetachResultPayload> type() {
        return TYPE;
    }
}
