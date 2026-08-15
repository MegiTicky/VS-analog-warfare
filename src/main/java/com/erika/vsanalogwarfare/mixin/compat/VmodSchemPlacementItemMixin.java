package com.erika.vsanalogwarfare.mixin.compat;

import com.erika.vsanalogwarfare.vehiclesetup.compat.VmodVehicleSetupCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.spaceeye.vmod.schematic.SchematicActionsQueue$SchemPlacementItem", remap = false)
public class VmodSchemPlacementItemMixin {
    @Inject(method = "place", at = @At("RETURN"), remap = false)
    private void vsaw$runVehicleSetup(long start, long timeout, CallbackInfoReturnable<Boolean> callback) {
        if (Boolean.TRUE.equals(callback.getReturnValue())) VmodVehicleSetupCompat.placementComplete(this);
    }
}
