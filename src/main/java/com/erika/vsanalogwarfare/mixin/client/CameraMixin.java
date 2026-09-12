package com.erika.vsanalogwarfare.mixin.client;

import com.erika.vsanalogwarfare.client.ClientScopeState;
import com.erika.vsanalogwarfare.client.ScopeDebug;
import com.erika.vsanalogwarfare.config.ClientConfig;
import com.erika.vsanalogwarfare.scope.compat.VsCompat;
import com.erika.vsanalogwarfare.scope.rig.CameraPose;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import javax.annotation.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3dc;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import org.valkyrienskies.core.api.ships.ClientShip;

@Mixin(value = Camera.class, priority = 900)
public abstract class CameraMixin {
    @Shadow(remap = false)
    protected abstract void m_90581_(Vec3 position);

    @Shadow(remap = false)
    protected abstract void m_90572_(float yaw, float pitch);

    @Shadow(remap = false)
    private float f_90557_;

    @Shadow(remap = false)
    private float f_90558_;

    @Shadow(remap = false)
    @Final
    private Quaternionf f_90559_;

    @Shadow(remap = false)
    @Final
    private Vector3f f_90554_;

    @Shadow(remap = false)
    @Final
    private Vector3f f_90555_;

    @Shadow(remap = false)
    @Final
    private Vector3f f_90556_;

    @Inject(method = "m_90575_", at = @At("TAIL"), remap = false)
    private void vs_analog_warfare$useVirtualScopeView(BlockGetter level, Entity entity, boolean detached, boolean mirror, float partialTick, CallbackInfo ci) {
        vs_analog_warfare$applyVirtualScopeView(partialTick);
        vs_analog_warfare$applyThirdPersonLift(detached, partialTick);
        if (ClientScopeState.active()) {
            ScopeDebug.cameraHook("vanilla", null, (Camera) (Object) this);
        }
    }

    @Inject(method = "setupWithShipMounted", at = @At("TAIL"), remap = false, require = 0)
    private void vs_analog_warfare$useVirtualScopeViewAfterVsMountedSetup(BlockGetter level, Entity renderViewEntity,
                                                                          boolean thirdPerson, boolean thirdPersonReverse,
                                                                          float partialTicks, ClientShip shipMountedTo,
                                                                          Vector3dc inShipPlayerPosition,
                                                                          CallbackInfo ci) {
        vs_analog_warfare$applyVirtualScopeView(partialTicks);
        if (thirdPerson
                && ClientScopeState.active()
                && ClientScopeState.viewMode() == ClientScopeState.ViewMode.THIRD_PERSON) {
            vs_analog_warfare$applyStabilizedThirdPerson(level, renderViewEntity, partialTicks, shipMountedTo);
        } else {
            vs_analog_warfare$applyThirdPersonLift(thirdPerson, partialTicks);
        }
        if (ClientScopeState.active()) {
            ScopeDebug.cameraHook("vs-mounted", shipMountedTo, (Camera) (Object) this);
        }
    }

    private void vs_analog_warfare$applyVirtualScopeView(float partialTick) {
        if (!ClientScopeState.scopeViewActive()) {
            return;
        }
        // Deliberately no ship pre-rotation here: the ship component of VS's
        // mounted-camera transform is already canceled by the compensated
        // angles fed to ComputeCameraAngles (see ClientScopeState). The pure
        // world-frame pose below is exactly what the frame renders, so it is
        // also what camera.rotation / yaw / pitch must report.
        CameraPose pose = ClientScopeState.cameraPose(partialTick);
        Vec3 cameraPosition = pose.position();
        Quaternionf scopeRotation = new Quaternionf(pose.qx(), pose.qy(), pose.qz(), pose.qw()).normalize();
        m_90581_(cameraPosition);

        this.f_90559_.set(scopeRotation).normalize();
        this.f_90554_.set(0.0f, 0.0f, 1.0f).rotate(this.f_90559_);
        this.f_90555_.set(0.0f, 1.0f, 0.0f).rotate(this.f_90559_);
        this.f_90556_.set(1.0f, 0.0f, 0.0f).rotate(this.f_90559_);
        Vector3f forward = new Vector3f(this.f_90554_).normalize();
        float worldYaw = (float) Math.toDegrees(Math.atan2(-forward.x(), forward.z()));
        float worldPitch = (float) -Math.toDegrees(Math.asin(Math.max(-1.0f, Math.min(1.0f, forward.y()))));
        m_90572_(worldYaw, worldPitch);
        this.f_90558_ = worldYaw;
        this.f_90557_ = worldPitch;
    }

