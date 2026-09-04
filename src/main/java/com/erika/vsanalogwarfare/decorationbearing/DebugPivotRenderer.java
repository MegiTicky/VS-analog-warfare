package com.erika.vsanalogwarfare.decorationbearing;

import com.erika.vsanalogwarfare.VSAnalogWarfare;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/**
 * Debug rendering for the decoration-bearing contraption. Draws in-world
 * markers so pivot and yaw errors are directly visible:
 *
 * <ul>
 *   <li>MAGENTA box + vertical axis line at the DBC's computed visual
 *       rotation pivot ({@code entityPos + pivotLocal + (0, 0.5, 0)}).</li>
 *   <li>YELLOW box at the linked CBC cannon's own visual pivot
 *       ({@code cbcEntityPos + (0, 0.5, 0)}). Magenta and yellow must
 *       coincide; any gap is the pivot error.</li>
 *   <li>CYAN box at the DBC entity origin (render origin).</li>
 * </ul>
 *
 * Enabled by default in this debug build; flip {@link #ENABLED} to false to
 * turn it off.
 */
@Mod.EventBusSubscriber(modid = VSAnalogWarfare.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class DebugPivotRenderer {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static boolean ENABLED = true;
    private static boolean announced;

    private DebugPivotRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (!ENABLED || event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        if (!announced) {
            announced = true;
            LOGGER.info("[VSAW_DBC] pivot debug render active (build={})", DecorationBearingContraptionEntity.BUILD_TAG);
        }

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        Vec3 camera = event.getCamera().getPosition();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof DecorationBearingContraptionEntity dbc)) {
                continue;
            }
            Vec3 entityPos = entity.position();
            Vec3 pivot = entityPos.add(dbc.getPivotLocal()).add(0.0, 0.5, 0.0);

            // Our computed visual rotation pivot + rotation axis (magenta)
            drawBox(poseStack, lines, camera, pivot, 0.2f, 1.0f, 0.0f, 1.0f);
            drawAxis(poseStack, lines, camera, pivot, 1.0f, 0.0f, 1.0f);
            // Entity origin (cyan)
            drawBox(poseStack, lines, camera, entityPos, 0.15f, 0.0f, 1.0f, 1.0f);

            // CBC cannon's own visual pivot (yellow) — should coincide with magenta
            Vec3 cbcPos = dbc.getCbcEntityPosSynced();
            if (cbcPos != Vec3.ZERO && cbcPos.lengthSqr() > 1.0e-4) {
                drawBox(poseStack, lines, camera, cbcPos.add(0.0, 0.5, 0.0), 0.2f, 1.0f, 1.0f, 0.0f);
            }
        }
        buffers.endBatch(RenderType.lines());
    }

    private static void drawBox(PoseStack poseStack, VertexConsumer consumer, Vec3 camera,
                                Vec3 center, float half, float r, float g, float b) {
        double x = center.x - camera.x;
        double y = center.y - camera.y;
        double z = center.z - camera.z;
        LevelRenderer.renderLineBox(poseStack, consumer,
                x - half, y - half, z - half, x + half, y + half, z + half, r, g, b, 1.0f);
    }

    private static void drawAxis(PoseStack poseStack, VertexConsumer consumer, Vec3 camera,
                                 Vec3 pivot, float r, float g, float b) {
        double x = pivot.x - camera.x;
        double y = pivot.y - camera.y;
        double z = pivot.z - camera.z;
        LevelRenderer.renderLineBox(poseStack, consumer,
                x - 0.05, y - 4.0, z - 0.05, x + 0.05, y + 4.0, z + 0.05, r, g, b, 1.0f);
    }
}
