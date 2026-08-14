package com.erika.vsanalogwarfare.client;

import com.erika.vsanalogwarfare.scope.ballistics.BallisticProfile;
import com.erika.vsanalogwarfare.scope.ballistics.ReticleMark;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;

import java.util.List;

public final class ReticleCache {
    private static final int TEXTURE_WIDTH = 256;
    private static final int TEXTURE_HEIGHT = 2048;
    private static TextureTarget reticleTarget;
    private static boolean cacheDirty = true;
    private static int cachedScopeHeight;
    private static double cachedFov;
    private static BallisticProfile cachedProfile;
    private static final String[] PRE_CACHED_RANGES = new String[201];

    static { for (int i = 0; i < PRE_CACHED_RANGES.length; i++) PRE_CACHED_RANGES[i] = String.valueOf(i * 100); }
    private ReticleCache() { }
    public static void markDirty() { cacheDirty = true; }
    public static void cleanup() { if (reticleTarget != null) { reticleTarget.destroyBuffers(); reticleTarget = null; } cacheDirty = true; cachedProfile = null; }
    private static void ensureRenderTarget() { if (reticleTarget == null) reticleTarget = new TextureTarget(TEXTURE_WIDTH, TEXTURE_HEIGHT, true, Minecraft.ON_OSX); }

    public static void rebuildIfNeeded(int scopeHeight, double fov, BallisticProfile profile, List<ReticleMark> marks) {
        if (!cacheDirty && cachedScopeHeight == scopeHeight && cachedFov == fov && cachedProfile != null && cachedProfile.equals(profile)) return;
        ensureRenderTarget();
        Minecraft minecraft = Minecraft.getInstance();
        reticleTarget.setClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        reticleTarget.clear(Minecraft.ON_OSX);
        reticleTarget.bindWrite(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        RenderSystem.setProjectionMatrix(new Matrix4f().ortho(0.0f, TEXTURE_WIDTH, TEXTURE_HEIGHT, 0.0f, 1000.0f, 3000.0f),
                com.mojang.blaze3d.vertex.VertexSorting.DISTANCE_TO_ORIGIN);
        RenderSystem.getModelViewStack().pushPose();
        RenderSystem.getModelViewStack().setIdentity();
        RenderSystem.getModelViewStack().translate(0, 0, -2000);
        RenderSystem.applyModelViewMatrix();
        double pixelsPerDegree = scopeHeight / Math.max(1.0, fov);
        double centerX = TEXTURE_WIDTH / 2.0;
        double centerY = TEXTURE_HEIGHT / 2.0;
        int color = 0xE0000000;
        float alpha = ((color >>> 24) & 0xFF) / 255.0f;
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int previousLine = -999;
        int thickness = Math.max(1, Math.round(scopeHeight / 360.0f));
        for (ReticleMark mark : marks) {
            int y = (int) Math.round(centerY + mark.pitchDegrees() * pixelsPerDegree);
            if (y < 0 || y >= TEXTURE_HEIGHT || mark.distance() % 500 != 0 && Math.abs(y - previousLine) < 4) continue;
            previousLine = y;
            int halfWidth = mark.distance() % 500 == 0 ? 6 : 4;
            int top = y - thickness / 2;
            int bottom = top + thickness;
            buffer.vertex((float) (centerX - halfWidth), top, 0).color(0, 0, 0, alpha).endVertex();
            buffer.vertex((float) (centerX - halfWidth), bottom, 0).color(0, 0, 0, alpha).endVertex();
            buffer.vertex((float) (centerX + halfWidth + 1), bottom, 0).color(0, 0, 0, alpha).endVertex();
            buffer.vertex((float) (centerX + halfWidth + 1), top, 0).color(0, 0, 0, alpha).endVertex();
        }
        Tesselator.getInstance().end();
        MultiBufferSource.BufferSource textBuffers = MultiBufferSource.immediate(Tesselator.getInstance().getBuilder());
        Font font = minecraft.font;
        int previousText = -999;
        for (ReticleMark mark : marks) {
            if (mark.distance() % 100 != 0) continue;
            int y = (int) Math.round(centerY + mark.pitchDegrees() * pixelsPerDegree);
            if (y < 0 || y >= TEXTURE_HEIGHT || mark.distance() % 500 != 0 && Math.abs(y - previousText) < 9) continue;
            previousText = y;
            int rangeIndex = mark.distance() / 100;
            String label = rangeIndex >= 0 && rangeIndex < PRE_CACHED_RANGES.length ? PRE_CACHED_RANGES[rangeIndex] : String.valueOf(mark.distance());
            font.drawInBatch(label, (float) (centerX + (mark.distance() % 500 == 0 ? 10 : 8)), y - 4,
                    0xD0101010, false, new Matrix4f(), textBuffers, Font.DisplayMode.NORMAL, 0, 15728880);
        }
        textBuffers.endBatch();
        RenderSystem.disableBlend();
        reticleTarget.unbindWrite();
        minecraft.getMainRenderTarget().bindWrite(true);
        RenderSystem.getModelViewStack().popPose();
        RenderSystem.applyModelViewMatrix();
        cachedScopeHeight = scopeHeight;
        cachedFov = fov;
        cachedProfile = profile;
        cacheDirty = false;
    }

    public static TextureTarget getReticleTarget() { return reticleTarget; }
    public static int getTextureWidth() { return TEXTURE_WIDTH; }
    public static int getTextureHeight() { return TEXTURE_HEIGHT; }
    public static double getCachedPxPerDegree() { return cachedScopeHeight / Math.max(1.0, cachedFov); }
}
