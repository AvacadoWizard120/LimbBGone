package io.github.avacadowizard120.mobamputation.network;

import io.github.avacadowizard120.mobamputation.MobAmputation;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server-to-client notice used to mirror player-caused amputations. */
public record DetachNoticePayload(int parentId, byte limb) implements CustomPacketPayload {
    public static final Type<DetachNoticePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MobAmputation.MOD_ID, "detach_notice_v6")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, DetachNoticePayload> CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeInt(payload.parentId());
                buffer.writeByte(payload.limb());
            },
            buffer -> new DetachNoticePayload(buffer.readInt(), buffer.readByte())
    );

    @Override
    public Type<DetachNoticePayload> type() {
        return TYPE;
    }
}
