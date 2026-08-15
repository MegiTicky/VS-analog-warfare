package com.erika.vsanalogwarfare.vehiclesetup.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

public record VehicleSetupShipPosition(long shipId, BlockPos offset) {
    @Nullable
    public static VehicleSetupShipPosition at(Level level, BlockPos position) {
        VehicleSetupReflection.ShipPosition result = VehicleSetupReflection.shipPosition(level, position);
        return result == null ? null : new VehicleSetupShipPosition(result.shipId(), result.offset());
    }
}
