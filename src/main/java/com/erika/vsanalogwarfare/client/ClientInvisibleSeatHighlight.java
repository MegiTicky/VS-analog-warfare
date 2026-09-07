package com.erika.vsanalogwarfare.client;

import com.erika.vsanalogwarfare.seat.InvisibleSeatBlock;
import com.erika.vsanalogwarfare.vehiclesetup.AnalogScrewdriverItem;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * Reveal outline for the invisible seat: while the local player holds the
 * invisible-seat item or the analog screwdriver, the targeted seat block is
 * outlined with a cyan wireframe (one batched LINES draw, no per-frame
 * allocation beyond the small corner array).
 */
public final class ClientInvisibleSeatHighlight {
    private static final float[] COLOR = {0.30F, 0.85F, 1.0F, 0.9F};

    private ClientInvisibleSeatHighlight() {
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || !holdingRevealItem(minecraft)) {
            return;
        }
        if (!(minecraft.hitResult instanceof BlockHitResult hit)
                || !(minecraft.level.getBlockState(hit.getBlockPos()).getBlock() instanceof InvisibleSeatBlock)) {
            return;
        }

        BlockPos pos = hit.getBlockPos();
        Vec3 center = Vec3.atCenterOf(pos);
        Vec3[] corners = new Vec3[8];
        for (int index = 0; index < corners.length; index++) {
            double x = (index & 1) == 0 ? -0.51 : 0.51;
            double y = (index & 2) == 0 ? -0.51 : 0.51;
            double z = (index & 4) == 0 ? -0.51 : 0.51;
            corners[index] = center.add(x, y, z);
        }

        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f matrix = poseStack.last().pose();
        lineBox(buffer, matrix, corners, COLOR);
        Tesselator.getInstance().end();
        RenderSystem.enableDepthTest();
        poseStack.popPose();
    }

    private static boolean holdingRevealItem(Minecraft minecraft) {
        for (InteractionHand hand : InteractionHand.values()) {
            Item item = minecraft.player.getItemInHand(hand).getItem();
            if (item instanceof AnalogScrewdriverItem) {
                return true;
            }
            if (item instanceof BlockItem blockItem && blockItem.getBlock() instanceof InvisibleSeatBlock) {
                return true;
            }
        }
        return false;
    }

    private static void lineBox(BufferBuilder buffer, Matrix4f matrix, Vec3[] corners, float[] color) {
        line(buffer, matrix, corners[0], corners[1], color);
        line(buffer, matrix, corners[2], corners[3], color);
        line(buffer, matrix, corners[4], corners[5], color);
        line(buffer, matrix, corners[6], corners[7], color);
        line(buffer, matrix, corners[0], corners[2], color);
        line(buffer, matrix, corners[1], corners[3], color);
        line(buffer, matrix, corners[4], corners[6], color);
        line(buffer, matrix, corners[5], corners[7], color);
        line(buffer, matrix, corners[0], corners[4], color);
        line(buffer, matrix, corners[1], corners[5], color);
        line(buffer, matrix, corners[2], corners[6], color);
        line(buffer, matrix, corners[3], corners[7], color);
    }

    private static void line(BufferBuilder buffer, Matrix4f matrix,
                             Vec3 first, Vec3 second, float[] color) {
        buffer.vertex(matrix, (float) first.x, (float) first.y, (float) first.z)
                .color(color[0], color[1], color[2], color[3]).endVertex();
        buffer.vertex(matrix, (float) second.x, (float) second.y, (float) second.z)
                .color(color[0], color[1], color[2], color[3]).endVertex();
    }
}
