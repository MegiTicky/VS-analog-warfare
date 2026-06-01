package com.erika.vsanalogwarfare.client;

import com.erika.vsanalogwarfare.scope.ScopeCameraEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public class ScopeCameraRenderer extends EntityRenderer<ScopeCameraEntity> {
    private static final ResourceLocation EMPTY = new ResourceLocation("textures/misc/unknown_pack.png");

    public ScopeCameraRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(ScopeCameraEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        // Invisible camera entity.
    }

    @Override
    public ResourceLocation getTextureLocation(ScopeCameraEntity entity) {
        return EMPTY;
    }
}
