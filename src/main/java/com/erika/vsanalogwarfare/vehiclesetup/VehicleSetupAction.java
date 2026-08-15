package com.erika.vsanalogwarfare.vehiclesetup;

import com.erika.vsanalogwarfare.VSAnalogWarfare;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

public final class VehicleSetupAction {
    public static final int FORMAT_VERSION = 5;

    private final VehicleSetupActionType type;
    @Nullable private final BlockPos targetOffset;
    @Nullable private final BlockPos secondaryOffset;
    @Nullable private final BlockPos shipOffset;
    private final long targetShipId;
    private final long secondaryShipId;
    @Nullable private final CompoundTag blockState;
    @Nullable private final CompoundTag controller;
    private final float stiffness;
    private final float yaw;
    private final int muzzleOffset;
    @Nullable private final String tallyhoEntity;
    @Nullable private final CompoundTag tallyhoState;
    private final int tallyhoVariant;
    private final double positionOffsetX;
    private final double positionOffsetY;
    private final double positionOffsetZ;

    private VehicleSetupAction(VehicleSetupActionType type, @Nullable BlockPos targetOffset,
                               @Nullable BlockPos secondaryOffset, @Nullable BlockPos shipOffset,
                               long targetShipId, long secondaryShipId,
                                 @Nullable CompoundTag blockState, @Nullable CompoundTag controller, float stiffness,
                                 float yaw, int muzzleOffset, @Nullable String tallyhoEntity,
                                 @Nullable CompoundTag tallyhoState, int tallyhoVariant,
                                 double positionOffsetX, double positionOffsetY, double positionOffsetZ) {
        this.type = type;
        this.targetOffset = targetOffset;
        this.secondaryOffset = secondaryOffset;
        this.shipOffset = shipOffset;
        this.targetShipId = targetShipId;
        this.secondaryShipId = secondaryShipId;
        this.blockState = blockState;
        this.controller = controller;
        this.stiffness = stiffness;
        this.yaw = yaw;
        this.muzzleOffset = muzzleOffset;
        this.tallyhoEntity = tallyhoEntity;
        this.tallyhoState = tallyhoState;
        this.tallyhoVariant = tallyhoVariant;
        this.positionOffsetX = positionOffsetX;
        this.positionOffsetY = positionOffsetY;
        this.positionOffsetZ = positionOffsetZ;
    }

    public static VehicleSetupAction placeBlock(long shipId, @Nullable BlockPos shipOffset,
                                                BlockPos anchorOffset, BlockState state) {
        return new VehicleSetupAction(VehicleSetupActionType.PLACE_BLOCK, anchorOffset, null, shipOffset,
                shipId, -1L, NbtUtils.writeBlockState(state), null, 0.0f, 0.0f, 0, null, null, 0, 0.0, 0.0, 0.0);
    }

    public static VehicleSetupAction removeBlock(long shipId, @Nullable BlockPos shipOffset, BlockPos anchorOffset) {
        return new VehicleSetupAction(VehicleSetupActionType.REMOVE_BLOCK, anchorOffset, null, shipOffset,
                shipId, -1L, null, null, 0.0f, 0.0f, 0, null, null, 0, 0.0, 0.0, 0.0);
    }

    public static VehicleSetupAction linkDbwBackups(long sourceShipId, BlockPos sourceOffset,
                                                     long targetShipId, BlockPos targetOffset) {
        return new VehicleSetupAction(VehicleSetupActionType.LINK_DBW_BACKUPS, sourceOffset, targetOffset, null,
                sourceShipId, targetShipId, null, null, 0.0f, 0.0f, 0, null, null, 0, 0.0, 0.0, 0.0);
    }

    public static VehicleSetupAction createTweakedController(long shipId, BlockPos hubOffset, ItemStack controller) {
        return new VehicleSetupAction(VehicleSetupActionType.CREATE_TWEAKED_CONTROLLER, hubOffset, null, null,
                shipId, -1L, null, controller.save(new CompoundTag()), 0.0f, 0.0f, 0, null, null, 0, 0.0, 0.0, 0.0);
    }

    public static VehicleSetupAction setTrackworkStiffness(float stiffness) {
        return new VehicleSetupAction(VehicleSetupActionType.SET_TRACKWORK_STIFFNESS, null, null, null, -1L, -1L,
                null, null, stiffness, 0.0f, 0, null, null, 0, 0.0, 0.0, 0.0);
    }

    public static VehicleSetupAction spawnTallyhoHullMg(long shipId, @Nullable BlockPos shipOffset,
                                                          BlockPos anchorOffset, float yaw, int muzzleOffset) {
        return new VehicleSetupAction(VehicleSetupActionType.SPAWN_TALLYHO_HULL_MG, anchorOffset, null, shipOffset,
                shipId, -1L, null, null, 0.0f, yaw, muzzleOffset, null, null, 0, 0.0, 0.0, 0.0);
    }

    public static VehicleSetupAction spawnTallyhoEntity(long shipId, @Nullable BlockPos shipOffset,
                                                         BlockPos anchorOffset, Vec3 positionOffset, String entityId,
                                                         float yaw, int variant, CompoundTag state) {
        return new VehicleSetupAction(VehicleSetupActionType.SPAWN_TALLYHO_ENTITY, anchorOffset, null, shipOffset,
                shipId, -1L, null, null, 0.0f, yaw, 0, entityId, state, variant,
                positionOffset.x, positionOffset.y, positionOffset.z);
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
    public float yaw() { return yaw; }
    public int muzzleOffset() { return muzzleOffset; }
    @Nullable public String tallyhoEntity() { return tallyhoEntity; }
    @Nullable public CompoundTag tallyhoState() { return tallyhoState == null ? null : tallyhoState.copy(); }
    public int tallyhoVariant() { return tallyhoVariant; }
    public Vec3 positionOffset() { return new Vec3(positionOffsetX, positionOffsetY, positionOffsetZ); }

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
        tag.putFloat("Yaw", yaw);
        tag.putInt("MuzzleOffset", muzzleOffset);
        if (tallyhoEntity != null) tag.putString("TallyhoEntity", tallyhoEntity);
        if (tallyhoState != null) tag.put("TallyhoState", tallyhoState.copy());
        tag.putInt("TallyhoVariant", tallyhoVariant);
        tag.putDouble("PositionOffsetX", positionOffsetX);
        tag.putDouble("PositionOffsetY", positionOffsetY);
        tag.putDouble("PositionOffsetZ", positionOffsetZ);
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
                    tag.getFloat("Stiffness"), tag.getFloat("Yaw"), tag.getInt("MuzzleOffset"),
                    tag.contains("TallyhoEntity") ? tag.getString("TallyhoEntity") : null,
                    tag.contains("TallyhoState") ? tag.getCompound("TallyhoState").copy() : null,
                    tag.getInt("TallyhoVariant"), tag.getDouble("PositionOffsetX"),
                    tag.getDouble("PositionOffsetY"), tag.getDouble("PositionOffsetZ"));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
