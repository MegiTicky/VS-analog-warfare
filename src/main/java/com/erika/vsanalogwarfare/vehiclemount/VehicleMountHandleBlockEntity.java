package com.erika.vsanalogwarfare.vehiclemount;

import com.erika.vsanalogwarfare.registry.ModBlockEntities;
import com.erika.vsanalogwarfare.vehiclesetup.compat.VehicleSetupShipPosition;
import com.erika.vsanalogwarfare.vehiclesetup.compat.VehicleSetupReflection;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class VehicleMountHandleBlockEntity extends BlockEntity {
    private final List<VehicleMountSeatLink> seats = new ArrayList<>();
    private long shipId = -1L;
    private BlockPos shipOffset;
    private int revision;
    private int offsetX;
    private int offsetY;
    private int offsetZ;
    private boolean locked;
    private boolean redstonePowered;
    private int captureRetries;
    private transient Map<Long, Object> placedShips;

    public VehicleMountHandleBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.VEHICLE_MOUNT_HANDLE.get(), pos, state);
    }

    public List<VehicleMountSeatLink> seats() { return List.copyOf(seats); }
    public int revision() { return revision; }
    public int offsetX() { return offsetX; }
    public int offsetY() { return offsetY; }
    public int offsetZ() { return offsetZ; }
    public boolean locked() { return locked; }

    public void initializeRedstoneState(boolean powered) {
        redstonePowered = powered;
        setChanged();
    }

    public void updateRedstoneState(boolean powered) {
        if (powered && !redstonePowered) {
            locked = !locked;
            setChanged();
            if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        if (powered != redstonePowered) {
            redstonePowered = powered;
            setChanged();
        }
    }

    public void push(int x, int y, int z) {
        int nextX = clampOffset(offsetX + x);
        int nextY = clampOffset(offsetY + y);
        int nextZ = clampOffset(offsetZ + z);
        if (nextX == offsetX && nextY == offsetY && nextZ == offsetZ) return;
        offsetX = nextX;
        offsetY = nextY;
        offsetZ = nextZ;
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    private static int clampOffset(int value) {
        return Math.max(-8, Math.min(8, value));
    }

    public void captureShipPosition() {
        if (level == null || shipId >= 0L || captureRetries >= 40) return;
        captureRetries++;
        VehicleSetupShipPosition position = VehicleSetupShipPosition.at(level, worldPosition);
        if (position != null) {
            shipId = position.shipId();
            shipOffset = position.offset();
            markChanged();
        }
    }

    public long shipId() { return shipId; }
    public BlockPos shipOffset() { return shipOffset; }

    public BlockPos currentPosition() {
        if (level == null || shipId < 0L || shipOffset == null) return worldPosition;
        Object mapped = placedShips == null ? null : placedShips.get(shipId);
        if (mapped != null) {
            BlockPos resolved = VehicleSetupReflection.positionOnShip(mapped, shipOffset);
            if (resolved != null) return resolved;
        }
        for (Object ship : com.erika.vsanalogwarfare.scope.compat.VsCompat.getAllShips(level)) {
            try {
                if (((Number) VehicleSetupReflection.invoke(ship, "getId")).longValue() == shipId) {
                    BlockPos resolved = VehicleSetupReflection.positionOnShip(ship, shipOffset);
                    if (resolved != null) return resolved;
                }
            } catch (ReflectiveOperationException ignored) { }
        }
        return worldPosition;
    }

    public Vec3 currentWorldPosition() {
        BlockPos position = currentPosition();
        if (level == null) return Vec3.atCenterOf(position);
        Object ship = placedShips == null ? null : placedShips.get(shipId);
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
        if (ship == null) return Vec3.atCenterOf(position);
        Vec3 transformed = com.erika.vsanalogwarfare.scope.compat.VsCompat.shipToWorldPosition(
                ship, Vec3.atCenterOf(position));
        return transformed == null ? Vec3.atCenterOf(position) : transformed;
    }

    public void setPlacedShips(Map<Long, Object> ships) {
        placedShips = ships;
    }

    public Object placedShip(long originalShipId) {
        return placedShips == null ? null : placedShips.get(originalShipId);
    }

    public void remapPlacedPosition(BlockPos actualPosition) {
        for (int i = 0; i < seats.size(); i++) {
            VehicleMountSeatLink link = seats.get(i);
            seats.set(i, new VehicleMountSeatLink(link.role(), link.seatUuid(), link.seatPos(), link.shipId(), link.shipOffset(), actualPosition));
        }
        setChanged();
    }

    public void addSeat(VehicleMountSeatLink link) {
        seats.removeIf(existing -> existing.shipId() == link.shipId() && existing.shipOffset().equals(link.shipOffset()));
        seats.add(link);
        markChanged();
    }

    public boolean removeSeat(int index) {
        if (index < 0 || index >= seats.size()) return false;
        seats.remove(index);
        markChanged();
        return true;
    }

    public boolean renameSeat(int index, String role) {
        if (index < 0 || index >= seats.size() || role.isBlank() || role.length() > 32) return false;
        seats.set(index, seats.get(index).withRole(role));
        markChanged();
        return true;
    }

    private void markChanged() {
        revision++;
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putLong("ShipId", shipId);
        if (shipOffset != null) tag.putLong("ShipOffset", shipOffset.asLong());
        tag.putInt("Revision", revision);
        tag.putInt("OffsetX", offsetX);
        tag.putInt("OffsetY", offsetY);
        tag.putInt("OffsetZ", offsetZ);
        tag.putBoolean("Locked", locked);
        tag.putBoolean("RedstonePowered", redstonePowered);
        ListTag list = new ListTag();
        for (VehicleMountSeatLink seat : seats) list.add(seat.save());
        tag.put("Seats", list);
    }

    @Override public void load(CompoundTag tag) {
        super.load(tag);
        seats.clear();
        shipId = tag.getLong("ShipId");
        shipOffset = tag.contains("ShipOffset") ? BlockPos.of(tag.getLong("ShipOffset")) : null;
        captureRetries = shipId >= 0L ? 40 : 0;
        revision = tag.getInt("Revision");
        offsetX = clampOffset(tag.getInt("OffsetX"));
        offsetY = clampOffset(tag.getInt("OffsetY"));
        offsetZ = clampOffset(tag.getInt("OffsetZ"));
        locked = tag.getBoolean("Locked");
        redstonePowered = tag.getBoolean("RedstonePowered");
        if (!tag.contains("Seats", Tag.TAG_LIST)) return;
        ListTag list = tag.getList("Seats", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            VehicleMountSeatLink link = VehicleMountSeatLink.load(list.getCompound(i));
            if (link != null) seats.add(link);
        }
    }

    @Override public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }
    @Override public void handleUpdateTag(CompoundTag tag) {
        load(tag);
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
