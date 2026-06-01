package com.lauya.vsanalogwarfare.client;

import com.lauya.vsanalogwarfare.config.ClientConfig;
import com.lauya.vsanalogwarfare.scope.ScopeBlockEntity;
import com.lauya.vsanalogwarfare.scope.ballistics.BallisticProfile;
import com.lauya.vsanalogwarfare.scope.ballistics.BallisticSolver;
import com.lauya.vsanalogwarfare.scope.ballistics.ReticleMark;
import com.lauya.vsanalogwarfare.scope.rig.CameraPose;
import com.lauya.vsanalogwarfare.scope.rig.FixedCoaxScopeRig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.List;

public final class ClientScopeState {
    private static boolean active;
    private static float targetFov = 70.0f;
    private static float animationStartFov = 70.0f;
    private static float visualFov = 70.0f;
    private static float targetZoom = 3.0f;
    private static float animationStartZoom = 3.0f;
    private static float visualZoom = 3.0f;
    private static int zoomMagnification = 3;
    private static long zoomAnimationStartMillis;
    private static final long ZOOM_ANIMATION_MILLIS = 200L;
    @Nullable
    private static BlockPos scopePos;
    @Nullable
    private static BlockPos mountPos;
    private static CameraPose fallbackPose = new CameraPose(Vec3.ZERO, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f);
    private static BallisticProfile ballisticProfile = BallisticProfile.EMPTY;
    private static List<ReticleMark> reticleMarks = List.of();
    private static boolean freeLookEnabled;
    private static float freeLookYaw;
    private static float freeLookPitch;

    private ClientScopeState() {
    }

    public static boolean active() {
        return active;
    }

    public static float fov() {
        updateZoomAnimation();
        return visualFov;
    }

    public static float animatedZoom() {
        updateZoomAnimation();
        return visualZoom;
    }

    public static float mouseSensitivityScale() {
        return Math.max(0.02f, (float) ((fov() / 70.0f) * ClientConfig.scopeZoomSensitivityMultiplier()));
    }

    public static int zoomMagnification() {
        return zoomMagnification;
    }

    public static boolean freeLookEnabled() {
        return active && freeLookEnabled;
    }

    public static BallisticProfile ballisticProfile() {
        return ballisticProfile == null ? BallisticProfile.EMPTY : ballisticProfile;
    }

    @Nullable
    public static BlockPos scopePos() {
        return scopePos;
    }

    @Nullable
    public static BlockPos mountPos() {
        return mountPos;
    }

    public static List<ReticleMark> reticleMarks() {
        return reticleMarks;
    }

    public static CameraPose sightPose(float partialTick) {
        return currentPose(partialTick);
    }

    public static CameraPose currentPose(float partialTick) {
        if (!active) {
            return fallbackPose;
        }

        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null || scopePos == null || mountPos == null || !level.isLoaded(scopePos)) {
            return fallbackPose;
        }

        BlockEntity blockEntity = level.getBlockEntity(scopePos);
        if (!(blockEntity instanceof ScopeBlockEntity scope)) {
            return fallbackPose;
        }

