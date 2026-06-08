package com.erika.vsanalogwarfare.client;

import com.erika.vsanalogwarfare.config.ClientConfig;
import com.erika.vsanalogwarfare.network.ModNetwork;
import com.erika.vsanalogwarfare.scope.ScopeBlockEntity;
import com.erika.vsanalogwarfare.scope.ballistics.BallisticProfile;
import com.erika.vsanalogwarfare.scope.ballistics.BallisticSolver;
import com.erika.vsanalogwarfare.scope.ballistics.ReticleMark;
import com.erika.vsanalogwarfare.scope.rig.CameraPose;
import com.erika.vsanalogwarfare.scope.rig.FixedCoaxScopeRig;
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

    // Cache per-frame to avoid repeatedly scanning blocks / reflecting CBC on every getter call.
    private static int cachedFrameId = Integer.MIN_VALUE;
    private static float cachedPartialTick = Float.NaN;
    private static CameraPose cachedSightPose = fallbackPose;
    private static CameraPose cachedCameraPose = fallbackPose;

    // For rangefinder
    private static double rangefinderDistance = -1.0;
    private static long rangefinderTimestamp = 0L;
    public static double rangefinderDistance() { return rangefinderDistance; }
    public static long rangefinderTimestamp() { return rangefinderTimestamp; }


    // Server-driven rangefinder (VS ship raycast) lands here. triggerRangefinder()
    // also seeds this field with the local vanilla + DH raycasts; the
    // RangefinderResultPacket handler calls this setter to overwrite the value
    // when a ship is closer than the terrain.
    public static void setRangefinderDistance(double distance) {
        rangefinderDistance = distance;
    }

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
        ensureCached(partialTick);
        return cachedSightPose;
    }


    public static CameraPose currentPose(float partialTick) {
        ensureCached(partialTick);
        return cachedSightPose;
    }


    public static CameraPose cameraPose(float partialTick) {
        ensureCached(partialTick);
        return cachedCameraPose;
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
        return cameraPosition(1.0f);
    }


    public static Vec3 cameraPosition(float partialTick) {
        return currentPose(partialTick).position();
    }


    public static float yaw() {
        return yaw(1.0f);
    }


    public static float yaw(float partialTick) {
        return currentPose(partialTick).yaw();
    }


    public static float pitch() {
        return pitch(1.0f);
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

        // Invalidate pose cache immediately (scope toggles, zoom changes, etc.).
        cachedFrameId = Integer.MIN_VALUE;
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


    private static void ensureCached(float partialTick) {
        if (!active) {
            cachedSightPose = fallbackPose;
            cachedCameraPose = fallbackPose;
            cachedFrameId = Integer.MIN_VALUE;
            cachedPartialTick = Float.NaN;
            return;
        }

        // Cache keyed by a coarse time bucket. This keeps pose math to ~once per frame
        // in practice without relying on version-specific Minecraft APIs.
        Minecraft mc = Minecraft.getInstance();
        int frameId = (int) (net.minecraft.Util.getMillis() / 8L);

        if (frameId == cachedFrameId && Math.abs(partialTick - cachedPartialTick) < 1.0e-4f) {
            return;
        }
        cachedFrameId = frameId;
        cachedPartialTick = partialTick;

        Level level = mc.level;
        if (level == null || scopePos == null || mountPos == null || !level.isLoaded(scopePos)) {
            cachedSightPose = fallbackPose;
            cachedCameraPose = fallbackPose;
            return;
        }

        BlockEntity blockEntity = level.getBlockEntity(scopePos);
        if (!(blockEntity instanceof ScopeBlockEntity scope)) {
            cachedSightPose = fallbackPose;
            cachedCameraPose = fallbackPose;
            return;
        }

        try {
            cachedSightPose = new FixedCoaxScopeRig(scope, mountPos).getCameraPose(partialTick);
        } catch (RuntimeException ignored) {
            cachedSightPose = fallbackPose;
        }

        if (!freeLookEnabled()) {
            cachedCameraPose = cachedSightPose;
        } else {
            cachedCameraPose = CameraPose.looking(
                    cachedSightPose.position(),
                    directionFromYawPitch(freeLookYaw, freeLookPitch),
                    new Vec3(0.0, 1.0, 0.0)
            );
        }
    }

    public static void triggerRangefinder() {
        if (!active) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        CameraPose pose = cameraPose(1.0f);
        Vec3 cameraPos = pose.position();
        Vec3 direction = pose.direction();
        double maxRange = 2000.0;

        // Offset raycast start to prevent hitting our own scope glass
        double offset = 1.5;
        Vec3 start = cameraPos.add(direction.scale(offset));
        Vec3 end = cameraPos.add(direction.scale(maxRange));

        // --- 1. VANILLA TERRAIN RAYCAST (Instant/Sync) ---
        net.minecraft.world.level.ClipContext context = new net.minecraft.world.level.ClipContext(
                start, end, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, mc.player);

        net.minecraft.world.phys.BlockHitResult hitResult = mc.level.clip(context);
        double vanillaDistance = hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK
                ? cameraPos.distanceTo(hitResult.getLocation())
                : -1.0;

        // Immediately set distance (UI will see vanilla hit, or start waiting)
        rangefinderDistance = vanillaDistance;
        rangefinderTimestamp = net.minecraft.Util.getMillis();

        // --- 2. DISTANT HORIZONS RAYCAST (Async Background Thread) ---
        if (vanillaDistance < 0) {
            // Push the heavy DH math to a background worker so the game never freezes
            java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                return com.erika.vsanalogwarfare.scope.compat.DhCompat
                        .getDistantHorizonsRaycastDistance(start, direction, maxRange);
            }).thenAccept(dhDist -> {
                if (dhDist > 0) {
                    double finalDhDist = dhDist + offset;

                    // Ignore DH ghost blocks that spawn right in your face
                    if (finalDhDist >= 32.0) {
                        // Safely push the result back to the main rendering thread
                        mc.execute(() -> {
                            double current = rangefinderDistance;
                            if (current < 0 || finalDhDist < current) {
                                rangefinderDistance = finalDhDist;
                            }
                        });
                    }
                }
            });
        }

        // --- 3. VS2 SERVER SHIP RAYCAST (Async via Network Packet) ---
        ModNetwork.sendToServer(new ModNetwork.RangefinderRequestPacket(cameraPos, direction));
    }
}
