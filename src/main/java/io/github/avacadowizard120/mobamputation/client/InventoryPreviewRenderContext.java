package io.github.avacadowizard120.mobamputation.client;

/**
 * Marks the small entity render performed inside inventory-style screens.
 *
 * <p>The local player is normally excluded from third-person amputation
 * rendering while the camera is first person. The inventory paper doll uses
 * that same player and renderer without changing the camera, so it needs a
 * narrowly scoped override rather than changing ordinary world rendering.</p>
 */
public final class InventoryPreviewRenderContext {
    private static int depth;

    public static void begin() {
        depth++;
    }

    public static void end() {
        if (depth > 0) {
            depth--;
        }
    }

    public static boolean isActive() {
        return depth > 0;
    }

    private InventoryPreviewRenderContext() {
    }
}
