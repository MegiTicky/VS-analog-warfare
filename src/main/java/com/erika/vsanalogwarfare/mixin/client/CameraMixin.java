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
        vs_analog_warfare$applyThirdPersonLift(detached);
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
            vs_analog_warfare$applyThirdPersonLift(thirdPerson);
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
     * Scope-session third-person view: keep VS's orbit/collision result untouched
     * and raise the origin in world Y so the vehicle hull below does not block
     * the view. Applied after both vanilla and VS mounted setups; when the player
     * is ship-mounted VS overwrites the vanilla setup afterward, so the lift is
     * never applied twice.
     */
    private void vs_analog_warfare$applyThirdPersonLift(boolean detached) {
        if (!detached
                || !ClientScopeState.active()
                || ClientScopeState.viewMode() != ClientScopeState.ViewMode.THIRD_PERSON) {
            return;
        }
        Vec3 position = ((Camera) (Object) this).getPosition();
        double lift = ClientConfig.scopeThirdPersonCameraLift();
        m_90581_(new Vec3(position.x, position.y + lift, position.z));
        vs_analog_warfare$stabilizeThirdPersonRotation();
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
     * Redo VS's orbit along the stabilized direction: same anchor (VS2's entity
     * mixin ship-corrects getEyePosition to the anchor VS itself used), same max
     * distance formula, same ship-aware collision that ignores the mounted ship.
     */
    private void vs_analog_warfare$applyStabilizedThirdPerson(BlockGetter level, Entity entity, float partialTick,
                                                              ClientShip shipMountedTo) {
        vs_analog_warfare$stabilizeThirdPersonRotation();
        if (!(level instanceof net.minecraft.world.level.Level mcLevel)) {
            return;
        }
        Vec3 eye = entity.getEyePosition(partialTick);
        Vec3 dir = new Vec3(this.f_90554_.x(), this.f_90554_.y(), this.f_90554_.z());

        org.joml.primitives.AABBi aabb = (org.joml.primitives.AABBi) shipMountedTo.getShipVoxelAABB();
        double shipDist = ((aabb.lengthX() + aabb.lengthY() + aabb.lengthZ()) / 3.0) * 1.5;
        double maxZoom = 4.0 * (Math.max(shipDist, 4.0) / 4.0);

        // VS2's getMaxZoomIgnoringMountedShip 8-ray fan, verbatim, along the
        // stabilized direction and skipping the mounted ship.
        for (int i = 0; i < 8; ++i) {
            float fx = (float) ((i & 1) * 2 - 1) * 0.1F;
            float fy = (float) ((i >> 1 & 1) * 2 - 1) * 0.1F;
            float fz = (float) ((i >> 2 & 1) * 2 - 1) * 0.1F;
            Vec3 from = eye.add(fx, fy, fz);
            Vec3 to = new Vec3(eye.x - dir.x * maxZoom + fx + fz,
                    eye.y - dir.y * maxZoom + fy,
                    eye.z - dir.z * maxZoom + fz);
            HitResult hitResult = org.valkyrienskies.mod.common.world.RaycastUtilsKt.clipIncludeShips(mcLevel,
                    new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, entity),
                    true, shipMountedTo.getId());
            if (hitResult.getType() != HitResult.Type.MISS) {
                double dist = hitResult.getLocation().distanceTo(eye);
                if (dist < maxZoom) {
                    maxZoom = dist;
                }
            }
        }

        double lift = ClientConfig.scopeThirdPersonCameraLift();
        m_90581_(eye.subtract(dir.scale(maxZoom)).add(0.0, lift, 0.0));
    }
}
