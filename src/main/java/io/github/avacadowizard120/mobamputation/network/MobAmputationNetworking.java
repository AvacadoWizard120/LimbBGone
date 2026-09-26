package io.github.avacadowizard120.mobamputation.network;

import io.github.avacadowizard120.mobamputation.api.Limb;
import io.github.avacadowizard120.mobamputation.logic.AmputationLogic;
import io.github.avacadowizard120.mobamputation.logic.ServerAmputationAuthority;
import io.github.avacadowizard120.mobamputation.state.PlayerAmputationAccess;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

//? if fabric || quilt {
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
//?}
//? if forge {
/*import net.minecraft.network.protocol.PacketFlow;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.PacketDistributor;
*///?}
//? if neoforge {
/*import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
*///?}

/** Loader bridge for the optional client-to-server detach message. */
public final class MobAmputationNetworking {
    private static final int PROTOCOL_VERSION = 6;

    //? if forge {
    /*private static Channel<net.minecraft.network.protocol.common.custom.CustomPacketPayload> channel;
    *///?}

    public static void initializeCommon() {
        ServerAmputationAuthority.setAutomaticResolutionListener(
                MobAmputationNetworking::handleAutomaticResolution
        );
        //? if fabric || quilt {
        PayloadTypeRegistry.playC2S().register(DetachRequestPayload.TYPE, DetachRequestPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DetachNoticePayload.TYPE, DetachNoticePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DetachResultPayload.TYPE, DetachResultPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ServerHelloPayload.TYPE, ServerHelloPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PlayerAmputationStatePayload.TYPE, PlayerAmputationStatePayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(DetachRequestPayload.TYPE,
                (payload, context) -> handleRequest(context.player(), payload));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (ServerPlayNetworking.canSend(handler, ServerHelloPayload.TYPE)) {
                ServerPlayNetworking.send(handler.getPlayer(), ServerHelloPayload.currentServerConfig());
                sendPlayerState(handler.getPlayer(), handler.getPlayer());
            }
        });
        //?}
    }

    //? if forge {
    /*public static void initializeForge() {
        channel = ChannelBuilder
                .named("mobamputation:network")
                .networkProtocolVersion(PROTOCOL_VERSION)
                .optional()
                .payloadChannel()
                .play()
                    .serverbound()
                        .addMain(DetachRequestPayload.TYPE, DetachRequestPayload.CODEC,
                                (payload, context) -> handleRequest(context.getSender(), payload))
                .play()
                    .clientbound()
                        .addMain(DetachNoticePayload.TYPE, DetachNoticePayload.CODEC,
                                (payload, context) -> dispatchClientNotice(payload))
                        .addMain(DetachResultPayload.TYPE, DetachResultPayload.CODEC,
                                (payload, context) -> dispatchClientResult(payload))
                        .addMain(ServerHelloPayload.TYPE, ServerHelloPayload.CODEC,
                                (payload, context) -> dispatchClientHello(payload))
                        .addMain(PlayerAmputationStatePayload.TYPE, PlayerAmputationStatePayload.CODEC,
                                (payload, context) -> dispatchClientPlayerState(payload))
                .build();
        MinecraftForge.EVENT_BUS.addListener(MobAmputationNetworking::onForgePlayerLogin);
    }

    private static void onForgePlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && channel.isRemotePresent(player.connection.getConnection())) {
            channel.send(ServerHelloPayload.currentServerConfig(), PacketDistributor.PLAYER.with(player));
            sendPlayerState(player, player);
        }
    }
    *///?}

    //? if neoforge {
    /*public static void initializeNeoForge(IEventBus modBus) {
        modBus.addListener(MobAmputationNetworking::registerNeoForgePayloads);
        NeoForge.EVENT_BUS.addListener(MobAmputationNetworking::onNeoForgePlayerLogin);
    }

    private static void registerNeoForgePayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(Integer.toString(PROTOCOL_VERSION)).optional();
        registrar.playToServer(DetachRequestPayload.TYPE, DetachRequestPayload.CODEC,
                (payload, context) -> handleRequest((ServerPlayer) context.player(), payload));
        registrar.playToClient(DetachNoticePayload.TYPE, DetachNoticePayload.CODEC,
                (payload, context) -> dispatchClientNotice(payload));
        registrar.playToClient(DetachResultPayload.TYPE, DetachResultPayload.CODEC,
                (payload, context) -> dispatchClientResult(payload));
        registrar.playToClient(ServerHelloPayload.TYPE, ServerHelloPayload.CODEC,
                (payload, context) -> dispatchClientHello(payload));
        registrar.playToClient(PlayerAmputationStatePayload.TYPE, PlayerAmputationStatePayload.CODEC,
                (payload, context) -> dispatchClientPlayerState(payload));
    }

    private static void onNeoForgePlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && player.connection.hasChannel(ServerHelloPayload.TYPE)) {
            PacketDistributor.sendToPlayer(player, ServerHelloPayload.currentServerConfig());
            sendPlayerState(player, player);
        }
    }
    *///?}

    /** Called by the client proxy when a local gib finishes detaching. */
    public static int sendDetachRequest(int parentId, Limb limb, DetachCause cause, int sourceEntityId) {
        return MobAmputationClientNetworking.sendRequest(parentId, limb, cause, sourceEntityId);
    }

    public static boolean serverHasMod() {
        return MobAmputationClientNetworking.serverHasMod();
    }

    /** True only when this player negotiated the trauma/state protocol. */
    public static boolean supportsClient(ServerPlayer player) {
        //? if fabric || quilt {
        return player != null
                && ServerPlayNetworking.canSend(player, ServerHelloPayload.TYPE);
        //?}
        //? if forge {
        /*return player != null && channel != null
                && channel.isRemotePresent(player.connection.getConnection());
        *///?}
        //? if neoforge {
        /*return player != null
                && player.connection.hasChannel(ServerHelloPayload.TYPE);
        *///?}
    }

    public static void dispatchClientNotice(DetachNoticePayload payload) {
        MobAmputationClientNetworking.handleNotice(payload);
    }

    public static void dispatchClientResult(DetachResultPayload payload) {
        MobAmputationClientNetworking.handleResult(payload);
    }

    public static void dispatchClientHello(ServerHelloPayload payload) {
        MobAmputationClientNetworking.handleHello(payload);
    }

    public static void dispatchClientPlayerState(PlayerAmputationStatePayload payload) {
        MobAmputationClientNetworking.handlePlayerState(payload);
    }

    public static void sendPlayerState(ServerPlayer viewer, Entity entity) {
        PlayerAmputationStatePayload payload = currentEntityState(entity);
        if (payload == null) {
            return;
        }
        sendPlayerStatePayload(viewer, payload);
    }

    /**
     * Revokes every cached wound for this player after a full body restoration.
     * This exceptional zero snapshot is deliberately sent to every compatible
     * connected client, not just current trackers: a death can move the player
     * to another dimension or respawn location after clients cached the old
     * same-UUID body. Normal live-state traffic remains tracker-scoped.
     *
     * <p>The zero must not pass through {@link #currentEntityState(Entity)}
     * because an intact player can be omitted when player amputations are
     * disabled after the original wound.</p>
     */
    public static void sendIntactPlayerStateToAllClients(ServerPlayer player) {
        PlayerAmputationStatePayload payload = new PlayerAmputationStatePayload(
                player.getId(), player.getUUID(), 0, 0
        );
        for (ServerPlayer viewer : player.server.getPlayerList().getPlayers()) {
            sendPlayerStatePayload(viewer, payload);
        }
    }

    private static PlayerAmputationStatePayload currentEntityState(Entity entity) {
        if (!(entity instanceof LivingEntity living)) {
            return null;
        }
        int mask = ServerAmputationAuthority.detachedMask(living);
        if (mask == 0 && !AmputationLogic.eligible(living)) {
            return null;
        }
        int bleedingMask = 0;
        if (living instanceof Player) {
            PlayerAmputationAccess state = (PlayerAmputationAccess) living;
            if (state.mobamputation$getFatalHeadTicks() > 0) {
                bleedingMask |= Limb.HEAD.bit();
            }
            for (Limb limb : new Limb[] { Limb.LEFT_ARM, Limb.RIGHT_ARM }) {
                if (state.mobamputation$getBleedTicks(limb) > 0) {
                    bleedingMask |= limb.bit();
                }
            }
        }
        return new PlayerAmputationStatePayload(
                living.getId(), living.getUUID(), mask, bleedingMask
        );
    }

    private static void sendPlayerStatePayload(
            ServerPlayer viewer,
            PlayerAmputationStatePayload payload
    ) {
        //? if fabric || quilt {
        if (supportsClient(viewer)
                && ServerPlayNetworking.canSend(viewer, PlayerAmputationStatePayload.TYPE)) {
            ServerPlayNetworking.send(viewer, payload);
        }
        //?}
        //? if forge {
        /*if (channel != null && channel.isRemotePresent(viewer.connection.getConnection())) {
            channel.send(payload, PacketDistributor.PLAYER.with(viewer));
        }
        *///?}
        //? if neoforge {
        /*if (supportsClient(viewer)
                && viewer.connection.hasChannel(PlayerAmputationStatePayload.TYPE)) {
            PacketDistributor.sendToPlayer(viewer, payload);
        }
        *///?}
    }

    private static void handleRequest(ServerPlayer sender, DetachRequestPayload payload) {
        if (!supportsClient(sender)) {
            return;
        }
        ServerAmputationAuthority.handle(sender, payload, resolution -> {
            LivingEntity target = resolution.target();
            sendResultToPlayer(sender, new DetachResultPayload(
                    payload.requestId(), target.getId(), payload.limb(), resolution.accepted()
            ));
            if (!resolution.accepted()) {
                return;
            }

            DetachNoticePayload notice = new DetachNoticePayload(target.getId(), payload.limb());
            if (resolution.cause() == DetachCause.MELEE) {
                // The requester already received its cause-preserving result.
                // Every other compatible client gets the original remote
                // notification and default 15/15 tumble.
                sendNotice(target, notice, sender);
            } else if (target instanceof ServerPlayer targetPlayer && targetPlayer != sender) {
                // Keep projectile/fishing events private as upstream intended,
                // but let the victim detach their local camera head.
                sendNoticeToPlayer(targetPlayer, notice);
            }

            // A live detach event must arrive before its durable snapshot. If
            // the snapshot wins the race, clients silently mark the limb as
            // already gone and suppress the launch, blood burst and camera
            // transition carried by the notice/result above.
            // Every current compatible observer needs the durable mask, even when
            // projectile/fishing launch animation remains requester-private
            // for upstream fidelity. Otherwise an already-severed mob looks
            // intact to current trackers and its death split duplicates the
            // missing limb. Future observers receive the same snapshot from
            // ServerEntityMixin when vanilla starts pairing the entity.
            sendStateToCurrentTrackers(target, sender);
        });
    }

    private static void handleAutomaticResolution(ServerAmputationAuthority.Resolution resolution) {
        if (!resolution.accepted()) {
            return;
        }
        LivingEntity target = resolution.target();
        DetachNoticePayload notice = new DetachNoticePayload(
                target.getId(), (byte) resolution.limb().ordinal()
        );
        // There is no requesting client for a mob/environment-owned
        // projectile, so every compatible observer receives the live event.
        sendNotice(target, notice, null);
        sendStateToCurrentTrackers(target, null);
    }

    private static void sendStateToCurrentTrackers(
            LivingEntity target,
            ServerPlayer requester
    ) {
        PlayerAmputationStatePayload payload = currentEntityState(target);
        if (payload == null) {
            return;
        }
        sendStatePayloadToCurrentTrackers(target, requester, payload);
    }

    private static void sendStatePayloadToCurrentTrackers(
            LivingEntity target,
            ServerPlayer requester,
            PlayerAmputationStatePayload payload
    ) {

        //? if fabric || quilt {
        Set<ServerPlayer> viewers = new LinkedHashSet<>(PlayerLookup.tracking(target));
        if (requester != null) {
            viewers.add(requester);
        }
        if (target instanceof ServerPlayer targetPlayer) {
            viewers.add(targetPlayer);
        }
        for (ServerPlayer viewer : viewers) {
            sendPlayerStatePayload(viewer, payload);
        }
        //?}
        //? if forge {
        /*if (channel != null) {
            Set<ServerPlayer> viewers = new LinkedHashSet<>(((net.minecraft.server.level.ServerLevel) target.level())
                    .getChunkSource().chunkMap.getPlayers(target.chunkPosition(), false));
            if (requester != null) {
                viewers.add(requester);
            }
            if (target instanceof ServerPlayer targetPlayer) {
                viewers.add(targetPlayer);
            }
            for (ServerPlayer viewer : viewers) {
                sendPlayerStatePayload(viewer, payload);
            }
        }
        *///?}
        //? if neoforge {
        /*Set<ServerPlayer> viewers = new LinkedHashSet<>(((net.minecraft.server.level.ServerLevel) target.level())
                .getChunkSource().chunkMap.getPlayers(target.chunkPosition(), false));
        if (requester != null) {
            viewers.add(requester);
        }
        if (target instanceof ServerPlayer targetPlayer) {
            viewers.add(targetPlayer);
        }
        for (ServerPlayer viewer : viewers) {
            sendPlayerStatePayload(viewer, payload);
        }
        *///?}
    }

    private static void sendResultToPlayer(ServerPlayer player, DetachResultPayload payload) {
        //? if fabric || quilt {
        if (supportsClient(player) && ServerPlayNetworking.canSend(player, DetachResultPayload.TYPE)) {
            ServerPlayNetworking.send(player, payload);
        }
        //?}
        //? if forge {
        /*if (channel != null && channel.isRemotePresent(player.connection.getConnection())) {
            channel.send(payload, PacketDistributor.PLAYER.with(player));
        }
        *///?}
        //? if neoforge {
        /*if (supportsClient(player) && player.connection.hasChannel(DetachResultPayload.TYPE)) {
            PacketDistributor.sendToPlayer(player, payload);
        }
        *///?}
    }

    private static void sendNoticeToPlayer(ServerPlayer player, DetachNoticePayload payload) {
        //? if fabric || quilt {
        if (supportsClient(player) && ServerPlayNetworking.canSend(player, DetachNoticePayload.TYPE)) {
            ServerPlayNetworking.send(player, payload);
        }
        //?}
        //? if forge {
        /*if (channel != null && channel.isRemotePresent(player.connection.getConnection())) {
            channel.send(payload, PacketDistributor.PLAYER.with(player));
        }
        *///?}
        //? if neoforge {
        /*if (supportsClient(player) && player.connection.hasChannel(DetachNoticePayload.TYPE)) {
            PacketDistributor.sendToPlayer(player, payload);
        }
        *///?}
    }

    private static void sendNotice(
            LivingEntity parent,
            DetachNoticePayload payload,
            ServerPlayer excluded
    ) {
        //? if fabric || quilt {
        Set<ServerPlayer> viewers = new LinkedHashSet<>(PlayerLookup.tracking(parent));
        if (parent instanceof ServerPlayer targetPlayer) {
            viewers.add(targetPlayer);
        }
        for (ServerPlayer player : viewers) {
            if (player != excluded
                    && supportsClient(player)
                    && ServerPlayNetworking.canSend(player, DetachNoticePayload.TYPE)) {
                ServerPlayNetworking.send(player, payload);
            }
        }
        //?}
        //? if forge {
        /*if (channel != null) {
            Set<ServerPlayer> viewers = new LinkedHashSet<>(((net.minecraft.server.level.ServerLevel) parent.level())
                    .getChunkSource().chunkMap.getPlayers(parent.chunkPosition(), false));
            if (parent instanceof ServerPlayer targetPlayer) {
                viewers.add(targetPlayer);
            }
            for (ServerPlayer player : viewers) {
                if (player != excluded && channel.isRemotePresent(player.connection.getConnection())) {
                    channel.send(payload, PacketDistributor.PLAYER.with(player));
                }
            }
        }
        *///?}
        //? if neoforge {
        /*Set<ServerPlayer> viewers = new LinkedHashSet<>(((net.minecraft.server.level.ServerLevel) parent.level())
                .getChunkSource().chunkMap.getPlayers(parent.chunkPosition(), false));
        if (parent instanceof ServerPlayer targetPlayer) {
            viewers.add(targetPlayer);
        }
        for (ServerPlayer player : viewers) {
            if (player != excluded
                    && supportsClient(player)
                    && player.connection.hasChannel(DetachNoticePayload.TYPE)) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
        *///?}
    }

    //? if forge {
    /*static Channel<net.minecraft.network.protocol.common.custom.CustomPacketPayload> forgeChannel() {
        return channel;
    }
    *///?}

    private MobAmputationNetworking() {
    }
}
