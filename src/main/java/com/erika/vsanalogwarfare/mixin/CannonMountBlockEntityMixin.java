package com.erika.vsanalogwarfare.mixin;

import com.erika.vsanalogwarfare.stabilizer.StabilizerController;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Injects the gyro stabilizer's compensating pitch speed into CBC's cannon
 * mount tick. The targeted call is the <b>pitch</b> {@code getAngularSpeed}
 * (ordinal 0 is yaw), inside the block that advances {@code cannonPitch};
 * CBC only reaches it when the mount is running and not stalled, and the
 * result still flows through CBC's own sequenced-angle-limit clamping and
 * elevation/depression limits.
 *
 * Runs on both server (authoritative) and client (smooth visuals); CBC's
 * {@code clientPitchDiff} chase absorbs any residual divergence.
 */
@Mixin(targets = "rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity", remap = false)
public class CannonMountBlockEntityMixin {

    @Shadow
    private float cannonPitch;

    @ModifyExpressionValue(
            method = "tick()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lrbasamoyai/createbigcannons/cannon_control/cannon_mount/CannonMountBlockEntity;getAngularSpeed(FF)F",
                    ordinal = 1,
                    remap = false),
            remap = false)
    private float vsaw$stabilizerPitchSpeed(float original) {
        float offset = StabilizerController.computeOffsetSpeed((Object) this, this.cannonPitch, original);
        if (offset == 0.0f) {
            return original;
        }
        return original + offset;
    }

    /**
     * Feeds the stabilizer's offset into CBC's client render extrapolation.
     * {@code getPitchOffset} re-derives the per-tick angular speed from the
     * shaft alone, so without this the drawn gun under-projects every step the
     * stabilizer commands and snaps at each tick boundary (visible as a 20 Hz
     * stutter in the zoomed scope). Read-only: reuses the offset the tick path
     * computed this game tick.
     */
    @ModifyExpressionValue(
            method = "getPitchOffset(F)F",
            at = @At(
                    value = "INVOKE",
                    target = "Lrbasamoyai/createbigcannons/cannon_control/cannon_mount/CannonMountBlockEntity;getAngularSpeed(FF)F",
                    ordinal = 0,
                    remap = false),
            remap = false)
    private float vsaw$renderPitchOffset(float original) {
        float renderOffset = StabilizerController.renderOffsetFor((Object) this);
        if (renderOffset == 0.0f) {
            return original;
        }
        return original + renderOffset;
    }

    /**
     * Render-time world-elevation lock: while the stabilizer is holding, the
     * rendered pitch is re-solved per frame against the ship's interpolated
     * render transform, so the gun is as smooth as the hull itself with no
     * 20 TPS stepping in the zoomed scope. Client-only, hold-state-gated, and
     * capped at a couple of degrees from CBC's own value; passes through
     * everywhere else (server, input, no stabilizer, schematic previews).
     */
    @ModifyReturnValue(method = "getPitchOffset(F)F", at = @At("RETURN"), remap = false)
    private float vsaw$renderPitchLock(float original) {
        return StabilizerController.computeRenderPitchOffset((Object) this, original);
    }
}
