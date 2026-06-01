package com.lauya.vsanalogwarfare.mixin.client;

import com.lauya.vsanalogwarfare.client.ScopeDebug;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PoseStack.class)
public abstract class PoseStackMixin {
    /*
     * VS's mounted-seat render wrapper calls PoseStack#mulPose with the inverse
     * ship rotation immediately before rendering the level. That is correct for a
     * normal passenger camera because the vanilla view matrix was built in the
     * ship/passenger frame. The scope camera is different: CameraMixin already
     * replaced vanilla setup with a world-space optic pose before the vanilla view
     * matrix is built. Applying VS's inverse ship rotation after that turns a
     * correct world-space yaw into a ship-yaw-dependent offset/double count.
     *
     * The wrapper method is added by VS's own mixin, so targeting it directly is
     * brittle. Intercepting PoseStack#mulPose and cancelling only that call while
     * scoped is the narrowest reliable hook across mixin ordering.
     */
    @Inject(method = "m_252781_", at = @At("HEAD"), cancellable = true, remap = false)
    private void vs_analog_warfare$skipVsMountedInverseRotationWhileScoped(Quaternionf quaternion, CallbackInfo ci) {
        if (ScopeDebug.shouldSkipVsMountedPoseRotation()) {
            ScopeDebug.poseRotationSkipped(quaternion);
            ci.cancel();
        }
    }
}
