package com.erika.vsanalogwarfare.client;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniondc;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.slf4j.Logger;

import java.util.Locale;

public final class ScopeDebug {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String BUILD_MARKER = "scope-client-frame-debug-2026-06-13-vs-agnostic";

    private static final ThreadLocal<Boolean> IN_VS_MOUNTED_CAMERA_WRAPPER = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private static String lastHook = "none";
    private static float lastShipYaw = Float.NaN;
    private static float lastCameraForwardYaw = Float.NaN;
    private static long lastClientLogMs;
    private static long lastPoseSkipLogMs;
    private static long lastReapplyLogMs;

    private ScopeDebug() {
    }

    public static void logLoaded() {
        LOGGER.info("[VSAW_SCOPE] loaded build={}", BUILD_MARKER);
    }

    public static void cameraHook(String hook, Object shipMountedTo, Camera camera) {
        lastHook = hook;
        lastShipYaw = shipMountedTo == null ? Float.NaN : extractShipYaw(shipMountedTo);
        lastCameraForwardYaw = yawFromVector(camera.getLookVector());

        long now = System.currentTimeMillis();
        if (now - lastClientLogMs >= 1000L) {
            lastClientLogMs = now;
            LOGGER.info("[VSAW_SCOPE] client hook={} shipYaw={} scopeYaw={} scopePitch={} roll={} camForwardYaw={} camYRot={} camXRot={} pos={}",
                    lastHook,
                    fmt(lastShipYaw),
                    fmt(ClientScopeState.yaw()),
                    fmt(ClientScopeState.pitch()),
                    fmt(ClientScopeState.roll()),
                    fmt(lastCameraForwardYaw),
                    fmt(camera.getYRot()),
                    fmt(camera.getXRot()),
                    fmt(ClientScopeState.cameraPosition()));
        }
    }

    public static String overlayLine(Camera camera) {
        float liveForwardYaw = camera == null ? Float.NaN : yawFromVector(camera.getLookVector());
        return "VSAW " + BUILD_MARKER
                + " hook=" + lastHook
                + " shipYaw=" + fmt(lastShipYaw)
                + " scopeYaw=" + fmt(ClientScopeState.yaw())
                + " roll=" + fmt(ClientScopeState.roll())
                + " camYaw=" + (camera == null ? "n/a" : fmt(camera.getYRot()))
                + " fwdYaw=" + fmt(liveForwardYaw);
    }

    public static boolean shouldSkipVsMountedPoseRotation() {
        if (!ClientScopeState.active()) {
            return false;
        }
        return IN_VS_MOUNTED_CAMERA_WRAPPER.get();
    }

    public static void poseRotationSkipped(Quaternionf quaternion) {
        long now = System.currentTimeMillis();
        if (now - lastPoseSkipLogMs >= 1000L) {
            lastPoseSkipLogMs = now;
            LOGGER.info("[VSAW_SCOPE] skipped VS mounted PoseStack rotation q=({}, {}, {}, {})",
                    fmt(quaternion.x), fmt(quaternion.y), fmt(quaternion.z), fmt(quaternion.w));
        }
    }

    public static void enterVsMountedCameraWrapper() {
        IN_VS_MOUNTED_CAMERA_WRAPPER.set(Boolean.TRUE);
    }

    public static void exitVsMountedCameraWrapper() {
        IN_VS_MOUNTED_CAMERA_WRAPPER.set(Boolean.FALSE);
    }

    public static void logScopeReappliedAfterVs() {
        long now = System.currentTimeMillis();
        if (now - lastReapplyLogMs >= 1000L) {
            lastReapplyLogMs = now;
            LOGGER.info("[VSAW_SCOPE] re-applied scope pose after VS ship-mounted camera setup");
        }
    }

    private static float extractShipYaw(Object shipMountedTo) {
        try {
            Object renderTransform = shipMountedTo.getClass().getMethod("getRenderTransform").invoke(shipMountedTo);
            Object rotation = renderTransform.getClass().getMethod("getShipToWorldRotation").invoke(renderTransform);
            Quaternionf q = new Quaternionf();
            if (rotation instanceof Quaternionf qf) {
                q.set(qf);
            } else if (rotation instanceof Quaternionfc qfc) {
                q.set(qfc);
            } else if (rotation instanceof Quaterniondc qdc) {
                q.set(qdc);
            } else {
                return Float.NaN;
            }
            Vector3f shipPlusX = q.transform(new Vector3f(1.0f, 0.0f, 0.0f));
            return yawFromVector(shipPlusX);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return Float.NaN;
        }
    }

    private static float yawFromVector(Vector3f vector) {
        return (float) Math.toDegrees(Math.atan2(vector.z(), vector.x()));
    }

    private static String fmt(float value) {
        return Float.isNaN(value) ? "n/a" : String.format(Locale.ROOT, "%.1f", value);
    }

    private static String fmt(Vec3 vec) {
        return String.format(Locale.ROOT, "%.2f,%.2f,%.2f", vec.x, vec.y, vec.z);
    }
}
