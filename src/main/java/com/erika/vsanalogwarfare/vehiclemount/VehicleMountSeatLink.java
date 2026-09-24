package com.erika.vsanalogwarfare.vehiclemount;

import com.erika.vsanalogwarfare.VSAnalogWarfare;
import com.erika.vsanalogwarfare.vehiclesetup.compat.VehicleSetupShipPosition;
import com.erika.vsanalogwarfare.vehiclesetup.compat.VehicleSetupReflection;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import com.simibubi.create.content.contraptions.actors.seat.SeatBlock;
import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;

import java.util.UUID;
import javax.annotation.Nullable;

public record VehicleMountSeatLink(String role, UUID seatUuid, BlockPos seatPos, long shipId, BlockPos shipOffset, BlockPos handlePos) {
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Role", role);
        tag.putUUID("Seat", seatUuid);
        tag.putLong("SeatPos", seatPos.asLong());
        tag.putLong("ShipId", shipId);
        tag.putLong("ShipOffset", shipOffset.asLong());
        tag.putLong("HandlePos", handlePos.asLong());
        return tag;
    }

    public static VehicleMountSeatLink load(CompoundTag tag) {
        if (!tag.hasUUID("Seat") || !tag.contains("SeatPos") || !tag.contains("ShipOffset") || !tag.contains("HandlePos")) return null;
        return new VehicleMountSeatLink(tag.getString("Role"), tag.getUUID("Seat"), BlockPos.of(tag.getLong("SeatPos")),
                tag.getLong("ShipId"), BlockPos.of(tag.getLong("ShipOffset")), BlockPos.of(tag.getLong("HandlePos")));
    }

    public VehicleMountSeatLink withRole(String newRole) {
        return new VehicleMountSeatLink(newRole, seatUuid, seatPos, shipId, shipOffset, handlePos);
    }

    public Entity resolve(Level level) {
        return resolve(level, null);
    }

    public Entity resolve(Level level, VehicleMountHandleBlockEntity handle) {
        BlockPos target = shipyardPosition(level, handle);
        if (target == null) return null;
        return findSeatEntityAt(level, target, seatUuid);
    }

    public Entity createSeat(Level level, VehicleMountHandleBlockEntity handle) {
        BlockPos target = shipyardPosition(level, handle);
        if (target == null || !(level.getBlockState(target).getBlock() instanceof SeatBlock)) return null;
        SeatEntity seat = new SeatEntity(level, target);
        seat.setPos(target.getX() + .5, target.getY(), target.getZ() + .5);
        level.addFreshEntity(seat);
        return seat;
    }

    /**
     * Locates the seat's block position. The stored AABB-min offset is relative
     * to the ship's bounding-box corner, which VS2 recomputes on every block
     * edit, so an edited hull resolves the offset to a position next to the
     * real seat. Candidates are therefore tried against the live blocks and
     * the first one that verifies wins:
     * <ol>
     *     <li>{@link #seatPos} — the shipyard position captured at link time,
     *     invariant for the ship's lifetime;</li>
     *     <li>the same-ship anchor delta between the handle and this seat,
     *     a pure shipyard difference that needs no AABB at all;</li>
     *     <li>the legacy {@code positionOnShip} offset frame;</li>
     *     <li>a small proximity scan around the best expectation, which also
     *     recovers seats displaced a block or two by paste drift.</li>
     * </ol>
     * A verified position outside the stored frame is written back through the
     * handle so the link converges onto the ship's live frame.
     */
    @Nullable
    private BlockPos shipyardPosition(Level level, VehicleMountHandleBlockEntity handle) {
        Object ship = resolveShip(level, handle);
        BlockPos byOffset = ship == null ? null : VehicleSetupReflection.positionOnShip(ship, shipOffset);
        BlockPos byAnchor = sameShipAnchorDelta(level, handle);
        BlockPos center = seatPos != null ? seatPos : byAnchor != null ? byAnchor : byOffset;

        BlockPos verified = verifiedSeatPosition(level, seatPos);
        if (verified == null) verified = verifiedSeatPosition(level, byAnchor);
        if (verified == null) verified = verifiedSeatPosition(level, byOffset);
        if (verified == null && center != null) verified = findNearbySeatBlock(level, center);
        if (verified == null) return null;
        healFrame(level, handle, verified);
        return verified;
    }

    @Nullable
    private Object resolveShip(Level level, VehicleMountHandleBlockEntity handle) {
        Object ship = handle == null ? null : handle.placedShip(shipId);
        if (ship != null) return ship;
        for (Object candidate : com.erika.vsanalogwarfare.scope.compat.VsCompat.getAllShips(level)) {
            try {
                if (((Number) VehicleSetupReflection.invoke(candidate, "getId")).longValue() == shipId) {
                    return candidate;
                }
            } catch (ReflectiveOperationException ignored) { }
        }
        return null;
    }

    /**
     * Same-ship anchor correction: the handle block position and the stored
     * offsets' difference are shipyard quantities, so their combination needs
     * no ship object and survives dead ship ids (legacy pasted handles carry
     * the schematic's source-world id). Only meaningful when the handle shares
     * this seat's ship.
     */
    @Nullable
    private BlockPos sameShipAnchorDelta(Level level, VehicleMountHandleBlockEntity handle) {
        if (handle == null || shipId < 0L || handle.shipId() != shipId
                || shipOffset == null || handle.shipOffset() == null) return null;
        return handle.getBlockPos().offset(shipOffset.subtract(handle.shipOffset()));
    }

    @Nullable
    private static BlockPos verifiedSeatPosition(Level level, @Nullable BlockPos candidate) {
        if (candidate == null) return null;
        if (level.getBlockState(candidate).getBlock() instanceof SeatBlock) return candidate;
        return findSeatEntityAt(level, candidate, null) != null ? candidate : null;
    }

    /**
     * The SeatEntity living in the target block's exact cube, mirroring how
     * Create's {@code SeatBlock.use} and {@code SeatBlock.isSeatOccupied} look
     * seats up: the seat entity is spawned dead-center in the block (0.25 wide)
     * and VS2 keeps every ship's seat entities at that ship's shipyard
     * coordinates, so an exact-block scan can never match a seat of a nearby
     * ship. No radius, no distance tolerance. A stored UUID is only a
     * preference for role bookkeeping; the block position is the identity.
     */
    @Nullable
    private static Entity findSeatEntityAt(Level level, BlockPos target, @Nullable UUID seatUuid) {
        for (Entity candidate : level.getEntitiesOfClass(SeatEntity.class, new net.minecraft.world.phys.AABB(target)))
            if (seatUuid == null || candidate.getUUID().equals(seatUuid)) return candidate;
        for (Entity candidate : level.getEntitiesOfClass(SeatEntity.class, new net.minecraft.world.phys.AABB(target)))
            return candidate;
        return null;
    }

    /** Nearest SeatBlock within a small cube around the expected position. */
    @Nullable
    private static BlockPos findNearbySeatBlock(Level level, BlockPos center) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-2, -2, -2), center.offset(2, 2, 2))) {
            if (!(level.getBlockState(pos).getBlock() instanceof SeatBlock)) continue;
            double dist = pos.distSqr(center);
            if (dist < bestDist) {
                bestDist = dist;
                best = pos.immutable();
            }
        }
        return best;
    }

    /**
     * Writes a verified position back into the stored frame so the link
     * converges onto the live ship frame: seatPos becomes the verified
     * shipyard position and the AABB-min offset is re-captured against the
     * ship currently managing it. Legacy pasted handles (whose seatPos still
     * points into the schematic's source-world shipyard) migrate on their
     * first successful resolution.
     */
    private void healFrame(Level level, VehicleMountHandleBlockEntity handle, BlockPos verified) {
        if (handle == null || level.isClientSide) return;
        VehicleSetupShipPosition live = VehicleSetupShipPosition.at(level, verified);
        long newShipId = live != null ? live.shipId() : shipId;
        BlockPos newOffset = live != null ? live.offset() : shipOffset;
        if (verified.equals(seatPos) && newShipId == shipId
                && (newOffset == null ? shipOffset == null : newOffset.equals(shipOffset))) return;
        VSAnalogWarfare.LOGGER.warn("[VSAW setup-debug] Seat link frame refreshed: role={}, shipId {} -> {}, "
                        + "seatPos {} -> {}, offset {} -> {}",
                role, shipId, newShipId, seatPos, verified, shipOffset, newOffset);
        handle.refreshSeatLink(this, new VehicleMountSeatLink(role, seatUuid, verified.immutable(),
                newShipId, newOffset, handlePos));
        handle.recaptureShipPosition();
    }
}
