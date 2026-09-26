package io.github.avacadowizard120.mobamputation.network;

import io.github.avacadowizard120.mobamputation.api.Limb;
import io.github.avacadowizard120.mobamputation.client.GibManager;
import io.github.avacadowizard120.mobamputation.config.MobAmputationConfig;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;

//? if fabric || quilt {
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
//?}
//? if forge {
/*import net.minecraftforge.network.PacketDistributor;
*///?}
//? if neoforge {
/*import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
*///?}

/** Physical-client half of the optional network bridge. */
public final class MobAmputationClientNetworking
        //? if fabric || quilt {
        implements ClientModInitializer
        //?}
{
    private static volatile boolean serverHasMod;
    private static final AtomicInteger NEXT_REQUEST_ID = new AtomicInteger(1);

    //? if fabric || quilt {
    @Override
    //?}
    public void onInitializeClient() {
        //? if fabric || quilt {
        ClientPlayNetworking.registerGlobalReceiver(DetachNoticePayload.TYPE,
                (payload, context) -> handleNotice(payload));
        ClientPlayNetworking.registerGlobalReceiver(DetachResultPayload.TYPE,
                (payload, context) -> handleResult(payload));
        ClientPlayNetworking.registerGlobalReceiver(ServerHelloPayload.TYPE,
                (payload, context) -> handleHello(payload));
        ClientPlayNetworking.registerGlobalReceiver(PlayerAmputationStatePayload.TYPE,
                (payload, context) -> handlePlayerState(payload));
        ClientPlayConnectionEvents.INIT.register((handler, client) -> GibManager.clear());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> GibManager.clear());
        //?}
    }

    public static boolean serverHasMod() {
        return serverHasMod && canSendToServer();
    }

    public static void handleHello(ServerHelloPayload payload) {
        serverHasMod = true;
        MobAmputationConfig.applySession(
                payload.headlessDeath(),
                payload.unlistedProjectileChance(),
                payload.fishingChance(),
                payload.projectileList(),
                payload.allowProjectileGibbing(),
                payload.toolRules(),
                payload.enchantmentsEnabled(),
                payload.playerGibs(),
                payload.playerTrauma(),
                payload.armorProtection(),
                payload.creeperAmputation()
        );
    }

    public static void resetSession() {
        serverHasMod = false;
        MobAmputationConfig.clearSession();
    }

    public static void handleNotice(DetachNoticePayload payload) {
        if (!serverHasMod()) {
            return;
        }
        if (payload.limb() >= 0 && payload.limb() < Limb.values().length) {
            GibManager.detachRemote(payload.parentId(), Limb.values()[payload.limb()]);
        }
    }

    public static void handleResult(DetachResultPayload payload) {
        if (!serverHasMod()) {
            return;
        }
        GibManager.resolveServerDetach(payload);
    }

    public static void handlePlayerState(PlayerAmputationStatePayload payload) {
        if (!serverHasMod()) {
            return;
        }
        GibManager.applyEntityState(
                payload.entityId(), payload.entityUuid(), payload.limbMask(), payload.bleedingLimbMask()
        );
    }

    static int sendRequest(int parentId, Limb limb, DetachCause cause, int sourceEntityId) {
        if (!serverHasMod()) {
            return 0;
        }
        int requestId = NEXT_REQUEST_ID.getAndUpdate(current -> current == Integer.MAX_VALUE ? 1 : current + 1);
        DetachRequestPayload payload = new DetachRequestPayload(
                requestId,
                parentId,
                (byte) limb.ordinal(),
                (byte) cause.ordinal(),
                sourceEntityId
        );
        //? if fabric || quilt {
        ClientPlayNetworking.send(payload);
        //?}
        //? if forge {
        /*MobAmputationNetworking.forgeChannel().send(payload, PacketDistributor.SERVER.noArg());
        *///?}
        //? if neoforge {
        /*PacketDistributor.sendToServer(payload);
        *///?}
        return requestId;
    }

    private static boolean canSendToServer() {
        //? if fabric || quilt {
        return ClientPlayNetworking.canSend(DetachRequestPayload.TYPE);
        //?}
        //? if forge {
        /*var connection = Minecraft.getInstance().getConnection();
        return connection != null
                && MobAmputationNetworking.forgeChannel() != null
                && MobAmputationNetworking.forgeChannel().isRemotePresent(connection.getConnection());
        *///?}
        //? if neoforge {
        /*var connection = Minecraft.getInstance().getConnection();
        return connection != null
                && NetworkRegistry.hasChannel(connection.getConnection(), null, DetachRequestPayload.TYPE.id());
        *///?}
    }

    /** Public because Fabric and Quilt instantiate client entrypoints reflectively. */
    public MobAmputationClientNetworking() {
    }
}
