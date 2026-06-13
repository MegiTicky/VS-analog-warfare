package com.erika.vsanalogwarfare.mixin.client;

import com.erika.vsanalogwarfare.client.ClientScopeState;
import com.erika.vsanalogwarfare.client.ScopeDebug;
import com.erika.vsanalogwarfare.scope.compat.VsCompat;
import com.erika.vsanalogwarfare.scope.rig.CameraPose;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
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
        if (ClientScopeState.active()) {
            ScopeDebug.cameraHook("vs-mounted", shipMountedTo, (Camera) (Object) this);
        }
    }

    private void vs_analog_warfare$applyVirtualScopeView(float partialTick) {
        if (!ClientScopeState.active()) {
            return;
        }
        CameraPose pose = ClientScopeState.cameraPose(partialTick);
        Vec3 cameraPosition = pose.position();
        float yaw = pose.yaw();
        float pitch = pose.pitch();
        Quaternionf scopeRotation = new Quaternionf(pose.qx(), pose.qy(), pose.qz(), pose.qw()).normalize();
        m_90581_(cameraPosition);

        m_90572_(yaw, pitch);
        this.f_90558_ = yaw;
        this.f_90557_ = pitch;

        this.f_90559_.set(scopeRotation).normalize();
        this.f_90554_.set(0.0f, 0.0f, 1.0f).rotate(this.f_90559_);
        this.f_90555_.set(0.0f, 1.0f, 0.0f).rotate(this.f_90559_);
        this.f_90556_.set(1.0f, 0.0f, 0.0f).rotate(this.f_90559_);
    }
}
