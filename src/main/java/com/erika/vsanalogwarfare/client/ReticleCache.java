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

    static {
        for (int i = 0; i < PRE_CACHED_RANGES.length; i++) PRE_CACHED_RANGES[i] = String.valueOf(i * 100);
    }

    private ReticleCache() { }
    public static void markDirty() { cacheDirty = true; }

    public static void cleanup() {
        if (reticleTarget != null) {
            reticleTarget.destroyBuffers();
            reticleTarget = null;
        }
        cacheDirty = true;
        cachedProfile = null;
    }

    private static void ensureRenderTarget() {
        if (reticleTarget == null) reticleTarget = new TextureTarget(TEXTURE_WIDTH, TEXTURE_HEIGHT, true, Minecraft.ON_OSX);
    }

    public static void rebuildIfNeeded(int scopeHeight, double fov, BallisticProfile profile, List<ReticleMark> marks) {
        if (!cacheDirty && cachedScopeHeight == scopeHeight && cachedFov == fov && profile.equals(cachedProfile)) return;
        ensureRenderTarget();
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        reticleTarget.setClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        reticleTarget.clear(Minecraft.ON_OSX);
        reticleTarget.bindWrite(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        RenderSystem.setProjectionMatrix(new Matrix4f().ortho(0.0f, TEXTURE_WIDTH, TEXTURE_HEIGHT, 0.0f,
                1000.0f, 3000.0f), com.mojang.blaze3d.vertex.VertexSorting.DISTANCE_TO_ORIGIN);
        RenderSystem.getModelViewStack().pushPose();
        RenderSystem.getModelViewStack().setIdentity();
        RenderSystem.getModelViewStack().translate(0, 0, -2000);
        RenderSystem.applyModelViewMatrix();
        double pxPerDegree = scopeHeight / Math.max(1.0, fov);
        double cx = TEXTURE_WIDTH / 2.0;
        double cy = TEXTURE_HEIGHT / 2.0;
        float alpha = 224.0f / 255.0f;
        float thickness = Math.max(1, Math.round((scopeHeight / 720.0f) * 2.0f));
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int lastLineY = -999;
        for (ReticleMark mark : marks) {
            int y = (int) Math.round(cy + mark.pitchDegrees() * pxPerDegree);
            if (y < 0 || y >= TEXTURE_HEIGHT || (mark.distance() % 500 != 0 && Math.abs(y - lastLineY) < 4)) continue;
            lastLineY = y;
            int half = mark.distance() % 500 == 0 ? 6 : 4;
            float y0 = y - thickness / 2.0f;
            float y1 = y0 + thickness;
            buffer.vertex((float) (cx - half), y0, 0.0f).color(0.0f, 0.0f, 0.0f, alpha).endVertex();
            buffer.vertex((float) (cx - half), y1, 0.0f).color(0.0f, 0.0f, 0.0f, alpha).endVertex();
            buffer.vertex((float) (cx + half + 1), y1, 0.0f).color(0.0f, 0.0f, 0.0f, alpha).endVertex();
            buffer.vertex((float) (cx + half + 1), y0, 0.0f).color(0.0f, 0.0f, 0.0f, alpha).endVertex();
        }
        Tesselator.getInstance().end();
        MultiBufferSource.BufferSource source = MultiBufferSource.immediate(Tesselator.getInstance().getBuilder());
        int lastTextY = -999;
        for (ReticleMark mark : marks) {
            if (mark.distance() % 100 != 0) continue;
            int y = (int) Math.round(cy + mark.pitchDegrees() * pxPerDegree);
            if (y < 0 || y >= TEXTURE_HEIGHT || (mark.distance() % 500 != 0 && Math.abs(y - lastTextY) < 9)) continue;
            lastTextY = y;
            int index = mark.distance() / 100;
            String text = index >= 0 && index < PRE_CACHED_RANGES.length ? PRE_CACHED_RANGES[index]
                    : String.valueOf(mark.distance());
            font.drawInBatch(text, (float) (cx + (mark.distance() % 500 == 0 ? 6 : 4) + 4), y - 4.0f,
                    0xD0101010, false, new Matrix4f(), source, Font.DisplayMode.NORMAL, 0, 15728880);
        }
        source.endBatch();
        RenderSystem.disableBlend();
        reticleTarget.unbindWrite();
        mc.getMainRenderTarget().bindWrite(true);
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
