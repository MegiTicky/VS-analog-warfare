package com.erika.vsanalogwarfare.scope;

import com.erika.vsanalogwarfare.scope.compat.VsCompat;
import com.erika.vsanalogwarfare.vehiclesetup.compat.VehicleSetupReflection;
import com.erika.vsanalogwarfare.vehiclesetup.compat.VehicleSetupShipPosition;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.Map;

/** A cannon target that survives a VS schematic being moved to another world position. */
public record ScopeCannonLink(long shipId, @Nullable BlockPos shipOffset, BlockPos fallbackPos) {
    public static ScopeCannonLink fromTarget(Level level, BlockPos target) {
        VehicleSetupShipPosition ship = VehicleSetupShipPosition.at(level, target);
        return ship == null
                ? new ScopeCannonLink(-1L, null, target.immutable())
                : new ScopeCannonLink(ship.shipId(), ship.offset(), target.immutable());
    }

    @Nullable
    public BlockPos resolve(Level level, @Nullable Map<Long, Object> placedShips) {
        if (shipId >= 0L && shipOffset != null) {
            Object ship = placedShips == null ? null : placedShips.get(shipId);
            if (ship == null) {
                for (Object candidate : VsCompat.getAllShips(level)) {
                    if (VsCompat.getShipId(candidate) == shipId) {
                        ship = candidate;
                        break;
                    }
                }
            }
            if (ship != null) {
                BlockPos resolved = VehicleSetupReflection.positionOnShip(ship, shipOffset);
                if (resolved != null) return resolved;
            }
            return null;
        }
        return fallbackPos;
    }

    /** Re-points this link at the ship it became after a schematic paste; shipOffset carries over. */
    public ScopeCannonLink rebased(long newShipId, BlockPos newFallbackPos) {
        return new ScopeCannonLink(newShipId, shipOffset, newFallbackPos);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("FallbackPos", fallbackPos.asLong());
        tag.putLong("ShipId", shipId);
        if (shipOffset != null) tag.putLong("ShipOffset", shipOffset.asLong());
        return tag;
    }

    @Nullable
    public static ScopeCannonLink load(CompoundTag tag) {
        if (tag == null || !tag.contains("FallbackPos")) return null;
        long shipId = tag.contains("ShipId") ? tag.getLong("ShipId") : -1L;
        BlockPos shipOffset = tag.contains("ShipOffset") ? BlockPos.of(tag.getLong("ShipOffset")) : null;
        return new ScopeCannonLink(shipId, shipOffset, BlockPos.of(tag.getLong("FallbackPos")));
    }
}
