package com.erika.vsanalogwarfare.client;

import com.erika.vsanalogwarfare.network.ScrewdriverHudPacket;
import com.erika.vsanalogwarfare.scope.compat.VsCompat;
import com.erika.vsanalogwarfare.vehiclesetup.compat.VehicleSetupReflection;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * X-ray wireframes for blocks the analog screwdriver recorded actions on (amber)
 * or marked for removal (red). Only visible while the screwdriver is held.
 * Ship blocks are re-transformed ship-to-world every frame so moving ships stay accurate.
 */
public final class ClientScrewdriverHighlight {
    private static final float[] REMOVAL_COLOR = {1.0f, 0.2f, 0.05f, 0.9f};
    private static final float[] ACTED_COLOR = {1.0f, 0.63f, 0.29f, 0.9f};

    private static BlockPos anchor;
    private static List<ScrewdriverHudPacket.HighlightRecord> records = List.of();

    private ClientScrewdriverHighlight() {
    }

    public static void setRecords(BlockPos setupAnchor, List<ScrewdriverHudPacket.HighlightRecord> newRecords) {
        anchor = setupAnchor;
        records = newRecords == null ? List.of() : List.copyOf(newRecords);
    }

    public static void clear() {
        anchor = null;
        records = List.of();
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL
                || anchor == null || records.isEmpty()
                || !AnalogScrewdriverOverlay.isHolding()) return;
        Minecraft minecraft = Minecraft.getInstance();
        Level level = minecraft.level;
        if (level == null) return;

        Map<Long, Object> shipsById = new HashMap<>();
        for (Object ship : VsCompat.getAllShips(level)) {
            shipsById.put(VsCompat.getShipId(ship), ship);
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
        for (ScrewdriverHudPacket.HighlightRecord record : records) {
            Vec3[] corners = resolveCorners(record, shipsById);
            if (corners == null) continue;
            float[] color = record.removal() ? REMOVAL_COLOR : ACTED_COLOR;
            lineBox(buffer, matrix, corners, color);
        }
        Tesselator.getInstance().end();
        RenderSystem.enableDepthTest();
        poseStack.popPose();
    }

    @SuppressWarnings("unchecked")
    private static Vec3[] resolveCorners(ScrewdriverHudPacket.HighlightRecord record, Map<Long, Object> shipsById) {
        if (record.shipId() >= 0) {
            if (record.shipOffset() == 0L) return null;
            Object ship = shipsById.get(record.shipId());
            if (ship == null) return null;
            BlockPos shipPos = VehicleSetupReflection.positionOnShip(ship, BlockPos.of(record.shipOffset()));
            if (shipPos == null) return null;
            return transformedCorners(ship, Vec3.atCenterOf(shipPos));
        }
        if (record.targetOffset() == 0L) return null;
        return transformedCorners(null, Vec3.atCenterOf(anchor.offset(BlockPos.of(record.targetOffset()))));
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
