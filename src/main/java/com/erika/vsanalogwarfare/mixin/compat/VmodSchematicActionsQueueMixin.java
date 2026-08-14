package com.erika.vsanalogwarfare.mixin.compat;

import com.erika.vsanalogwarfare.vehiclesetup.compat.VmodVehicleSetupCompat;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.UUID;

@Pseudo
@Mixin(targets = "net.spaceeye.vmod.schematic.SchematicActionsQueue", remap = false)
public class VmodSchematicActionsQueueMixin {
    @Inject(method = "queueShipsCreationEvent", at = @At("HEAD"), remap = false)
    private void vsaw$rememberPlacer(ServerLevel level, UUID player, List<?> ships, CallbackInfo ci) {
        VmodVehicleSetupCompat.rememberPlacement(player, ships);
    }
}
