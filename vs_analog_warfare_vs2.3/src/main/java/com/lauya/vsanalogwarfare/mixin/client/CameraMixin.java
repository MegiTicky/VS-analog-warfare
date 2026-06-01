package com.lauya.vsanalogwarfare.mixin.client;

import com.lauya.vsanalogwarfare.client.ClientScopeState;
import com.lauya.vsanalogwarfare.client.ScopeDebug;
import com.lauya.vsanalogwarfare.scope.rig.CameraPose;
import net.minecraft.client.Camera;
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
import org.valkyrienskies.core.api.ships.ClientShip;

@Mixin(value = Camera.class, priority = 900)
public abstract class CameraMixin {
    /*
     * Use SRG names directly and disable remapping. The CurseForge/Forge runtime in the
     * crash log loads client-1.20.1-20230612.114412-srg.jar and this MVP project was not
     * producing a refmap, so named shadows such as setPosition could not be found at
     * startup. These are Camera#setPosition(Vec3), Camera#setRotation(float,float), and
     * Camera#setup(...) in the 1.20.1 SRG namespace.
     */
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

    /*
     * Valkyrien Skies calls its own Camera#setupWithShipMounted(...) later during
     * renderLevel for passengers seated on moving ships. That overwrites vanilla
     * Camera#setup, which is why the scope appeared yaw-dependent/180-ish wrong
     * only when used from a Create seat on a ship. Inject after that method too.
     *
     * This method is added to Camera by VS's mixin, so this mixin has lower priority
     * and require=0 for safer startup behavior.
     */
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

        // Start from the vanilla yaw/pitch path to keep any vanilla side effects, then
        // overwrite the quaternion and basis vectors with the rolled optic orientation.
        m_90572_(yaw, pitch);
        this.f_90558_ = yaw;
        this.f_90557_ = pitch;

        this.f_90559_.set(scopeRotation).normalize();
        this.f_90554_.set(0.0f, 0.0f, 1.0f).rotate(this.f_90559_);
        this.f_90555_.set(0.0f, 1.0f, 0.0f).rotate(this.f_90559_);
        this.f_90556_.set(1.0f, 0.0f, 0.0f).rotate(this.f_90559_);
    }
}
