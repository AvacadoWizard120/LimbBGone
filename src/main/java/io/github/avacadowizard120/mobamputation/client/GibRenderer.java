package io.github.avacadowizard120.mobamputation.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.avacadowizard120.mobamputation.api.Limb;
import io.github.avacadowizard120.mobamputation.config.MobAmputationConfig;
import io.github.avacadowizard120.mobamputation.entity.GibEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;

public final class GibRenderer {
    private static final ModelPart HEAD_64 = bake(
            CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -4.0F, -4.0F, 8.0F, 8.0F, 8.0F),
            PartPose.offset(0.0F, 20.0F, 0.0F),
            64,
            64
    );
    private static final ModelPart HEAD_32 = bake(
            CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -4.0F, -4.0F, 8.0F, 8.0F, 8.0F),
            PartPose.offset(0.0F, 20.0F, 0.0F),
            64,
            32
    );
    private static final ModelPart PLAYER_HAT_64 = bake(
            CubeListBuilder.create().texOffs(32, 0).addBox(
                    -4.0F, -4.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.5F)
            ),
            PartPose.offset(0.0F, 20.0F, 0.0F),
            64,
            64
    );
    private static final ModelPart ARM_64 = bake(
            CubeListBuilder.create().texOffs(40, 16).addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F),
            PartPose.offset(-1.0F, 22.0F, 0.0F),
            64,
            64
    );
    private static final ModelPart ARM_32 = bake(
            CubeListBuilder.create().texOffs(40, 16).addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F),
            PartPose.offset(-1.0F, 22.0F, 0.0F),
            64,
            32
    );
    private static final ModelPart SKELETON_ARM = bake(
            CubeListBuilder.create().texOffs(40, 16).addBox(-1.0F, -2.0F, -2.0F, 2.0F, 12.0F, 2.0F),
            PartPose.offset(-1.0F, 22.0F, 0.0F),
            64,
            32
    );
    private static final ModelPart PLAYER_RIGHT_ARM_WIDE = playerArm(40, 16, -3.0F, 4.0F, 1.0F, 0.0F);
    private static final ModelPart PLAYER_LEFT_ARM_WIDE = playerArm(32, 48, -1.0F, 4.0F, -1.0F, 0.0F);
    private static final ModelPart PLAYER_RIGHT_SLEEVE_WIDE = playerArm(40, 32, -3.0F, 4.0F, 1.0F, 0.25F);
    private static final ModelPart PLAYER_LEFT_SLEEVE_WIDE = playerArm(48, 48, -1.0F, 4.0F, -1.0F, 0.25F);
    private static final ModelPart PLAYER_RIGHT_ARM_SLIM = playerArm(40, 16, -2.0F, 3.0F, 0.5F, 0.0F);
    private static final ModelPart PLAYER_LEFT_ARM_SLIM = playerArm(32, 48, -1.0F, 3.0F, -0.5F, 0.0F);
    private static final ModelPart PLAYER_RIGHT_SLEEVE_SLIM = playerArm(40, 32, -2.0F, 3.0F, 0.5F, 0.25F);
    private static final ModelPart PLAYER_LEFT_SLEEVE_SLIM = playerArm(48, 48, -1.0F, 3.0F, -0.5F, 0.25F);

    public static void render(
            GibEntity gib,
            double x,
            double y,
            double z,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight
    ) {
        if (gib.isAttached() || !GibManager.shouldRender(gib)) {
            return;
        }
        LivingEntity parent = gib.parent();
        if (parent == null) {
            return;
        }

        ResourceLocation texture = texture(parent);
        VertexConsumer vertices = buffers.getBuffer(RenderType.entityTranslucent(texture));
        int groundThreshold = MobAmputationConfig.get().gibGroundTime();
        float alpha = gib.groundTime() >= groundThreshold
                ? Mth.clamp(1.0F - (gib.groundTime() - groundThreshold + partialTick) / 20.0F, 0.0F, 1.0F)
                : 1.0F;

        poseStack.pushPose();
        poseStack.translate(x, y, z);
        double initialYOffset = gib.limb() == Limb.HEAD
                ? 4.0D / 16.0D
                : parent instanceof Skeleton ? 1.0D / 16.0D : 2.0D / 16.0D;
        poseStack.translate(0.0D, initialYOffset, 0.0D);
        poseStack.mulPose(Axis.YP.rotationDegrees(Mth.rotLerp(partialTick, gib.yRotO, gib.getYRot())));
        poseStack.mulPose(Axis.XN.rotationDegrees(Mth.rotLerp(partialTick, gib.xRotO, gib.getXRot())));
        poseStack.translate(0.0D, 24.0D / 16.0D - gib.getBbHeight() * 0.5D, 0.0D);
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        renderPart(model(parent, gib.limb()), poseStack, vertices, packedLight, alpha);
        renderPlayerOverlay(parent, gib.limb(), poseStack, vertices, packedLight, alpha);
        poseStack.popPose();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ResourceLocation texture(LivingEntity parent) {
        EntityRenderer renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(parent);
        return renderer.getTextureLocation((Entity) parent);
    }

    private static ModelPart model(LivingEntity parent, Limb limb) {
        if (limb == Limb.HEAD) {
            return parent instanceof Zombie || parent instanceof Player ? HEAD_64 : HEAD_32;
        }
        if (parent instanceof Player player) {
            return playerArm(player, limb, false);
        }
        if (parent instanceof Zombie) {
            return ARM_64;
        }
        return parent instanceof Skeleton ? SKELETON_ARM : ARM_32;
    }

    private static void renderPlayerOverlay(
            LivingEntity parent,
            Limb limb,
            PoseStack poseStack,
            VertexConsumer vertices,
            int packedLight,
            float alpha
    ) {
        if (!(parent instanceof Player player)) {
            return;
        }
        if (limb == Limb.HEAD) {
            if (player.isModelPartShown(PlayerModelPart.HAT)) {
                renderPart(PLAYER_HAT_64, poseStack, vertices, packedLight, alpha);
            }
            return;
        }
        PlayerModelPart sleeve = limb == Limb.LEFT_ARM
                ? PlayerModelPart.LEFT_SLEEVE
                : PlayerModelPart.RIGHT_SLEEVE;
        if (player.isModelPartShown(sleeve)) {
            renderPart(playerArm(player, limb, true), poseStack, vertices, packedLight, alpha);
        }
    }

    private static void renderPart(
            ModelPart part,
            PoseStack poseStack,
            VertexConsumer vertices,
            int packedLight,
            float alpha
    ) {
        part.render(
                poseStack,
                vertices,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                alphaColor(alpha)
        );
    }

    private static ModelPart playerArm(Player player, Limb limb, boolean sleeve) {
        boolean slim = player instanceof AbstractClientPlayer clientPlayer
                && clientPlayer.getSkin().model() == PlayerSkin.Model.SLIM;
        if (limb == Limb.LEFT_ARM) {
            if (slim) {
                return sleeve ? PLAYER_LEFT_SLEEVE_SLIM : PLAYER_LEFT_ARM_SLIM;
            }
            return sleeve ? PLAYER_LEFT_SLEEVE_WIDE : PLAYER_LEFT_ARM_WIDE;
        }
        if (slim) {
            return sleeve ? PLAYER_RIGHT_SLEEVE_SLIM : PLAYER_RIGHT_ARM_SLIM;
        }
        return sleeve ? PLAYER_RIGHT_SLEEVE_WIDE : PLAYER_RIGHT_ARM_WIDE;
    }

    private static ModelPart playerArm(
            int textureX,
            int textureY,
            float cubeX,
            float width,
            float poseX,
            float inflation
    ) {
        return bake(
                CubeListBuilder.create().texOffs(textureX, textureY).addBox(
                        cubeX, -2.0F, -2.0F, width, 12.0F, 4.0F, new CubeDeformation(inflation)
                ),
                PartPose.offset(poseX, 22.0F, 0.0F),
                64,
                64
        );
    }

    private static ModelPart bake(
            CubeListBuilder cubes,
            PartPose pose,
            int textureWidth,
            int textureHeight
    ) {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("part", cubes, pose);
        return LayerDefinition.create(mesh, textureWidth, textureHeight).bakeRoot().getChild("part");
    }

    private static int alphaColor(float alpha) {
        return Mth.clamp((int) (alpha * 255.0F), 0, 255) << 24 | 0x00FFFFFF;
    }

    private GibRenderer() {
    }
}
