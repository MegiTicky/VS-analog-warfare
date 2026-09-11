package com.erika.vsanalogwarfare.client;

import com.erika.vsanalogwarfare.config.ClientConfig;
import com.erika.vsanalogwarfare.network.ModNetwork;
import com.erika.vsanalogwarfare.scope.ScopeBlockEntity;
import com.erika.vsanalogwarfare.scope.ballistics.BallisticProfile;
import com.erika.vsanalogwarfare.scope.ballistics.BallisticSolver;
import com.erika.vsanalogwarfare.scope.ballistics.ReticleMark;
import com.erika.vsanalogwarfare.scope.rig.CameraPose;
import com.erika.vsanalogwarfare.scope.rig.FixedCoaxScopeRig;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.List;



public final class ClientScopeState {
    /** Client-only render mode inside an active scope session; the server session stays active in both. */
    public enum ViewMode { SCOPE, THIRD_PERSON }

    private static boolean active;
    private static ViewMode viewMode = ViewMode.SCOPE;
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
    // Camera type active before the scope's third-person view took over; restored
    // on toggling back or when the session ends.
    @Nullable
    private static CameraType savedCameraType;

    // Cache per-frame to avoid repeatedly scanning blocks / reflecting CBC on every getter call.
    private static int cachedFrameId = Integer.MIN_VALUE;
    private static float cachedPartialTick = Float.NaN;
    private static CameraPose cachedSightPose = fallbackPose;
    private static CameraPose cachedCameraPose = fallbackPose;
    // Angles actually fed to the camera this frame. When the player is mounted
    // to a ship these pre-divide the world-frame pose by the ship's render
    // rotation, so VS2's mounted-camera transform cancels exactly. Render-only:
    // freeLookYaw/freeLookPitch and the aim packet stay world-frame.
    private static float cachedRenderYaw;
    private static float cachedRenderPitch;
    private static float cachedRenderRoll;

    private static double cachedZeroPitch = 0.0;
    private static boolean zeroPitchDirty = true;

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


    public static ViewMode viewMode() {
        return viewMode;
    }


    /** True while the scope session is active AND rendering the scope view (not the raised third-person view). */
    public static boolean scopeViewActive() {
        return active && viewMode == ViewMode.SCOPE;
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


    /** Yaw actually rendered this frame: world-frame yaw with VS seat-rotation compensation applied. */
    public static float renderYaw(float partialTick) {
        ensureCached(partialTick);
        return cachedRenderYaw;
    }


    /** Pitch actually rendered this frame: world-frame pitch with VS seat-rotation compensation applied. */
    public static float renderPitch(float partialTick) {
        ensureCached(partialTick);
        return cachedRenderPitch;
    }


    /** Roll actually rendered this frame: world-frame roll with VS seat-rotation compensation applied. */
    public static float renderRoll(float partialTick) {
        ensureCached(partialTick);
        return cachedRenderRoll;
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
            // The scope rig already returns a world-space pose, including the
            // ship transform. Seed from it directly so mounted free look is not
            // transformed a second time.
            freeLookYaw = pose.yaw();
            freeLookPitch = (float) (pose.pitch() + getZeroPitch());
        }
    }


