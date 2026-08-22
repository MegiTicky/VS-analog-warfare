package com.erika.vsanalogwarfare.vehiclemount;

import com.erika.vsanalogwarfare.VSAnalogWarfare;
import com.erika.vsanalogwarfare.vehiclesetup.compat.VehicleSetupReflection;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import com.simibubi.create.content.contraptions.actors.seat.SeatBlock;
import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;

import java.util.UUID;

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
        for (Entity candidate : level.getEntitiesOfClass(Entity.class,
                new net.minecraft.world.phys.AABB(target).inflate(2.0),
                candidate -> candidate instanceof com.simibubi.create.content.contraptions.actors.seat.SeatEntity
                        && (candidate.getUUID().equals(seatUuid)
                        || candidate.distanceToSqr(Vec3.atCenterOf(target)) <= 1.5))) return candidate;
        return null;
    }

    public Entity createSeat(Level level, VehicleMountHandleBlockEntity handle) {
        BlockPos target = shipyardPosition(level, handle);
        if (target == null || !(level.getBlockState(target).getBlock() instanceof SeatBlock)) return null;
        SeatEntity seat = new SeatEntity(level, target);
        seat.setPos(target.getX() + .5, target.getY(), target.getZ() + .5);
        level.addFreshEntity(seat);
        return seat;
    }

    private BlockPos shipyardPosition(Level level, VehicleMountHandleBlockEntity handle) {
        Object ship = handle == null ? null : handle.placedShip(shipId);
        if (ship == null) {
            for (Object candidate : com.erika.vsanalogwarfare.scope.compat.VsCompat.getAllShips(level)) {
                try {
                    if (((Number) VehicleSetupReflection.invoke(candidate, "getId")).longValue() == shipId) {
                        ship = candidate;
                        break;
                    }
                } catch (ReflectiveOperationException ignored) { }
            }
        }
        BlockPos resolved = ship == null ? null : VehicleSetupReflection.positionOnShip(ship, shipOffset);
        if (resolved != null && handle != null && shipId >= 0L && handle.shipId() == shipId
                && shipOffset != null && handle.shipOffset() != null) {
            BlockPos anchor = handle.getBlockPos();
            BlockPos seat = anchor.offset(shipOffset.subtract(handle.shipOffset()));
            if (level.getBlockState(seat).getBlock() instanceof SeatBlock) {
                if (!seat.equals(resolved)) {
                    VSAnalogWarfare.LOGGER.warn("[VSAW setup-debug] Same-ship seat correction: "
                                    + "originalShipId={}, aabbTarget={}, anchorTarget={}, delta={}",
                            shipId, resolved, seat, seat.subtract(resolved));
                }
                return seat;
            }
        }
        return resolved;
    }
}
