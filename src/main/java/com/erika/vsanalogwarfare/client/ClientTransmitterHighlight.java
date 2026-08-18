package com.erika.vsanalogwarfare.client;

import com.erika.vsanalogwarfare.scope.compat.VsCompat;
import com.erika.vsanalogwarfare.vehiclesetup.VehicleSetupAction;
import com.erika.vsanalogwarfare.vehiclesetup.compat.VehicleSetupReflection;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;

public final class ClientTransmitterHighlight {
    private static VehicleSetupAction action;
    private static BlockPos setupPos;

    private ClientTransmitterHighlight() { }

    public static void show(BlockPos setupPos, VehicleSetupAction action) {
        ClientTransmitterHighlight.setupPos = setupPos;
        ClientTransmitterHighlight.action = action;
    }

    public static void clear() {
        action = null;
        setupPos = null;
    }

    public static boolean isShowing(VehicleSetupAction candidate) {
        return action == candidate;
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL
                || action == null || setupPos == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        Level level = minecraft.level;
        if (level == null) return;
        Vec3[] corners = resolve(level);
        if (corners == null) return;

        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR);
        lineBox(buffer, poseStack, corners, 1.0f, 0.2f, 0.05f, 1.0f);
        Tesselator.getInstance().end();
        RenderSystem.enableDepthTest();
        poseStack.popPose();
    }

    private static void lineBox(BufferBuilder buffer, PoseStack poseStack, Vec3[] corners,
                                float red, float green, float blue, float alpha) {
        var matrix = poseStack.last().pose();
        line(buffer, matrix, corners[0], corners[1], red, green, blue, alpha);
        line(buffer, matrix, corners[2], corners[3], red, green, blue, alpha);
        line(buffer, matrix, corners[4], corners[5], red, green, blue, alpha);
        line(buffer, matrix, corners[6], corners[7], red, green, blue, alpha);
        line(buffer, matrix, corners[0], corners[2], red, green, blue, alpha);
        line(buffer, matrix, corners[1], corners[3], red, green, blue, alpha);
        line(buffer, matrix, corners[4], corners[6], red, green, blue, alpha);
        line(buffer, matrix, corners[5], corners[7], red, green, blue, alpha);
        line(buffer, matrix, corners[0], corners[4], red, green, blue, alpha);
        line(buffer, matrix, corners[1], corners[5], red, green, blue, alpha);
        line(buffer, matrix, corners[2], corners[6], red, green, blue, alpha);
        line(buffer, matrix, corners[3], corners[7], red, green, blue, alpha);
    }

    private static void line(BufferBuilder buffer, org.joml.Matrix4f matrix,
                             Vec3 first, Vec3 second,
                             float red, float green, float blue, float alpha) {
        buffer.vertex(matrix, (float) first.x, (float) first.y, (float) first.z)
                .color(red, green, blue, alpha).endVertex();
        buffer.vertex(matrix, (float) second.x, (float) second.y, (float) second.z)
                .color(red, green, blue, alpha).endVertex();
    }

    private static Vec3[] resolve(Level level) {
        if (action.targetShipId() >= 0 && action.shipOffset() != null) {
            Object ship = VehicleSetupReflection.findShip(level, setupPos);
            if (ship != null && VsCompat.getShipId(ship) == action.targetShipId()) {
                BlockPos target = VehicleSetupReflection.positionOnShip(ship, action.shipOffset());
                if (target != null) {
                    return transformedCorners(ship, Vec3.atCenterOf(target));
                }
            }
            return null;
        }
        if (action.targetOffset() == null) return null;
        return transformedCorners(null, Vec3.atCenterOf(setupPos.offset(action.targetOffset())));
    }

    private static Vec3[] transformedCorners(Object ship, Vec3 center) {
        Vec3[] corners = new Vec3[8];
        for (int index = 0; index < corners.length; index++) {
            double x = (index & 1) == 0 ? -0.53 : 0.53;
            double y = (index & 2) == 0 ? -0.53 : 0.53;
            double z = (index & 4) == 0 ? -0.53 : 0.53;
            Vec3 local = center.add(x, y, z);
            corners[index] = ship == null ? local : VsCompat.shipToWorldPosition(ship, local);
        }
        return corners;
    }
}
