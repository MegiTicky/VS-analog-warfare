package com.lauya.vsanalogwarfare.mixin.client;

import com.lauya.vsanalogwarfare.client.ScopeDebug;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    /*
     * The VS mounted-seat render wrapper calls into a helper on GameRenderer
     * (added by VS's own mixin) that applies an inverse ship rotation via
     * PoseStack.mulPose right before rendering the level. While scoped we
     * want to skip exactly that mulPose call; doing stack-trace inspection
     * inside PoseStack.mulPose is extremely expensive.
     *
     * This method is not present in vanilla; it exists only when VS is loaded.
     * require=0 keeps startup safe if the target is absent or renamed.
     */
    @Inject(method = "setupCameraWithMountedShip", at = @At("HEAD"), remap = false, require = 0)
    private void vs_analog_warfare$enterVsMountedCameraWrapper(CallbackInfo ci) {
        ScopeDebug.enterVsMountedCameraWrapper();
    }

    @Inject(method = "setupCameraWithMountedShip", at = @At("RETURN"), remap = false, require = 0)
    private void vs_analog_warfare$exitVsMountedCameraWrapper(CallbackInfo ci) {
        ScopeDebug.exitVsMountedCameraWrapper();
    }
}

