package io.github.avacadowizard120.mobamputation.client.dismemberment;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;

/** Exact 1.12 Mob Dismemberment body-part geometry and transforms. */
public final class DeathGibRenderer {
    private static final ModelPart HEAD_64 = bake(
            CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -4.0F, -4.0F, 8.0F, 8.0F, 8.0F),
            PartPose.offset(0.0F, 20.0F, 0.0F), 64, 64
    );
    private static final ModelPart BODY_64 = bake(
            CubeListBuilder.create().texOffs(16, 16).addBox(-4.0F, -6.0F, -2.0F, 8.0F, 12.0F, 4.0F),
            PartPose.offset(0.0F, 22.0F, 0.0F), 64, 64
    );
    private static final ModelPart ARM_64 = bake(
            CubeListBuilder.create().texOffs(40, 16).addBox(-2.0F, -6.0F, -2.0F, 4.0F, 12.0F, 4.0F),
            PartPose.offset(0.0F, 22.0F, 0.0F), 64, 64
    );
    private static final ModelPart LEG_64 = bake(
            CubeListBuilder.create().texOffs(0, 16).addBox(-2.0F, -6.0F, -2.0F, 4.0F, 12.0F, 4.0F),
            PartPose.offset(0.0F, 24.0F, 0.0F), 64, 64
    );
    private static final ModelPart HEAD_32 = bake(
            CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -4.0F, -4.0F, 8.0F, 8.0F, 8.0F),
            PartPose.offset(0.0F, 20.0F, 0.0F), 64, 32
    );
    private static final ModelPart BODY_32 = bake(
            CubeListBuilder.create().texOffs(16, 16).addBox(-4.0F, -6.0F, -2.0F, 8.0F, 12.0F, 4.0F),
            PartPose.offset(0.0F, 22.0F, 0.0F), 64, 32
    );
    private static final ModelPart SKELETON_ARM = bake(
            CubeListBuilder.create().texOffs(40, 16).addBox(-1.0F, -6.0F, -1.0F, 2.0F, 12.0F, 2.0F),
            PartPose.offset(0.0F, 24.0F, 0.0F), 64, 32
    );
    private static final ModelPart SKELETON_LEG = bake(
            CubeListBuilder.create().texOffs(0, 16).addBox(-1.0F, -6.0F, -1.0F, 2.0F, 12.0F, 2.0F),
            PartPose.offset(0.0F, 24.0F, 0.0F), 64, 32
    );
    private static final ModelPart CREEPER_FOOT = bake(
            CubeListBuilder.create().texOffs(0, 16).addBox(-2.0F, -3.0F, -2.0F, 4.0F, 6.0F, 4.0F),
            PartPose.offset(0.0F, 24.0F, 0.0F), 64, 32
    );

    public static void render(
            DeathGibEntity gib,
            double x,
            double y,
            double z,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight
    ) {
        LivingEntity parent = gib.parent();
        if (parent == null) {
            return;
        }

        VertexConsumer vertices = buffers.getBuffer(RenderType.entityTranslucent(texture(parent)));
        int threshold = DeathDismembermentSettings.gibGroundTime();
        float alpha = gib.groundTime() >= threshold
                ? Mth.clamp(1.0F - (gib.groundTime() - threshold + partialTick) / 20.0F, 0.0F, 1.0F)
                : 1.0F;

        poseStack.pushPose();
        poseStack.translate(x, y, z);
        double initialYOffset = gib.part() == DeathGibPart.HEAD
                ? 4.0D / 16.0D
                : gib.part().isArm() && parent instanceof Skeleton ? 1.0D / 16.0D : 2.0D / 16.0D;
        poseStack.translate(0.0D, initialYOffset, 0.0D);
        poseStack.mulPose(Axis.YP.rotationDegrees(Mth.rotLerp(partialTick, gib.yRotO, gib.getYRot())));
        poseStack.mulPose(Axis.XN.rotationDegrees(Mth.rotLerp(partialTick, gib.xRotO, gib.getXRot())));
        poseStack.translate(0.0D, 24.0D / 16.0D - gib.getBbHeight() * 0.5D, 0.0D);
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        model(parent, gib.part()).render(
                poseStack,
                vertices,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                Mth.clamp((int) (alpha * 255.0F), 0, 255) << 24 | 0x00FFFFFF
        );
        poseStack.popPose();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ResourceLocation texture(LivingEntity parent) {
        EntityRenderer renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(parent);
        return renderer.getTextureLocation((Entity) parent);
    }

    private static ModelPart model(LivingEntity parent, DeathGibPart part) {
        if (part == DeathGibPart.HEAD) {
            return parent instanceof Zombie ? HEAD_64 : HEAD_32;
        }
        if (part == DeathGibPart.BODY) {
            return parent instanceof Zombie ? BODY_64 : BODY_32;
        }
        if (part.isArm()) {
            return parent instanceof Zombie ? ARM_64 : SKELETON_ARM;
        }
        if (part.isLeg()) {
            return parent instanceof Zombie ? LEG_64 : SKELETON_LEG;
        }
        return CREEPER_FOOT;
    }

    private static ModelPart bake(CubeListBuilder cubes, PartPose pose, int textureWidth, int textureHeight) {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("part", cubes, pose);
        return LayerDefinition.create(mesh, textureWidth, textureHeight).bakeRoot().getChild("part");
    }

    private DeathGibRenderer() {
    }
}
