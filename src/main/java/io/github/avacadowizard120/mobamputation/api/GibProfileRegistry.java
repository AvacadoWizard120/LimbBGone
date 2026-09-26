package io.github.avacadowizard120.mobamputation.api;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;

/**
 * Internal profile lookup used by the built-in head/arm implementations.
 *
 * <p>The registration seam is retained for development and future custom-mob
 * work, but is not a supported add-on API in this release. A profile alone
 * does not provide compatible geometry, textures, model-part hiding, or death
 * gibs for an arbitrary entity.</p>
 */
public final class GibProfileRegistry {
    private static final Map<EntityType<?>, GibProfile> CUSTOM = new ConcurrentHashMap<>();

    public static void register(EntityType<? extends LivingEntity> type, GibProfile profile) {
        CUSTOM.put(Objects.requireNonNull(type, "type"), Objects.requireNonNull(profile, "profile"));
    }

    public static GibProfile find(LivingEntity entity) {
        GibProfile custom = CUSTOM.get(entity.getType());
        if (custom != null) {
            return custom;
        }
        if (entity instanceof Player) {
            return GibProfile.player();
        }
        if (entity instanceof Creeper) {
            return GibProfile.creeper();
        }
        if (entity instanceof Zombie) {
            return GibProfile.humanoid(BloodProperties.original(1.0F, 0.0F, 0.0F));
        }
        if (entity instanceof Skeleton) {
            return GibProfile.humanoid(null);
        }
        return null;
    }

    public static ResourceLocation entityId(LivingEntity entity) {
        return EntityType.getKey(entity.getType());
    }

    private GibProfileRegistry() {
    }
}
