package com.erika.vsanalogwarfare.client;

import com.erika.vsanalogwarfare.vehiclemount.VehicleMountHandleBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.RandomSource;
import net.minecraftforge.client.RenderTypeHelper;
import net.minecraftforge.client.model.data.ModelData;

public final class VehicleMountHandleRenderer implements BlockEntityRenderer<VehicleMountHandleBlockEntity> {
    private final BlockRenderDispatcher blockRenderer;
    private final BlockColors blockColors;

    public VehicleMountHandleRenderer(BlockEntityRendererProvider.Context context) {
        blockRenderer = Minecraft.getInstance().getBlockRenderer();
        blockColors = Minecraft.getInstance().getBlockColors();
    }

    @Override
    public void render(VehicleMountHandleBlockEntity handle, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        poseStack.pushPose();
        poseStack.translate(handle.offsetX() / 8.0, handle.offsetY() / 8.0, handle.offsetZ() / 8.0);
        BakedModel model = blockRenderer.getBlockModel(handle.getBlockState());
        int color = blockColors.getColor(handle.getBlockState(), null, null, 0);
        float red = (float) (color >> 16 & 255) / 255.0F;
        float green = (float) (color >> 8 & 255) / 255.0F;
        float blue = (float) (color & 255) / 255.0F;
        for (RenderType renderType : model.getRenderTypes(handle.getBlockState(), RandomSource.create(42), ModelData.EMPTY)) {
            blockRenderer.getModelRenderer().renderModel(poseStack.last(),
                    buffer.getBuffer(RenderTypeHelper.getEntityRenderType(renderType, false)), handle.getBlockState(), model,
                    red, green, blue, packedLight, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, renderType);
        }
        poseStack.popPose();
    }
}
