package com.erika.vsanalogwarfare.client;

import com.erika.vsanalogwarfare.VSAnalogWarfare;
import com.erika.vsanalogwarfare.config.ClientConfig;
import com.erika.vsanalogwarfare.config.CommonConfig;
import com.erika.vsanalogwarfare.network.MouseAimTargetPacket;
import com.erika.vsanalogwarfare.network.ModNetwork;
import com.erika.vsanalogwarfare.network.StopScopePacket;
import com.erika.vsanalogwarfare.network.ToggleScopeZoomPacket;
import com.erika.vsanalogwarfare.scope.ballistics.BallisticProfile;
import com.erika.vsanalogwarfare.scope.ballistics.ReticleMark;
import com.erika.vsanalogwarfare.scope.rig.CameraPose;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

@Mod.EventBusSubscriber(modid = VSAnalogWarfare.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientForgeEvents {
    private static final ResourceLocation SCOPE_BASE = new ResourceLocation(VSAnalogWarfare.MOD_ID, "textures/misc/scope_base.png");
    private static boolean shiftWasDown;
    private static int mouseAimPacketCooldown;

    private ClientForgeEvents() {
    }

    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (ClientScopeState.active()) {
            event.setFOV(ClientScopeState.fov());
        }
    }

    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (!ClientScopeState.active()) {
            return;
        }
        float partialTick = (float) event.getPartialTick();
        CameraPose pose = ClientScopeState.cameraPose(partialTick);
        event.setYaw(pose.yaw());
        event.setPitch(pose.pitch());
        event.setRoll(ClientScopeState.roll(pose));
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (!ClientScopeState.active()) {
            shiftWasDown = mc.options.keyShift.isDown();
            mouseAimPacketCooldown = 0;
            while (ClientKeyMappings.SCOPE_ZOOM.consumeClick()) {
                // Drop queued key presses from outside scope.
            }
            while (ClientKeyMappings.SCOPE_FREE_LOOK.consumeClick()) {
                // Drop queued key presses from outside scope.
            }
            while (ClientKeyMappings.SCOPE_RANGEFINDER.consumeClick()) {
                // Drop queued key presses from outside scope.
            }
            return;
        }
        boolean shiftDown = mc.options.keyShift.isDown();
        if (shiftDown && !shiftWasDown) {
            ModNetwork.sendToServer(new StopScopePacket());
        }
        shiftWasDown = shiftDown;

        while (ClientKeyMappings.SCOPE_ZOOM.consumeClick()) {
            ModNetwork.sendToServer(new ToggleScopeZoomPacket());
        }
        while (ClientKeyMappings.SCOPE_FREE_LOOK.consumeClick()) {
            ClientScopeState.toggleFreeLook();
        }
        while (ClientKeyMappings.SCOPE_RANGEFINDER.consumeClick()) {
            ClientScopeState.triggerRangefinder();
        }
        sendMouseAimTargetIfNeeded();
    }

    private static void sendMouseAimTargetIfNeeded() {
        if (!ClientScopeState.freeLookEnabled()) {
            mouseAimPacketCooldown = 0;
            return;
        }
        if (mouseAimPacketCooldown > 0) {
            mouseAimPacketCooldown--;
            return;
        }
        mouseAimPacketCooldown = Math.max(1, CommonConfig.mouseAimPacketIntervalTicks()) - 1;
        BlockPos scopePos = ClientScopeState.scopePos();
        BlockPos mountPos = ClientScopeState.mountPos();
        if (scopePos == null || mountPos == null) {
            return;
        }
        Vec3 direction = ClientScopeState.zeroedFreeLookDirection();
        ModNetwork.sendToServer(new MouseAimTargetPacket(scopePos, mountPos, direction.x, direction.y, direction.z));
    }

    @SubscribeEvent
    public static void onRenderOverlayPre(RenderGuiOverlayEvent.Pre event) {
        if (ClientScopeState.active() && event.getOverlay().id().equals(VanillaGuiOverlay.CROSSHAIR.id())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        if (!ClientScopeState.active()) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        Minecraft mc = Minecraft.getInstance();
        int screenW = event.getWindow().getGuiScaledWidth();
        int screenH = event.getWindow().getGuiScaledHeight();
        int[] scopeRect = fitScopeRect(screenW, screenH);
        int scopeX = scopeRect[0];
        int scopeY = scopeRect[1];
        int scopeW = scopeRect[2];
        int scopeH = scopeRect[3];

        double[] sightOffset = projectedSightOffset(scopeW, scopeH, event.getPartialTick());
        double sightScopeX = scopeX + sightOffset[0];
        double sightScopeY = scopeY + sightOffset[1];

        // Disabled: redundant with drawScopeApertureMask() and costs extra GUI fill work every frame.
        // drawScopeBlackBars(graphics, screenW, screenH, scopeX, scopeY, scopeW, scopeH);
        drawScopeApertureMask(graphics, screenW, screenH, scopeX, scopeY, scopeW, scopeH);
        blitZoomedScopeBase(graphics, screenW, screenH, sightScopeX, sightScopeY, scopeW, scopeH);

        drawBallisticMarks(graphics, mc, sightScopeX, sightScopeY, scopeW, scopeH);
        drawFreeLookTargetCircle(graphics, screenW, screenH);

        drawRangefinderText(graphics, mc, sightScopeX, sightScopeY, scopeW, scopeH);

        // Disabled: per-frame debug overlay is expensive (Font rendering + formatting) and was a major hotspot in spark.
        // String debug = ScopeDebug.overlayLine(mc.gameRenderer.getMainCamera());
        // BallisticProfile profile = ClientScopeState.ballisticProfile();
        // if (profile.valid()) {
        //     debug += " | " + profile.cannonType() + " " + profile.projectileId() + " v=" + String.format(java.util.Locale.ROOT, "%.2f", profile.muzzleSpeed());
        // }
        // debug += " | " + ClientScopeState.zoomMagnification() + "x";
        // if (ClientScopeState.freeLookEnabled()) debug += " | freelook";
        // graphics.drawString(mc.font, debug, 8, 8, 0xFFE6E6, true);
    }

    private static void drawFreeLookTargetCircle(GuiGraphics graphics, int screenW, int screenH) {
        if (!ClientScopeState.freeLookEnabled()) {
            return;
        }
        int cx = screenW / 2;
        int cy = screenH / 2;
        int color = 0xFFFFFFFF;
        graphics.fill(cx - 1, cy - 5, cx + 2, cy - 3, color);
        graphics.fill(cx - 1, cy + 4, cx + 2, cy + 6, color);
        graphics.fill(cx - 5, cy - 1, cx - 3, cy + 2, color);
        graphics.fill(cx + 4, cy - 1, cx + 6, cy + 2, color);
    }

    private static void drawRangefinderText(GuiGraphics graphics, Minecraft mc, double x, double y0, int w, int h) {
        long timeSince = net.minecraft.Util.getMillis() - ClientScopeState.rangefinderTimestamp();

        // Hide if not triggered, or after 7 seconds
        if (ClientScopeState.rangefinderTimestamp() == 0L || timeSince > 7000) {
            return;
        }

        int cx = (int) Math.round(x + w / 2.0);
        int cy = (int) Math.round(y0 + h / 2.0);
        int elementX = cx + 30;
        int elementY = cy + 30;

        if (timeSince < 3000) {
            // 1. MEASURING PHASE
            int color = 0xFF22FF22;
            graphics.drawString(mc.font, "RNG: CALC", elementX, elementY, color, false);

            int barWidth = 53;
            int barHeight = 2;
            int progress = (int) ((timeSince / 3000.0f) * barWidth);
            int barY = elementY + 10;

            graphics.fill(elementX, barY, elementX + barWidth, barY + barHeight, 0xFF114411);
            graphics.fill(elementX, barY, elementX + progress, barY + barHeight, color);

        } else {
            // 2. RESULT PHASE
            double distance = ClientScopeState.rangefinderDistance();
            if (distance >= 0) {
                // Success: Draw green distance
                String text = "RNG: " + Math.round(distance);
                graphics.drawString(mc.font, text, elementX, elementY, 0xFF22FF22, false);
            } else {
                // Failure: Dynamically display the max range from the config!
                int maxDisplay = (int) Math.round(com.erika.vsanalogwarfare.config.CommonConfig.maxRangefinderDistance());
                graphics.drawString(mc.font, "RNG: > " + maxDisplay, elementX, elementY, 0xFFFF2222, false);
            }
        }
    }

    private static double[] projectedSightOffset(int scopeW, int scopeH, float partialTick) {
        if (!ClientScopeState.freeLookEnabled()) {
            return new double[]{0.0, 0.0};
        }
        CameraPose camera = ClientScopeState.cameraPose(partialTick);
        CameraPose sight = ClientScopeState.sightPose(partialTick);
        double pxPerRad = scopeH / Math.max(0.001, Math.toRadians(ClientScopeState.fov()));
        Vec3 forward = camera.direction();
        Vec3 left = camera.left();
        Vec3 up = camera.up();

        // CHANGED: Correct the sight direction so the scope aperture tracks the zeroed crosshair,
        // rather than flying off-screen to follow the raw elevated barrel!
        double zeroPitch = ClientScopeState.getZeroPitch();
        Vec3 sightDir;
        if (zeroPitch > 0) {
            // Apply the zeroing drop to the physical barrel's pitch to find the virtual zeroed aim point
            sightDir = ClientScopeState.directionFromYawPitch(sight.yaw(), (float)(sight.pitch() + zeroPitch));
        } else {
            sightDir = sight.direction();
        }

        double forwardDot = clamp(sightDir.dot(forward), -1.0, 1.0);
        double leftDot = sightDir.dot(left);
        double upDot = sightDir.dot(up);
        double dx = -Math.atan2(leftDot, forwardDot) * pxPerRad;
        double dy = -Math.atan2(upDot, forwardDot) * pxPerRad;
        return new double[]{dx, dy};
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void drawScopeApertureMask(GuiGraphics graphics, int screenW, int screenH, int scopeX, int scopeY, int scopeW, int scopeH) {
        int black = 0xFF000000;
        int padding = 1;
        int left = Math.max(0, scopeX + padding);
        int top = Math.max(0, scopeY + padding);
        int right = Math.min(screenW, scopeX + scopeW - padding);
        int bottom = Math.min(screenH, scopeY + scopeH - padding);

        if (left >= right || top >= bottom) {
            graphics.fill(0, 0, screenW, screenH, black);
            return;
        }

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        
        Matrix4f matrix = graphics.pose().last().pose();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        
        float a = 1.0f;
        
        if (top > 0) {
            buffer.vertex(matrix, 0, 0, 0).color(0, 0, 0, a).endVertex();
            buffer.vertex(matrix, 0, top, 0).color(0, 0, 0, a).endVertex();
            buffer.vertex(matrix, screenW, top, 0).color(0, 0, 0, a).endVertex();
            buffer.vertex(matrix, screenW, 0, 0).color(0, 0, 0, a).endVertex();
        }
        
        if (bottom < screenH) {
            buffer.vertex(matrix, 0, bottom, 0).color(0, 0, 0, a).endVertex();
            buffer.vertex(matrix, 0, screenH, 0).color(0, 0, 0, a).endVertex();
            buffer.vertex(matrix, screenW, screenH, 0).color(0, 0, 0, a).endVertex();
            buffer.vertex(matrix, screenW, bottom, 0).color(0, 0, 0, a).endVertex();
        }
        
        if (left > 0 && bottom > top) {
            buffer.vertex(matrix, 0, top, 0).color(0, 0, 0, a).endVertex();
            buffer.vertex(matrix, 0, bottom, 0).color(0, 0, 0, a).endVertex();
            buffer.vertex(matrix, left, bottom, 0).color(0, 0, 0, a).endVertex();
            buffer.vertex(matrix, left, top, 0).color(0, 0, 0, a).endVertex();
        }
        
        if (right < screenW && bottom > top) {
            buffer.vertex(matrix, right, top, 0).color(0, 0, 0, a).endVertex();
            buffer.vertex(matrix, right, bottom, 0).color(0, 0, 0, a).endVertex();
            buffer.vertex(matrix, screenW, bottom, 0).color(0, 0, 0, a).endVertex();
            buffer.vertex(matrix, screenW, top, 0).color(0, 0, 0, a).endVertex();
        }
        
        tesselator.end();
    }

    private static void drawScopeBlackBars(GuiGraphics graphics, int screenW, int screenH, int scopeX, int scopeY, int scopeW, int scopeH) {
        int black = 0xFF000000;
        if (scopeY > 0) {
            graphics.fill(0, 0, screenW, scopeY, black);
        }
        int bottom = scopeY + scopeH;
        if (bottom < screenH) {
            graphics.fill(0, bottom, screenW, screenH, black);
        }
        if (scopeX > 0) {
            graphics.fill(0, scopeY, scopeX, bottom, black);
        }
        int right = scopeX + scopeW;
        if (right < screenW) {
            graphics.fill(right, scopeY, screenW, bottom, black);
        }
    }

    private static void blitZoomedScopeBase(GuiGraphics graphics, int screenW, int screenH, double x, double y, int w, int h) {
        float zoom = Math.max(3.0f, ClientScopeState.animatedZoom());

        // Base enlargement multiplier. You can tweak this number (e.g., 1.15f or 1.35f) to get the perfect size.
        float baseEnlargement = 1.25f;

        // Apply the enlargement to the base scale
        float scale = Math.max(1.0f, zoom / 3.0f) * baseEnlargement;

        int drawW = Math.round(w * scale);
        int drawH = Math.round(h * scale);
        double drawX = x + w / 2.0 - drawW / 2.0;
        double drawY = y + h / 2.0 - drawH / 2.0;
        fillBeyondScopeBase(graphics, screenW, screenH, drawX, drawY, drawW, drawH);

        int baseX = (int) Math.floor(drawX);
        int baseY = (int) Math.floor(drawY);
        double fracX = drawX - baseX;
        double fracY = drawY - baseY;
        graphics.pose().pushPose();
        graphics.pose().translate(fracX, fracY, 0.0D);
        graphics.blit(SCOPE_BASE, baseX, baseY, drawW, drawH, 0.0f, 0.0f, 1280, 720, 1280, 720);
        graphics.pose().popPose();
    }

    private static void fillBeyondScopeBase(GuiGraphics graphics, int screenW, int screenH, double x, double y, int w, int h) {
        int padding = 1;

        int left = Math.max(0, (int) Math.floor(x) + padding);
        int top = Math.max(0, (int) Math.floor(y) + padding);
        int right = Math.min(screenW, (int) Math.ceil(x + w) - padding);
        int bottom = Math.min(screenH, (int) Math.ceil(y + h) - padding);

        if (left >= right || top >= bottom) {
            graphics.fill(0, 0, screenW, screenH, 0xFF000000);
            return;
        }

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        
        Matrix4f matrix = graphics.pose().last().pose();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        
        float a = 1.0f;
        
        if (top > 0) {
            buffer.vertex(matrix, 0, 0, 0).color(0, 0, 0, a).endVertex();
            buffer.vertex(matrix, 0, top, 0).color(0, 0, 0, a).endVertex();
            buffer.vertex(matrix, screenW, top, 0).color(0, 0, 0, a).endVertex();
            buffer.vertex(matrix, screenW, 0, 0).color(0, 0, 0, a).endVertex();
        }
        
        if (bottom < screenH) {
            buffer.vertex(matrix, 0, bottom, 0).color(0, 0, 0, a).endVertex();
            buffer.vertex(matrix, 0, screenH, 0).color(0, 0, 0, a).endVertex();
            buffer.vertex(matrix, screenW, screenH, 0).color(0, 0, 0, a).endVertex();
            buffer.vertex(matrix, screenW, bottom, 0).color(0, 0, 0, a).endVertex();
        }
        
        if (left > 0 && bottom > top) {
            buffer.vertex(matrix, 0, top, 0).color(0, 0, 0, a).endVertex();
            buffer.vertex(matrix, 0, bottom, 0).color(0, 0, 0, a).endVertex();
            buffer.vertex(matrix, left, bottom, 0).color(0, 0, 0, a).endVertex();
            buffer.vertex(matrix, left, top, 0).color(0, 0, 0, a).endVertex();
        }
        
        if (right < screenW && bottom > top) {
            buffer.vertex(matrix, right, top, 0).color(0, 0, 0, a).endVertex();
            buffer.vertex(matrix, right, bottom, 0).color(0, 0, 0, a).endVertex();
            buffer.vertex(matrix, screenW, bottom, 0).color(0, 0, 0, a).endVertex();
            buffer.vertex(matrix, screenW, top, 0).color(0, 0, 0, a).endVertex();
        }
        
        tesselator.end();
    }

    private static int[] fitScopeRect(int screenW, int screenH) {
        double textureAspect = 1280.0 / 720.0;
        int drawW = screenW;
        int drawH = (int) Math.round(drawW / textureAspect);
        if (drawH > screenH) {
            drawH = screenH;
            drawW = (int) Math.round(drawH * textureAspect);
        }
        int drawX = (screenW - drawW) / 2;
        int drawY = (screenH - drawH) / 2;
        return new int[]{drawX, drawY, drawW, drawH};
    }

    // Pre-cached strings remain here at the top!
    private static final String[] PRE_CACHED_RANGES = new String[201];
    static {
        for (int i = 0; i < PRE_CACHED_RANGES.length; i++) {
            PRE_CACHED_RANGES[i] = String.valueOf(i * 100);
        }
    }

    private static void drawBallisticMarks(GuiGraphics graphics, Minecraft mc, double x, double y0, int w, int h) {
        BallisticProfile profile = ClientScopeState.ballisticProfile();
        if (!profile.valid()) {
            return;
        }

        double pxPerDegree = h / Math.max(1.0, ClientScopeState.fov());
        double cx = x + w / 2.0;

        int currentZeroDistance = ClientScopeState.sightZeroDistance();
        double zeroOffsetPixels = ClientScopeState.getZeroPitch() * pxPerDegree;
        double cy = (y0 + h / 2.0) - zeroOffsetPixels;

        int markColor = 0xE0000000;
        int textColor = 0xD0101010;
        int thickness = Math.max(1, Math.round((h / 720.0f) * (ClientScopeState.animatedZoom() / 3.0f)));
        java.util.List<ReticleMark> marks = ClientScopeState.reticleMarks();

        float a = ((markColor >> 24) & 0xFF) / 255.0f;
        float r = ((markColor >> 16) & 0xFF) / 255.0f;
        float g = ((markColor >> 8) & 0xFF) / 255.0f;
        float b = (markColor & 0xFF) / 255.0f;

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        
        Matrix4f matrix = graphics.pose().last().pose();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        
        int lastLineY = -999;
        for (ReticleMark mark : marks) {
            int y = (int) Math.round(cy + mark.pitchDegrees() * pxPerDegree);

            if (y < y0 || y >= y0 + h) continue;

            if (mark.distance() % 500 != 0 && Math.abs(y - lastLineY) < 4) {
                continue;
            }
            lastLineY = y;

            int half = mark.distance() % 500 == 0 ? 6 : 4;
            int y0Line = y - thickness / 2;
            int ix = (int) Math.round(cx);
            int y1Line = y0Line + thickness;

            buffer.vertex(matrix, ix - half, y0Line, 0).color(r, g, b, a).endVertex();
            buffer.vertex(matrix, ix - half, y1Line, 0).color(r, g, b, a).endVertex();
            buffer.vertex(matrix, ix + half + 1, y1Line, 0).color(r, g, b, a).endVertex();
            buffer.vertex(matrix, ix + half + 1, y0Line, 0).color(r, g, b, a).endVertex();
        }
        
        tesselator.end();

        net.minecraft.client.gui.Font font = mc.font;
        int lastTextY = -999;
        for (ReticleMark mark : marks) {
            if (mark.distance() % 100 != 0) continue;

            int y = (int) Math.round(cy + mark.pitchDegrees() * pxPerDegree);
            if (y < y0 || y >= y0 + h) continue;

            if (mark.distance() % 500 != 0 && Math.abs(y - lastTextY) < 9) {
                continue;
            }
            lastTextY = y;

            int cacheIdx = mark.distance() / 100;
            String textToDraw = (cacheIdx >= 0 && cacheIdx < PRE_CACHED_RANGES.length)
                    ? PRE_CACHED_RANGES[cacheIdx]
                    : String.valueOf(mark.distance());

            int half = mark.distance() % 500 == 0 ? 6 : 4;
            int ix = (int) Math.round(cx);

            graphics.drawString(font, textToDraw, ix + half + 4, y - 4, textColor, false);
        }

        String zeroText = "ZRN: " + currentZeroDistance + "m";
        int textX = (int) Math.round(cx - 65);
        int textY = (int) Math.round((y0 + h / 2.0) + 30);
        graphics.drawString(font, zeroText, textX, textY, 0xFF22FF22, false);
    }

    @SubscribeEvent
    public static void onMouseScroll(net.minecraftforge.client.event.InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (ClientScopeState.active() && mc.player != null) {
            if (ClientKeyMappings.SCOPE_ZEROING.isDown()) {
                double scrollDelta = event.getScrollDelta();
                if (scrollDelta != 0) {
                    int currentZero = ClientScopeState.sightZeroDistance();
                    int change = scrollDelta > 0 ? 50 : -50;
                    int newZero = currentZero + change;

                    double maxDist = com.erika.vsanalogwarfare.config.CommonConfig.maxRangefinderDistance();
                    newZero = Math.max(0, Math.min((int) maxDist, newZero));

                    if (newZero != currentZero) {
                        // Calculate how much the angle drops between the old distance and the new distance
                        double oldPitch = ClientScopeState.getZeroPitch();
                        ClientScopeState.setSightZeroDistance(newZero);
                        double newPitch = ClientScopeState.getZeroPitch();

                        float deltaPitch = (float) (newPitch - oldPitch);
                        BlockPos mountPos = ClientScopeState.mountPos();

                        // Tell the server to physically turn the elevation handwheel by that amount!
                        if (mountPos != null && deltaPitch != 0) {
                            com.erika.vsanalogwarfare.network.ModNetwork.sendToServer(
                                    new com.erika.vsanalogwarfare.network.AdjustMountPitchPacket(mountPos, deltaPitch)
                            );
                        }

                        mc.player.playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.get(), 0.3F, 1.5F);
                    }
                    event.setCanceled(true);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        // If the player is scoped and the config is set to block interactions
        if (event.getLevel().isClientSide && ClientScopeState.active() && com.erika.vsanalogwarfare.config.ClientConfig.disablePlayerBlockInteractionWhileScoped()) {

            // DENY the block interaction (e.g., prevents opening doors, flipping levers, opening chests)
            event.setUseBlock(net.minecraftforge.eventbus.api.Event.Result.DENY);

            // ALLOW the item interaction (e.g., forces the Redstone Controller or food to be used instead)
            event.setUseItem(net.minecraftforge.eventbus.api.Event.Result.ALLOW);
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(net.minecraftforge.event.entity.player.PlayerInteractEvent.LeftClickBlock event) {
        // Prevent the player from mining/breaking blocks while scoped
        if (event.getLevel().isClientSide && ClientScopeState.active() && com.erika.vsanalogwarfare.config.ClientConfig.disablePlayerBlockInteractionWhileScoped()) {
            event.setCanceled(true);
        }
    }

}