        try {
            return new FixedCoaxScopeRig(scope, mountPos).getCameraPose(partialTick);
        } catch (RuntimeException ignored) {
            return fallbackPose;
        }
    }

    public static CameraPose cameraPose(float partialTick) {
        CameraPose sight = currentPose(partialTick);
        if (!freeLookEnabled()) {
            return sight;
        }
        return CameraPose.looking(sight.position(), directionFromYawPitch(freeLookYaw, freeLookPitch), new Vec3(0.0, 1.0, 0.0));
    }

    public static Vec3 freeLookDirection() {
        return directionFromYawPitch(freeLookYaw, freeLookPitch);
    }

    public static void toggleFreeLook() {
        if (!active) {
            freeLookEnabled = false;
            return;
        }
        freeLookEnabled = !freeLookEnabled;
        if (freeLookEnabled) {
            CameraPose pose = currentPose(1.0f);
            freeLookYaw = pose.yaw();
            freeLookPitch = pose.pitch();
        }
    }

    public static void addFreeLookInput(double deltaYaw, double deltaPitch) {
        if (!freeLookEnabled()) {
            return;
        }
        freeLookYaw = wrapDegrees((float) (freeLookYaw + deltaYaw));
        freeLookPitch = clamp((float) (freeLookPitch + deltaPitch), -89.9f, 89.9f);
    }

    public static Vec3 directionFromYawPitch(float yaw, float pitch) {
        double yawRad = Math.toRadians(yaw + 90.0f);
        double pitchRad = Math.toRadians(pitch);
        double horizontal = Math.cos(pitchRad);
        return new Vec3(Math.cos(yawRad) * horizontal, -Math.sin(pitchRad), Math.sin(yawRad) * horizontal).normalize();
    }

    public static Vec3 cameraPosition() {
        return currentPose(1.0f).position();
    }

    public static Vec3 cameraPosition(float partialTick) {
        return currentPose(partialTick).position();
    }

    public static float yaw() {
        return currentPose(1.0f).yaw();
    }

    public static float yaw(float partialTick) {
        return currentPose(partialTick).yaw();
    }

    public static float pitch() {
        return currentPose(1.0f).pitch();
    }

    public static float pitch(float partialTick) {
        return currentPose(partialTick).pitch();
    }

    public static float qx() {
        return currentPose(1.0f).qx();
    }

    public static float qy() {
        return currentPose(1.0f).qy();
    }

    public static float qz() {
        return currentPose(1.0f).qz();
    }

    public static float qw() {
        return currentPose(1.0f).qw();
    }

    public static Quaternionf quaternion(float partialTick) {
        CameraPose pose = cameraPose(partialTick);
        return new Quaternionf(pose.qx(), pose.qy(), pose.qz(), pose.qw()).normalize();
    }

    public static float roll() {
        return roll(1.0f);
    }

    public static float roll(float partialTick) {
        return roll(currentPose(partialTick));
    }

    public static float roll(CameraPose pose) {
        Quaternionf desired = new Quaternionf(pose.qx(), pose.qy(), pose.qz(), pose.qw()).normalize();
        Quaternionf upright = new Quaternionf().rotationYXZ(
                (float) Math.toRadians(-pose.yaw()),
                (float) Math.toRadians(pose.pitch()),
                0.0f
        );

        Vector3f forward = upright.transform(new Vector3f(0.0f, 0.0f, 1.0f)).normalize();
        Vector3f uprightUp = upright.transform(new Vector3f(0.0f, 1.0f, 0.0f)).normalize();
        Vector3f desiredUp = desired.transform(new Vector3f(0.0f, 1.0f, 0.0f)).normalize();

        float sin = forward.dot(uprightUp.cross(desiredUp, new Vector3f()));
        float cos = uprightUp.dot(desiredUp);
        return (float) Math.toDegrees(Math.atan2(sin, cos));
    }

    public static void set(boolean active, float fov, int zoomMagnification, @Nullable BlockPos scopePos, @Nullable BlockPos mountPos,
                           double x, double y, double z, float yaw, float pitch,
                           float qx, float qy, float qz, float qw, BallisticProfile profile) {
        boolean wasActive = ClientScopeState.active;
        ClientScopeState.active = active;
        int newZoom = active ? zoomMagnification : 3;
        if (!active) {
            freeLookEnabled = false;
        }
        if (!wasActive || !active) {
            ClientScopeState.targetFov = fov;
            ClientScopeState.animationStartFov = fov;
            ClientScopeState.visualFov = fov;
            ClientScopeState.targetZoom = newZoom;
            ClientScopeState.animationStartZoom = newZoom;
            ClientScopeState.visualZoom = newZoom;
            ClientScopeState.zoomAnimationStartMillis = net.minecraft.Util.getMillis();
        } else if (Math.abs(ClientScopeState.targetFov - fov) > 1.0e-4f || ClientScopeState.zoomMagnification != newZoom) {
            updateZoomAnimation();
            ClientScopeState.animationStartFov = ClientScopeState.visualFov;
            ClientScopeState.animationStartZoom = ClientScopeState.visualZoom;
            ClientScopeState.targetFov = fov;
            ClientScopeState.targetZoom = newZoom;
            ClientScopeState.zoomAnimationStartMillis = net.minecraft.Util.getMillis();
        } else {
            ClientScopeState.targetFov = fov;
            ClientScopeState.targetZoom = newZoom;
        }
        ClientScopeState.zoomMagnification = newZoom;
        ClientScopeState.scopePos = scopePos;
        ClientScopeState.mountPos = mountPos;
        ClientScopeState.fallbackPose = new CameraPose(new Vec3(x, y, z), yaw, pitch, qx, qy, qz, qw);
        BallisticProfile newProfile = active && profile != null ? profile : BallisticProfile.EMPTY;
        if (!newProfile.equals(ClientScopeState.ballisticProfile)) {
            ClientScopeState.ballisticProfile = newProfile;
            ClientScopeState.reticleMarks = newProfile.valid()
                    ? BallisticSolver.generateMarks(newProfile, BallisticSolver.DEFAULT_INTERVAL, BallisticSolver.DEFAULT_MAX_RANGE)
                    : List.of();
        }
    }
    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float wrapDegrees(float value) {
        value %= 360.0f;
        if (value >= 180.0f) value -= 360.0f;
        if (value < -180.0f) value += 360.0f;
        return value;
    }

    private static void updateZoomAnimation() {
        long elapsed = Math.max(0L, net.minecraft.Util.getMillis() - zoomAnimationStartMillis);
        float t = Math.min(1.0f, elapsed / (float) ZOOM_ANIMATION_MILLIS);
        float eased = t * t * (3.0f - 2.0f * t);
        visualFov = animationStartFov + (targetFov - animationStartFov) * eased;
        visualZoom = animationStartZoom + (targetZoom - animationStartZoom) * eased;
    }

}
