package com.erika.vsanalogwarfare.mixin.client;

import com.erika.vsanalogwarfare.client.ClientScopeState;
import com.erika.vsanalogwarfare.client.ScopeDebug;
import com.erika.vsanalogwarfare.scope.compat.VsCompat;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GameRenderer.class, priority = 500)
public abstract class GameRendererMixin {

    @Inject(method = "m_109089_", at = @At("HEAD"), remap = false)
    private void vs_analog_warfare$beginMountedRender(float partialTicks, long finishTimeNano,
                                                       PoseStack matrixStack, CallbackInfo ci) {
        if (ClientScopeState.active() && VsCompat.isPlayerMountedToShip()) ScopeDebug.enterVsMountedCameraWrapper();
    }

    @Inject(method = "m_109089_", at = @At("RETURN"), remap = false)
    private void vs_analog_warfare$endMountedRender(float partialTicks, long finishTimeNano,
                                                     PoseStack matrixStack, CallbackInfo ci) {
        ScopeDebug.exitVsMountedCameraWrapper();
    }

    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean loggedSkip = false;

    @WrapOperation(
        method = "m_109089_",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;m_253210_(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/phys/Vec3;Lorg/joml/Matrix4f;)V"
        ),
        remap = false
    )
    private void vs_analog_warfare$wrapPrepareCullFrustum(
            LevelRenderer instance,
            PoseStack poseStack,
            Vec3 cameraPos,
            Matrix4f projectionMatrix,
            Operation<Void> original,
            float partialTicks,
            long finishTimeNano,
            PoseStack matrixStack) {

        if (ClientScopeState.scopeViewActive()) {
            if (!loggedSkip) {
                loggedSkip = true;
                // Note: this wrapper does NOT bypass VS - original.call() still
                // runs VS's mounted-camera transform. The ship rotation it adds
                // is canceled by the compensated angles fed to
                // ComputeCameraAngles (see ClientScopeState).
                LOGGER.debug("[VSAW_SCOPE] Scope active - VS mounted-camera transform compensated via ComputeCameraAngles");
            }
            original.call(instance, poseStack, cameraPos, projectionMatrix);
            return;
        }

        original.call(instance, poseStack, cameraPos, projectionMatrix);
    }
}
