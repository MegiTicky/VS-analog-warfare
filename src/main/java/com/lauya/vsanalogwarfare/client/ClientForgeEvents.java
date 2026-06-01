package com.lauya.vsanalogwarfare.client;

import com.lauya.vsanalogwarfare.VSAnalogWarfare;
import com.lauya.vsanalogwarfare.config.ClientConfig;
import com.lauya.vsanalogwarfare.config.CommonConfig;
import com.lauya.vsanalogwarfare.network.MouseAimTargetPacket;
import com.lauya.vsanalogwarfare.network.ModNetwork;
import com.lauya.vsanalogwarfare.scope.ballistics.BallisticProfile;
import com.lauya.vsanalogwarfare.scope.ballistics.ReticleMark;
import com.lauya.vsanalogwarfare.network.StopScopePacket;
import com.lauya.vsanalogwarfare.network.ToggleScopeZoomPacket;
import com.lauya.vsanalogwarfare.scope.rig.CameraPose;
import net.minecraft.client.Minecraft;
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

@Mod.EventBusSubscriber(modid = VSAnalogWarfare.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientForgeEvents {
    private static final ResourceLocation SCOPE_BASE = new ResourceLocation(VSAnalogWarfare.MOD_ID, "textures/misc/scope_base.png");
    private static boolean shiftWasDown;
    private static int mouseAimPacketCooldown;

    // Temporary debug switch: disable VSAW-applied camera roll so we can see whether
    // VS/other mods are already rolling the view and/or whether our roll math is wrong.
    private static final boolean DEBUG_DISABLE_VSAW_ROLL = false;

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
        if (DEBUG_DISABLE_VSAW_ROLL) {
            event.setRoll(30.0f);
        } else {
            event.setRoll(ClientScopeState.roll(pose));
        }
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
        Vec3 direction = ClientScopeState.freeLookDirection();
        ModNetwork.sendToServer(new MouseAimTargetPacket(scopePos, mountPos, direction.x, direction.y, direction.z));
    }

    @SubscribeEvent
    public static void onInteractionKeyMappingTriggered(InputEvent.InteractionKeyMappingTriggered event) {
        if (!ClientScopeState.active() || !ClientConfig.disablePlayerBlockInteractionWhileScoped()) {
            return;
        }
        if (event.isAttack() || event.isUseItem()) {
            event.setSwingHand(false);
            event.setCanceled(true);
        }
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
        Vec3 sightDir = sight.direction();
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
        int padding = 1; // pixels to shrink from each side
        int left = Math.max(0, scopeX + padding);
        int top = Math.max(0, scopeY + padding);
        int right = Math.min(screenW, scopeX + scopeW - padding);
        int bottom = Math.min(screenH, scopeY + scopeH - padding);

        if (top > 0) {
            graphics.fill(0, 0, screenW, top, black);
        }
        if (bottom < screenH) {
            graphics.fill(0, bottom, screenW, screenH, black);
        }
        if (left > 0 && bottom > top) {
            graphics.fill(0, top, left, bottom, black);
        }
        if (right < screenW && bottom > top) {
            graphics.fill(right, top, screenW, bottom, black);
        }
        if (left >= right || top >= bottom) {
            graphics.fill(0, 0, screenW, screenH, black);
        }
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
        float scale = Math.max(1.0f, zoom / 3.0f);
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
        int black = 0xFF000000;
        int padding = 1; // inward amount in pixels

        int left = Math.max(0, (int) Math.floor(x) + padding);
        int top = Math.max(0, (int) Math.floor(y) + padding);
        int right = Math.min(screenW, (int) Math.ceil(x + w) - padding);
        int bottom = Math.min(screenH, (int) Math.ceil(y + h) - padding);

        if (top > 0) {
            graphics.fill(0, 0, screenW, top, black);
        }
        if (bottom < screenH) {
            graphics.fill(0, bottom, screenW, screenH, black);
        }
        if (left > 0 && bottom > top) {
            graphics.fill(0, top, left, bottom, black);
        }
        if (right < screenW && bottom > top) {
            graphics.fill(right, top, screenW, bottom, black);
        }
        if (left >= right || top >= bottom) {
            graphics.fill(0, 0, screenW, screenH, black);
        }
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

    private static void drawBallisticMarks(GuiGraphics graphics, Minecraft mc, double x, double y0, int w, int h) {
        BallisticProfile profile = ClientScopeState.ballisticProfile();
        if (!profile.valid()) {
            return;
        }
        double pxPerDegree = h / Math.max(1.0, ClientScopeState.fov());
        double cx = x + w / 2.0;
        double cy = y0 + h / 2.0;
        int markColor = 0xE0000000;
        int textColor = 0xD0101010;
        int thickness = Math.max(1, Math.round((h / 720.0f) * (ClientScopeState.animatedZoom() / 3.0f)));
        for (ReticleMark mark : ClientScopeState.reticleMarks()) {
            int y = (int) Math.round(cy + mark.pitchDegrees() * pxPerDegree);
            if (y < y0 || y >= y0 + h) {
                continue;
            }
            int half = mark.distance() % 500 == 0 ? 6 : 4;
            int y0Line = y - thickness / 2;
            int ix = (int) Math.round(cx);
            graphics.fill(ix - half, y0Line, ix + half + 1, y0Line + thickness, markColor);
            if (mark.distance() % 100 == 0) {
                graphics.drawString(mc.font, Integer.toString(mark.distance()), ix + half + 4, y - 4, textColor, false);
            }
        }
    }

}
