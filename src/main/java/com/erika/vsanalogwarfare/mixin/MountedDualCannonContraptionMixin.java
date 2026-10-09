package com.erika.vsanalogwarfare.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(targets = "com.cainiao1053.cbcmoreshells.cannon_control.contraption.MountedDualCannonContraption")
public class MountedDualCannonContraptionMixin {
    // Vanilla Entity.shoot: SRG-named in the production CBCMS jar, Mojang-named
    // in the deobfuscated dev jar. Omit the member name so owner+descriptor
    // matches either; fireShot's only (DDDFF)V calls are the two barrel shots,
    // so the ordinals are stable in both environments.
    @ModifyArg(
            method = "fireShot",
            at = @At(value = "INVOKE", target = "Lcom/cainiao1053/cbcmoreshells/munitions/dual_cannon/AbstractDualCannonProjectile;(DDDFF)V", ordinal = 0, remap = false),
            index = 4,
            remap = false)
    private float vsaw$eliminateSpreadBarrel1(float spread) {
        return 0.0f;
    }

    @ModifyArg(
            method = "fireShot",
            at = @At(value = "INVOKE", target = "Lcom/cainiao1053/cbcmoreshells/munitions/dual_cannon/AbstractDualCannonProjectile;(DDDFF)V", ordinal = 1, remap = false),
            index = 4,
            remap = false)
    private float vsaw$eliminateSpreadBarrel2(float spread) {
        return 0.0f;
    }
}
