package com.erika.vsanalogwarfare.scope;

import com.erika.vsanalogwarfare.scope.compat.VsCompat;
import com.erika.vsanalogwarfare.vehiclesetup.compat.VehicleSetupReflection;
import com.erika.vsanalogwarfare.vehiclesetup.compat.VehicleSetupShipPosition;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.function.Predicate;

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

    /**
     * Resolves the linked block position, tolerating ship-AABB drift. The
     * stored offset is relative to the ship's AABB min corner, which VS2
     * recomputes on every block edit, so a structurally edited ship resolves
     * the offset to a position next to the real target. When the offset frame
     * no longer verifies, the fallback position — the shipyard position
     * captured at link time, invariant for the ship's lifetime — is returned
     * instead when it verifies, so callers keep working between the edit and
     * the next periodic frame heal. Null when nothing verifies.
     */
    @Nullable
    public BlockPos resolveVerified(Level level, @Nullable Map<Long, Object> placedShips, Predicate<BlockPos> verifies) {
        BlockPos resolved = resolve(level, placedShips);
        if (resolved != null && verifies.test(resolved)) return resolved;
        if (shipId >= 0L && verifies.test(fallbackPos)) return fallbackPos;
        return null;
    }

    /**
     * Schematic NBT carries the ship ids of the world it was saved in, which never match the
     * freshly allocated ids of the pasted ships. Maps this link onto its pasted ship via the
     * placement's ship map; null when the link has no ship-relative anchor, the mount was not
     * part of the paste, or the pasted ship cannot be resolved.
     */
    @Nullable
    public static ScopeCannonLink rebasedAfterPaste(ScopeCannonLink link, Map<Long, Object> placedShips) {
        if (link == null || placedShips == null || link.shipId() < 0L || link.shipOffset() == null) return null;
        Object ship = placedShips.get(link.shipId());
        if (ship == null) return null;
        long newShipId = VsCompat.getShipId(ship);
        if (newShipId == link.shipId()) return null;
        BlockPos resolved = VehicleSetupReflection.positionOnShip(ship, link.shipOffset());
        if (resolved == null) return null;
        return link.rebased(newShipId, resolved.immutable());
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
