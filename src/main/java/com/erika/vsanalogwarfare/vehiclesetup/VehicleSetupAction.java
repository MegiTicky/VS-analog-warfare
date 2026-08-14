package com.erika.vsanalogwarfare.vehiclesetup;

import com.erika.vsanalogwarfare.VSAnalogWarfare;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

public final class VehicleSetupAction {
    public static final int FORMAT_VERSION = 2;

    private final VehicleSetupActionType type;
    @Nullable private final BlockPos targetOffset;
    @Nullable private final BlockPos secondaryOffset;
    @Nullable private final BlockPos shipOffset;
    private final long targetShipId;
    private final long secondaryShipId;
    @Nullable private final CompoundTag blockState;
    @Nullable private final CompoundTag controller;
    private final float stiffness;

    private VehicleSetupAction(VehicleSetupActionType type, @Nullable BlockPos targetOffset,
                               @Nullable BlockPos secondaryOffset, @Nullable BlockPos shipOffset,
                               long targetShipId, long secondaryShipId,
                               @Nullable CompoundTag blockState, @Nullable CompoundTag controller, float stiffness) {
        this.type = type;
        this.targetOffset = targetOffset;
        this.secondaryOffset = secondaryOffset;
        this.shipOffset = shipOffset;
        this.targetShipId = targetShipId;
        this.secondaryShipId = secondaryShipId;
        this.blockState = blockState;
        this.controller = controller;
        this.stiffness = stiffness;
    }

    public static VehicleSetupAction placeBlock(long shipId, @Nullable BlockPos shipOffset,
                                                BlockPos anchorOffset, BlockState state) {
        return new VehicleSetupAction(VehicleSetupActionType.PLACE_BLOCK, anchorOffset, null, shipOffset,
                shipId, -1L, NbtUtils.writeBlockState(state), null, 0.0f);
    }

    public static VehicleSetupAction removeBlock(long shipId, @Nullable BlockPos shipOffset, BlockPos anchorOffset) {
        return new VehicleSetupAction(VehicleSetupActionType.REMOVE_BLOCK, anchorOffset, null, shipOffset,
                shipId, -1L, null, null, 0.0f);
    }

    public static VehicleSetupAction linkDbwBackups(long sourceShipId, BlockPos sourceOffset,
                                                     long targetShipId, BlockPos targetOffset) {
        return new VehicleSetupAction(VehicleSetupActionType.LINK_DBW_BACKUPS, sourceOffset, targetOffset, null,
                sourceShipId, targetShipId, null, null, 0.0f);
    }

    public static VehicleSetupAction createTweakedController(long shipId, BlockPos hubOffset, ItemStack controller) {
        return new VehicleSetupAction(VehicleSetupActionType.CREATE_TWEAKED_CONTROLLER, hubOffset, null, null,
                shipId, -1L, null, controller.save(new CompoundTag()), 0.0f);
    }

    public static VehicleSetupAction setTrackworkStiffness(float stiffness) {
        return new VehicleSetupAction(VehicleSetupActionType.SET_TRACKWORK_STIFFNESS, null, null, null, -1L, -1L,
                null, null, stiffness);
    }

    public VehicleSetupActionType type() { return type; }
    @Nullable public BlockPos targetOffset() { return targetOffset; }
    @Nullable public BlockPos secondaryOffset() { return secondaryOffset; }
    @Nullable public BlockPos shipOffset() { return shipOffset; }
    public long targetShipId() { return targetShipId; }
    public long secondaryShipId() { return secondaryShipId; }
    @Nullable public CompoundTag blockState() { return blockState == null ? null : blockState.copy(); }
    @Nullable public CompoundTag controller() { return controller == null ? null : controller.copy(); }
    public float stiffness() { return stiffness; }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("FormatVersion", FORMAT_VERSION);
        tag.putString("Type", type.name());
        if (targetOffset != null) tag.putLong("TargetOffset", targetOffset.asLong());
        if (secondaryOffset != null) tag.putLong("SecondaryOffset", secondaryOffset.asLong());
        if (shipOffset != null) tag.putLong("ShipOffset", shipOffset.asLong());
        if (targetShipId >= 0L) tag.putLong("TargetShipId", targetShipId);
        if (secondaryShipId >= 0L) tag.putLong("SecondaryShipId", secondaryShipId);
        if (blockState != null) tag.put("BlockState", blockState.copy());
        if (controller != null) tag.put("Controller", controller.copy());
        tag.putFloat("Stiffness", stiffness);
        return tag;
    }

    @Nullable
    public static VehicleSetupAction load(CompoundTag tag) {
        try {
            int formatVersion = tag.getInt("FormatVersion");
            if (formatVersion > FORMAT_VERSION) {
                VSAnalogWarfare.LOGGER.warn("[VSAW] Skipping vehicle setup action from a newer format (version {} "
                        + "> supported {}); update the mod to read this setup.", formatVersion, FORMAT_VERSION);
                return null;
            }
            return new VehicleSetupAction(VehicleSetupActionType.valueOf(tag.getString("Type")),
                    tag.contains("TargetOffset") ? BlockPos.of(tag.getLong("TargetOffset")) : null,
                    tag.contains("SecondaryOffset") ? BlockPos.of(tag.getLong("SecondaryOffset")) : null,
                    tag.contains("ShipOffset") ? BlockPos.of(tag.getLong("ShipOffset")) : null,
                    tag.contains("TargetShipId") ? tag.getLong("TargetShipId") : -1L,
                    tag.contains("SecondaryShipId") ? tag.getLong("SecondaryShipId") : -1L,
                    tag.contains("BlockState") ? tag.getCompound("BlockState").copy() : null,
                    tag.contains("Controller") ? tag.getCompound("Controller").copy() : null,
                    tag.getFloat("Stiffness"));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
