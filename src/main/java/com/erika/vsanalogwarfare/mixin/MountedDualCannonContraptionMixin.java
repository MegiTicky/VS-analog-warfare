package com.erika.vsanalogwarfare.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(targets = "com.cainiao1053.cbcmoreshells.cannon_control.contraption.MountedDualCannonContraption")
public class MountedDualCannonContraptionMixin {
    @ModifyArg(
            method = "fireShot",
            at = @At(value = "INVOKE", target = "Lcom/cainiao1053/cbcmoreshells/munitions/dual_cannon/AbstractDualCannonProjectile;m_6686_(DDDFF)V", ordinal = 0, remap = false),
            index = 4,
            remap = false)
    private float vsaw$eliminateSpreadBarrel1(float spread) {
        return 0.0f;
    }

    @ModifyArg(
            method = "fireShot",
            at = @At(value = "INVOKE", target = "Lcom/cainiao1053/cbcmoreshells/munitions/dual_cannon/AbstractDualCannonProjectile;m_6686_(DDDFF)V", ordinal = 1, remap = false),
            index = 4,
            remap = false)
    private float vsaw$eliminateSpreadBarrel2(float spread) {
        return 0.0f;
    }
}
