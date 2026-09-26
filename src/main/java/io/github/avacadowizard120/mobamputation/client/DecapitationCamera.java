package io.github.avacadowizard120.mobamputation.client;

import io.github.avacadowizard120.mobamputation.api.Limb;
import io.github.avacadowizard120.mobamputation.config.MobAmputationConfig;
import io.github.avacadowizard120.mobamputation.entity.GibEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * Owns the short-lived, entirely client-side camera ride for the local
 * player's detached head.
 *
 * <p>The gib itself is used as vanilla's camera entity. That gives the view
 * the gib's interpolated position, pitch and yaw without copying or changing
 * any of its physics. This class is deliberately conservative about camera
 * ownership: if vanilla or another mod points the camera somewhere else, the
 * ride is abandoned rather than fighting to take it back.</p>
 */
public final class DecapitationCamera {
    private static GibEntity head;
    private static LocalPlayer player;

    public static void onDetached(GibEntity gib) {
        Minecraft minecraft = Minecraft.getInstance();
        if (gib.limb() != Limb.HEAD
                || gib.parent() != minecraft.player
                || !MobAmputationConfig.get().decapitationCamera()
                || !minecraft.options.getCameraType().isFirstPerson()
                || minecraft.getCameraEntity() != minecraft.player
                || gib.isRemoved()
                || gib.isAttached()) {
            return;
        }

        head = gib;
        player = minecraft.player;
        minecraft.setCameraEntity(gib);
    }

    /** Called at the end of every Minecraft client tick. */
    public static void clientTick(Minecraft minecraft) {
        GibEntity riddenHead = head;
        if (riddenHead == null) {
            return;
        }

        // Never compete with spectator cameras, replay/camera mods, or a
        // vanilla camera transition. Losing ownership ends this ride.
        if (minecraft.getCameraEntity() != riddenHead) {
            release();
            return;
        }

        boolean valid = MobAmputationConfig.get().decapitationCamera()
                && minecraft.level != null
                && minecraft.player != null
                && minecraft.player == player
                && !player.isRemoved()
                && riddenHead.level() == minecraft.level
                && !riddenHead.isRemoved()
                && !riddenHead.isAttached()
                && minecraft.options.getCameraType().isFirstPerson();
        if (!valid) {
            restore(minecraft);
        }
    }

    /** Immediately relinquishes the camera during level/session cleanup. */
    public static void reset(Minecraft minecraft) {
        if (head != null && minecraft.getCameraEntity() == head) {
            restore(minecraft);
        } else {
            release();
        }
    }

    /** True only while this exact gib still owns the first-person camera. */
    public static boolean isRiding(GibEntity gib) {
        return head == gib && Minecraft.getInstance().getCameraEntity() == gib;
    }

    private static void restore(Minecraft minecraft) {
        LocalPlayer currentPlayer = minecraft.player;
        if (currentPlayer != null && !currentPlayer.isRemoved()) {
            minecraft.setCameraEntity(currentPlayer);
        } else {
            minecraft.setCameraEntity(null);
        }
        release();
    }

    private static void release() {
        head = null;
        player = null;
    }

    private DecapitationCamera() {
    }
}