    public static void toggleViewMode() {
        if (!active) {
            viewMode = ViewMode.SCOPE;
            restoreCameraType();
            return;
        }
        if (viewMode == ViewMode.SCOPE) {
            viewMode = ViewMode.THIRD_PERSON;
            // The scope view teleports the first-person camera, so third person
            // must be requested explicitly or VS's orbit (and the lift) never runs.
            Minecraft mc = Minecraft.getInstance();
            savedCameraType = mc.options.getCameraType();
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            // Seed the player from the rendered scope-camera direction (free-look
            // direction when engaged, sight impact line otherwise) so the
            // third-person camera keeps pointing at the same target. Third person
            // is world-stabilized (player yaw/pitch are its world angles), so the
            // world-frame scope direction maps straight onto the player.
            CameraPose pose = cameraPose(1.0f);
            setPlayerRotation(pose.yaw(), pose.pitch());
        } else {
            viewMode = ViewMode.SCOPE;
            restoreCameraType();
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                // Third person is world-stabilized: the player rotation is already
                // the world look direction, so free look seeds straight from it.
                // The cannon re-elevates by the sight zero on the next aim packet
                // (scope convention: reticle center = impact line, bore above it).
                freeLookYaw = player.getYRot();
                freeLookPitch = clamp(player.getXRot(), -89.9f, 89.9f);
            }
            // Aim packets require free look while in the scope view; third person
            // always aimed, so keep the turret driving across the toggle.
            freeLookEnabled = true;
            replayZoomIn();
        }
        cachedFrameId = Integer.MIN_VALUE;
    }


    private static void restoreCameraType() {
        if (savedCameraType != null) {
            Minecraft.getInstance().options.setCameraType(savedCameraType);
            savedCameraType = null;
        }
    }


    private static void setPlayerRotation(float yaw, float pitch) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        float clampedPitch = clamp(pitch, -89.9f, 89.9f);
        player.setYRot(yaw);
        player.setXRot(clampedPitch);
        player.yRotO = yaw;
        player.xRotO = clampedPitch;
        player.yBodyRot = yaw;
        player.yBodyRotO = yaw;
        player.yHeadRot = yaw;
        player.yHeadRotO = yaw;
    }


    /** Restart the 200 ms zoom animation from the un-zoomed state toward the current scope targets. */
    private static void replayZoomIn() {
        animationStartFov = 70.0f;
        animationStartZoom = 3.0f;
        zoomAnimationStartMillis = net.minecraft.Util.getMillis();
    }

    /** Inverse of {@link #directionFromYawPitch}: Minecraft azimuth in degrees. */
    private static float yawFromDirection(Vec3 direction) {
        return (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
    }

    /** Inverse of {@link #directionFromYawPitch}: elevation in degrees (+ = up). */
    private static float pitchFromDirection(Vec3 direction) {
        double y = Math.max(-1.0D, Math.min(1.0D, direction.y));
        return (float) -Math.toDegrees(Math.asin(y));
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
        return rollOf(new Quaternionf(pose.qx(), pose.qy(), pose.qz(), pose.qw()).normalize(),
                pose.yaw(), pose.pitch());
    }


    /** Twist of {@code rotation} about its forward axis relative to the upright yaw/pitch basis, in degrees. */
    public static float rollOf(Quaternionf rotation, float yawDeg, float pitchDeg) {
        Quaternionf upright = new Quaternionf().rotationYXZ(
                (float) Math.toRadians(-yawDeg),
                (float) Math.toRadians(pitchDeg),
                0.0f
        );

        Vector3f forward = upright.transform(new Vector3f(0.0f, 0.0f, 1.0f)).normalize();
        Vector3f uprightUp = upright.transform(new Vector3f(0.0f, 1.0f, 0.0f)).normalize();
        Vector3f desiredUp = rotation.transform(new Vector3f(0.0f, 1.0f, 0.0f)).normalize();

        float sin = forward.dot(uprightUp.cross(desiredUp, new Vector3f()));
        float cos = uprightUp.dot(desiredUp);
        return (float) Math.toDegrees(Math.atan2(sin, cos));
    }


    public static void set(boolean active, float fov, int zoomMagnification, @Nullable BlockPos scopePos, @Nullable BlockPos mountPos,
                           double x, double y, double z, float yaw, float pitch,
                           float qx, float qy, float qz, float qw, BallisticProfile profile, int zeroDistance,
                           boolean highAngle, float mountMaxDepressionDeg, float mountMaxElevationDeg) {
        boolean wasActive = ClientScopeState.active;
        ClientScopeState.active = active;
        int newZoom = active ? zoomMagnification : 3;

        if (!active) {
            freeLookEnabled = false;
            viewMode = ViewMode.SCOPE;
            restoreCameraType();
            renderZeroPitchSmoothed = Double.NaN;
        }
        // Correctly initialize newProfile and check for updates
        BallisticProfile newProfile = active && profile != null ? profile : BallisticProfile.EMPTY;
        if (!newProfile.equals(ClientScopeState.ballisticProfile)) {
            ClientScopeState.ballisticProfile = newProfile;
            zeroPitchDirty = true;
            apexSolution = null;
            capRangeSolution = null;
            ReticleCache.markDirty();
            ClientScopeState.reticleMarks = newProfile.valid()
                    ? BallisticSolver.generateMarks(newProfile, BallisticSolver.DEFAULT_INTERVAL, BallisticSolver.DEFAULT_MAX_RANGE)
                    : List.of();
        }
        if (active) {
            // Zero is clamped with the FRESH profile so the packet echo can never re-inject a value
            // the wheel clamp would reject (that divergence used to reverse the pitch deltas).
            sightZeroDistance = Math.max(-depressionSpan(), Math.min(zeroCap(), zeroDistance));
            highAngleZero = highAngle;
            if (mountMaxDepressionDeg > 0.0f) maxDepressionDeg = mountMaxDepressionDeg;
            if (mountMaxElevationDeg > 0.0f) maxElevationDeg = mountMaxElevationDeg;
            zeroPitchDirty = true;
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

        if (!active) {
            ReticleCache.cleanup();
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
            applyCachedCameraPose(fallbackPose);
            cachedFrameId = Integer.MIN_VALUE;
            cachedPartialTick = Float.NaN;
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (Float.compare(partialTick, cachedPartialTick) == 0) {
            return;
        }
        cachedPartialTick = partialTick;

        Level level = mc.level;

        if (level == null || scopePos == null || mountPos == null || !level.isLoaded(scopePos)) {
            cachedSightPose = fallbackPose;
            applyCachedCameraPose(fallbackPose);
            return;
        }

        BlockEntity blockEntity = level.getBlockEntity(scopePos);
        if (!(blockEntity instanceof ScopeBlockEntity scope)) {
            cachedSightPose = fallbackPose;
            applyCachedCameraPose(fallbackPose);
            return;
        }

        try {
            cachedSightPose = new FixedCoaxScopeRig(scope, mountPos).getCameraPose(partialTick);
        } catch (RuntimeException ignored) {
            cachedSightPose = fallbackPose;
        }

        if (viewMode == ViewMode.THIRD_PERSON) {
            // Third person: the view is world-stabilized on the player's live look
            // direction. The player yaw/pitch are interpreted as world angles (see
            // CameraMixin) and go through the same ship compensation as the scope,
            // so VS2's mounted-camera transform cancels and the horizon stays
            // level while the ship rolls under the camera.
            LocalPlayer player = mc.player;
            if (player != null) {
                // World-up hint (the 2-arg looking overload), NOT the sight's up:
                // an orbit camera must stay gravity-up. With the sight's up, an
                // elevated cannon couples elevation x turret-yaw-lag into the
                // render roll and the horizon rolls while the turret slews.
                applyCachedCameraPose(CameraPose.looking(
                        cachedSightPose.position(),
                        directionFromYawPitch(player.getYRot(), player.getXRot())
                ));
            } else {
                applyCachedCameraPose(cachedSightPose);
            }
        } else if (!freeLookEnabled()) {
            // FreeLook is OFF: Counter-rotate the camera down to match the gun elevating (or up
            // when the zero depresses below bore). Uses the SMOOTHED zero pitch — the same signal
            // the reticle offset uses — so a scroll step does not kick the camera ahead of the
            // filtered bore and jiggle the sight picture.
            double zeroPitch = renderZeroPitch();
            if (Math.abs(zeroPitch) > 1.0e-4) {
                applyCachedCameraPose(CameraPose.looking(
                        cachedSightPose.position(),
                        directionFromYawPitch(cachedSightPose.yaw(), (float)(cachedSightPose.pitch() + zeroPitch)),
                        cachedSightPose.up()
                ));
            } else {
                applyCachedCameraPose(cachedSightPose);
            }
        } else {
            // FreeLook is ON - use sight pose's up to preserve roll from ship orientation
            applyCachedCameraPose(CameraPose.looking(
                    cachedSightPose.position(),
                    directionFromYawPitch(freeLookYaw, freeLookPitch),
                    cachedSightPose.up()
            ));
        }
    }

    /**
     * Caches the camera pose and derives the render angles from it. While the
     * player is mounted to a ship, VS2 appends the ship's render rotation to
     * the camera after our angles are consumed (mounted-camera transform at
     * prepareCullFrustum). Feeding it {@code Q_ship⁻¹ · Q_world} therefore
     * renders exactly {@code Q_world}, keeping free-look and the scope locked
     * to world-space angles on rotating ships. The same interpolated render
     * transform quaternion VS2 itself uses is taken for the pre-division, so
     * the cancellation is exact (no lag, no drift, roll handled correctly).
     */
    private static void applyCachedCameraPose(CameraPose pose) {
        cachedCameraPose = pose;
        Quaternionf rotation = new Quaternionf(pose.qx(), pose.qy(), pose.qz(), pose.qw()).normalize();
        if (ClientConfig.scopeMountedRotationCompensation()) {
            Quaternionf shipRotation = com.erika.vsanalogwarfare.scope.compat.VsCompat.playerMountedShipRotation();
            if (shipRotation != null) {
                rotation = shipRotation.conjugate().mul(rotation).normalize();
            }
        }
        Vector3f forward = rotation.transform(new Vector3f(0.0f, 0.0f, 1.0f)).normalize();
        cachedRenderYaw = (float) Math.toDegrees(Math.atan2(-forward.x(), forward.z()));
        cachedRenderPitch = (float) -Math.toDegrees(Math.asin(Math.max(-1.0f, Math.min(1.0f, forward.y()))));
        cachedRenderRoll = rollOf(rotation, cachedRenderYaw, cachedRenderPitch);
    }

    public static void triggerRangefinder() {
        if (!active) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        CameraPose pose = cameraPose(1.0f);
        Vec3 cameraPos = pose.position();
        
        Vec3 direction;
        if (freeLookEnabled()) {
            // Free look angles are world-frame (see toggleFreeLook), mounted or not.
            direction = freeLookDirection();
        } else {
            double zeroPitch = getZeroPitch();
            if (Math.abs(zeroPitch) > 1.0e-4) {
                Vec3 zeroedDir = directionFromYawPitch(cachedSightPose.yaw(), (float)(cachedSightPose.pitch() + zeroPitch));
                if (com.erika.vsanalogwarfare.scope.compat.VsCompat.isPlayerMountedToShip()) {
                    direction = com.erika.vsanalogwarfare.scope.compat.VsCompat
                            .shipToWorldDirectionForRaycast(mc.level, mountPos, zeroedDir);
                } else {
                    direction = zeroedDir;
                }
            } else {
                net.minecraft.core.Direction fallbackFacing = net.minecraft.core.Direction.NORTH;
                Vec3 localDirection = com.erika.vsanalogwarfare.scope.compat.CbcCompat
                        .getAimDirection(mc.level, mountPos, fallbackFacing, 1.0f, false)
                        .orElse(Vec3.atLowerCornerOf(fallbackFacing.getNormal()).normalize());
                direction = com.erika.vsanalogwarfare.scope.compat.VsCompat
                        .shipToWorldDirectionForRaycast(mc.level, mountPos, localDirection);
            }
        }
        
        double maxRange = com.erika.vsanalogwarfare.config.CommonConfig.maxRangefinderDistance();

        double offset = 1.5;
        Vec3 start = cameraPos.add(direction.scale(offset));
        Vec3 end = cameraPos.add(direction.scale(maxRange));

        // --- 1. VANILLA TERRAIN RAYCAST ---
        net.minecraft.world.level.ClipContext context = new net.minecraft.world.level.ClipContext(
                start, end, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, mc.player);

        net.minecraft.world.phys.BlockHitResult hitResult = mc.level.clip(context);
        double vanillaDistance = hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK
                ? cameraPos.distanceTo(hitResult.getLocation())
                : -1.0;

        rangefinderDistance = vanillaDistance;
        rangefinderTimestamp = net.minecraft.Util.getMillis();
        rangefinderTasksPending = 2; // We are waiting on 2 tasks: DH and Server Ship

        // --- 2. DISTANT HORIZONS RAYCAST ---
        if (vanillaDistance < 0) {
            java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                return com.erika.vsanalogwarfare.scope.compat.DhCompat
                        .getDistantHorizonsRaycastDistance(start, direction, maxRange);
            }).thenAccept(dhDist -> {
                mc.execute(() -> {
                    if (dhDist > 0) {
                        double finalDhDist = dhDist + offset;
                        if (finalDhDist >= 32.0) {
                            double current = rangefinderDistance;
                            if (current < 0 || finalDhDist < current) {
                                rangefinderDistance = finalDhDist;
                            }
                        }
                    }
                    decrementRangefinderTasks(); // Mark DH task as completed
                });
            });
        } else {
            // Vanilla already hit, but we still need to mark the DH task as "done" since we skipped it
            decrementRangefinderTasks();
        }

        // --- 3. VS2 SERVER SHIP RAYCAST ---
        ModNetwork.sendToServer(new ModNetwork.RangefinderRequestPacket(cameraPos, direction));
    }

    private static int rangefinderTasksPending = 0;

    public static void decrementRangefinderTasks() {
        if (rangefinderTasksPending > 0) {
            rangefinderTasksPending--;

            // If both background tasks finished and we STILL hit nothing, fast-forward the UI timer!
            if (rangefinderTasksPending == 0 && rangefinderDistance < 0) {
                // Safely force the timer exactly 3 seconds into the past to trigger the result phase instantly
                rangefinderTimestamp = net.minecraft.Util.getMillis() - 3000L;
            }
        }
    }

    private static int sightZeroDistance = 0;
    // High-angle (artillery) branch: zeroing past the cannon's apex range keeps elevating while the
    // zero distance walks back down. The two branches share the apex point.
    private static boolean highAngleZero = false;
    private static com.erika.vsanalogwarfare.scope.ballistics.BallisticSolver.ApexSolution apexSolution = null;
    private static Integer capRangeSolution = null;
    // Mount's real pitch limits, resolved server-side and synced via ScopeStatePacket (degrees).
    private static float maxDepressionDeg = 89.0f;
    private static float maxElevationDeg = 89.0f;
    // Degrees of depression commanded per zeroing notch below ZRN 0 (bore).
    public static final float DEPRESSION_DEGREES_PER_STEP = 5.0f;
    // Per-frame low-passed zero pitch (tau ~0.1 s, the g-h filter's settling window): the camera
    // counter-rotation and the reticle offset both consume this, so an instant scroll step no longer
    // kicks the camera before the filtered bore catches up.
    private static final double RENDER_ZERO_TAU_SECONDS = 0.1;
    private static double renderZeroPitchSmoothed = Double.NaN;
    private static long renderZeroPitchStamp = 0L;

    public static int sightZeroDistance() {
        return sightZeroDistance;
    }

    public static boolean highAngleZero() {
        return highAngleZero;
    }

    public static float maxDepressionDeg() {
        return maxDepressionDeg;
    }

    public static float maxElevationDeg() {
        return maxElevationDeg;
    }

    public static void setHighAngleZero(boolean high) {
        if (highAngleZero != high) {
            highAngleZero = high;
            zeroPitchDirty = true; // Mark for recalculation
        }
    }

    /** Flattest-arc maximum range of the current profile in meters, or -1 when no valid profile is known. */
    public static int apexRange() {
        if (ballisticProfile == null || !ballisticProfile.valid()) return -1;
        if (apexSolution == null) {
            apexSolution = com.erika.vsanalogwarfare.scope.ballistics.BallisticSolver.maxRangeApex(ballisticProfile);
        }
        return apexSolution != null ? (int) Math.round(apexSolution.range()) : -1;
    }

    /** Pitch of the flattest-arc maximum-range solution (the wheel's branch crossing point). */
    public static double apexPitch() {
        return apexSolution != null ? apexSolution.pitchDegrees() : 45.0;
    }

    /** The pitch ceiling the zeroing wheel may command: client config AND the mount's real elevation. */
    public static double elevationCapPitch() {
        float mountCap = maxElevationDeg > 0.0f ? maxElevationDeg : 89.0f;
        return Math.min(com.erika.vsanalogwarfare.config.ClientConfig.maxZeroPitchDegrees(), mountCap);
    }

    /**
     * Range of the shell when fired at the mount's elevation cap. Below the apex pitch this is the
     * mount-limited maximum range; above it, the high branch's zero floor. -1 when unknown.
     */
    public static int elevationCapRange() {
        if (ballisticProfile == null || !ballisticProfile.valid()) return -1;
        if (capRangeSolution == null) {
            double range = com.erika.vsanalogwarfare.scope.ballistics.BallisticSolver
                    .impactRange(ballisticProfile, elevationCapPitch());
            capRangeSolution = Double.isFinite(range) ? (int) Math.round(range) : -1;
        }
        return capRangeSolution;
    }

    /**
     * Upper bound for the stored zero: the wheel's full elevation travel when a profile exists
     * (two apex crossings), otherwise the rangefinder clamp.
     */
    public static int zeroCap() {
        int apex = apexRange();
        if (apex > 0) return Math.min(2 * apex, 10000);
        return (int) com.erika.vsanalogwarfare.config.CommonConfig.maxRangefinderDistance();
    }

    /** Negative end of the wheel: the mount's real depression, DEPRESSION_DEGREES_PER_STEP per notch. */
    public static int depressionSpan() {
        if (maxDepressionDeg <= 0.01f) return 0;
        int step = Math.max(1, com.erika.vsanalogwarfare.config.ClientConfig.zeroingStep());
        return (int) (maxDepressionDeg / DEPRESSION_DEGREES_PER_STEP) * step;
    }

    /**
     * Zero pitch smoothed for render-time consumers. Both the camera counter-rotation and the
     * reticle texture offset must use THIS value so they move in lockstep with each other and
     * spread a scroll step over the same window the filtered bore moves in.
     */
    public static double renderZeroPitch() {
        long now = System.nanoTime();
        double target = getZeroPitch();
        if (Double.isNaN(renderZeroPitchSmoothed)) {
            renderZeroPitchSmoothed = target;
        } else {
            double dt = Math.max(0.0, (now - renderZeroPitchStamp) / 1.0e9);
            double k = 1.0 - Math.exp(-dt / RENDER_ZERO_TAU_SECONDS);
            renderZeroPitchSmoothed += (target - renderZeroPitchSmoothed) * k;
        }
        renderZeroPitchStamp = now;
        return renderZeroPitchSmoothed;
    }

    public static void setSightZeroDistance(int dist) {
        int newDist = Math.max(-depressionSpan(), Math.min(zeroCap(), dist));
        if (sightZeroDistance != newDist) {
            sightZeroDistance = newDist;
            zeroPitchDirty = true; // Mark for recalculation
        }
    }

    public static double getZeroPitch() {
        if (zeroPitchDirty) {
            if (sightZeroDistance < 0) {
                // Depression segment: each notch below bore is a fixed number of degrees.
                int step = Math.max(1, com.erika.vsanalogwarfare.config.ClientConfig.zeroingStep());
                cachedZeroPitch = sightZeroDistance * (DEPRESSION_DEGREES_PER_STEP / (double) step);
            } else if (sightZeroDistance > 0 && ballisticProfile != null && ballisticProfile.valid()) {
                double cap = elevationCapPitch();
                double maxPitch = highAngleZero ? cap
                        : Math.min(com.erika.vsanalogwarfare.scope.ballistics.BallisticSolver.DEFAULT_MAX_PITCH_DEG, cap);
                if (highAngleZero) {
                    apexRange(); // populate the apex cache - the high-arc scan is bounded by apexPitch()
                }
                com.erika.vsanalogwarfare.scope.ballistics.ReticleMark mark =
                        com.erika.vsanalogwarfare.scope.ballistics.BallisticSolver.solvePitch(
                                ballisticProfile, sightZeroDistance, maxPitch, highAngleZero, apexPitch()
                        );
                // Unsolvable zero: hold the last commanded elevation. The zero wheel drives the cannon by
                // pitch deltas, so a 0.0 fallback here would slam the cannon flat on one scroll notch.
                if (mark != null) {
                    cachedZeroPitch = mark.pitchDegrees();
                }
            } else if (sightZeroDistance <= 0 && highAngleZero && ballisticProfile != null && ballisticProfile.valid()) {
                // Legacy state (high branch parked at/below bore): muzzle at the elevation cap.
                cachedZeroPitch = elevationCapPitch();
            } else if (sightZeroDistance == 0) {
                cachedZeroPitch = 0.0;
            } // else: no valid profile - hold the last commanded elevation
            zeroPitchDirty = false;
        }
        return cachedZeroPitch;
    }

    public static Vec3 zeroedFreeLookDirection() {
        double zeroPitch = getZeroPitch();
        // Force the physical cannon to aim HIGHER than the free-look camera
        return directionFromYawPitch(freeLookYaw, (float)(freeLookPitch - zeroPitch));
    }
}