    /**
     * Scope-session third-person view: redo the orbit from the world-Y raised
     * eye with ships fully excluded from the collision rays. Applied after both
     * vanilla and VS mounted setups; when the player is ship-mounted VS
     * overwrites the vanilla setup afterward, so the orbit is never applied
     * twice.
     */
    private void vs_analog_warfare$applyThirdPersonLift(boolean detached, float partialTick) {
        if (!detached
                || !ClientScopeState.active()
                || ClientScopeState.viewMode() != ClientScopeState.ViewMode.THIRD_PERSON) {
            return;
        }
        vs_analog_warfare$orbitThirdPerson(partialTick, ((Camera) (Object) this).getEntity(), null);
    }

    /**
     * Third-person view is world-stabilized: the player yaw/pitch are treated as
     * world angles (the pose-stack side is compensated via ComputeCameraAngles),
     * so the ship component VS's mounted camera premultiplied onto the
     * quaternion is stripped here. The orbit position stays anchored to the
     * vehicle; only the look direction stops swinging with the hull.
     */
    private void vs_analog_warfare$stabilizeThirdPersonRotation() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        float yaw = player.getYRot();
        float pitch = player.getXRot();
        Quaternionf stabilized = new Quaternionf().rotationYXZ(
                -yaw * ((float) Math.PI / 180.0f), pitch * ((float) Math.PI / 180.0f), 0.0f);
        this.f_90559_.set(stabilized).normalize();
        this.f_90554_.set(0.0f, 0.0f, 1.0f).rotate(this.f_90559_);
        this.f_90555_.set(0.0f, 1.0f, 0.0f).rotate(this.f_90559_);
        this.f_90556_.set(1.0f, 0.0f, 0.0f).rotate(this.f_90559_);
        m_90572_(yaw, pitch);
        this.f_90558_ = yaw;
        this.f_90557_ = pitch;
    }

    /**
     * VS recomputes the third-person back-off along its ship-combined forwards
     * (shipRot · look), while this view renders along the stabilized world look.
     * Redo the orbit along the stabilized direction: the pivot is the world-Y
     * raised eye (the lift is part of the pivot, not appended after), so the
     * collision rays test the exact path the camera travels.
     */
    private void vs_analog_warfare$applyStabilizedThirdPerson(BlockGetter level, Entity entity, float partialTick,
                                                              ClientShip shipMountedTo) {
        vs_analog_warfare$orbitThirdPerson(partialTick, entity, shipMountedTo);
    }

    /**
     * Orbit the scope third-person camera around the lifted eye position with
     * ships fully excluded from the obstruction rays. VS2 replaces
     * {@link net.minecraft.world.level.Level#clip} globally with a ship-including
     * raycast, so vanilla's own zoom pass pulls the camera in against the hull
     * the player stands or rides on; {@code RaycastUtilsKt.vanillaClip} bypasses
     * that override and only sees world blocks — ship blocks live in shipyard
     * space and can never clip the camera. World terrain still blocks normally.
     */
    private void vs_analog_warfare$orbitThirdPerson(float partialTick, Entity entity, @Nullable ClientShip shipMountedTo) {
        vs_analog_warfare$stabilizeThirdPersonRotation();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || entity == null) {
            return;
        }
        Vec3 pivot = entity.getEyePosition(partialTick)
                .add(0.0, ClientConfig.scopeThirdPersonCameraLift(), 0.0);
        Vec3 dir = new Vec3(this.f_90554_.x(), this.f_90554_.y(), this.f_90554_.z());

        double maxZoom = 4.0;
        if (shipMountedTo != null) {
            org.joml.primitives.AABBi aabb = (org.joml.primitives.AABBi) shipMountedTo.getShipVoxelAABB();
            double shipDist = ((aabb.lengthX() + aabb.lengthY() + aabb.lengthZ()) / 3.0) * 1.5;
            maxZoom = 4.0 * (Math.max(shipDist, 4.0) / 4.0);
        }

        // Vanilla Camera#getMaxZoom 8-ray fan, verbatim, along the stabilized
        // direction — but bypassing VS2's ship-including clip override.
        for (int i = 0; i < 8; ++i) {
            float fx = (float) ((i & 1) * 2 - 1) * 0.1F;
            float fy = (float) ((i >> 1 & 1) * 2 - 1) * 0.1F;
            float fz = (float) ((i >> 2 & 1) * 2 - 1) * 0.1F;
            Vec3 from = pivot.add(fx, fy, fz);
            Vec3 to = new Vec3(pivot.x - dir.x * maxZoom + fx + fz,
                    pivot.y - dir.y * maxZoom + fy,
                    pivot.z - dir.z * maxZoom + fz);
            HitResult hitResult = org.valkyrienskies.mod.common.world.RaycastUtilsKt.vanillaClip(mc.level,
                    new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, entity));
            if (hitResult.getType() != HitResult.Type.MISS) {
                double dist = hitResult.getLocation().distanceTo(pivot);
                if (dist < maxZoom) {
                    maxZoom = dist;
                }
            }
        }

        m_90581_(pivot.subtract(dir.scale(maxZoom)));
    }
}
