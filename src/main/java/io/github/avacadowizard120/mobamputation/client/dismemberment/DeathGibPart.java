package io.github.avacadowizard120.mobamputation.client.dismemberment;

import java.util.List;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;

/** The original Mob Dismemberment integer part types, named for modern code. */
public enum DeathGibPart {
    HEAD(0, 0.5F, 0.5F),
    LEFT_ARM(1, 0.3F, 0.4F),
    RIGHT_ARM(2, 0.3F, 0.4F),
    BODY(3, 0.5F, 0.5F),
    LEFT_LEG(4, 0.3F, 0.4F),
    RIGHT_LEG(5, 0.3F, 0.4F),
    FRONT_LEFT_FOOT(6, 0.3F, 0.4F),
    FRONT_RIGHT_FOOT(7, 0.3F, 0.4F),
    BACK_LEFT_FOOT(8, 0.3F, 0.4F),
    BACK_RIGHT_FOOT(9, 0.3F, 0.4F);

    private static final List<DeathGibPart> HUMANOID_PARTS = List.of(
            HEAD, LEFT_ARM, RIGHT_ARM, BODY, LEFT_LEG, RIGHT_LEG
    );
    private static final List<DeathGibPart> CREEPER_PARTS = List.of(
            HEAD, BODY, FRONT_LEFT_FOOT, FRONT_RIGHT_FOOT, BACK_LEFT_FOOT, BACK_RIGHT_FOOT
    );

    private final int upstreamType;
    private final float width;
    private final float height;

    DeathGibPart(int upstreamType, float width, float height) {
        this.upstreamType = upstreamType;
        this.width = width;
        this.height = height;
    }

    public int upstreamType() {
        return upstreamType;
    }

    public float width() {
        return width;
    }

    public float height() {
        return height;
    }

    public boolean isArm() {
        return this == LEFT_ARM || this == RIGHT_ARM;
    }

    public boolean isLeg() {
        return this == LEFT_LEG || this == RIGHT_LEG;
    }

    public boolean isFoot() {
        return upstreamType >= 6;
    }

    public static List<DeathGibPart> forEntity(LivingEntity entity) {
        return entity instanceof Creeper ? CREEPER_PARTS : HUMANOID_PARTS;
    }
}
