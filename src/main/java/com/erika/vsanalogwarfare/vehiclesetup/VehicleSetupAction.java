package com.erika.vsanalogwarfare.vehiclesetup;

import com.erika.vsanalogwarfare.VSAnalogWarfare;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

public final class VehicleSetupAction {
    public static final int FORMAT_VERSION = 9;

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
    @Nullable private final CompoundTag interactionItem;
    private final int interactionHand;
    private final int interactionFace;
    private final boolean interactionSneaking;
    private final int transmitterChannel;
    @Nullable private final String transmitterPassword;
    private final int delayBeforeTicks;

    private VehicleSetupAction(VehicleSetupActionType type, @Nullable BlockPos targetOffset,
                               @Nullable BlockPos secondaryOffset, @Nullable BlockPos shipOffset,
                               long targetShipId, long secondaryShipId,
                                 @Nullable CompoundTag blockState, @Nullable CompoundTag controller, float stiffness,
                                 float yaw, int muzzleOffset, @Nullable String tallyhoEntity,
                                 @Nullable CompoundTag tallyhoState, int tallyhoVariant,
                                 double positionOffsetX, double positionOffsetY, double positionOffsetZ,
                                   @Nullable CompoundTag interactionItem, int interactionHand, int interactionFace,
                                   boolean interactionSneaking) {
        this(type, targetOffset, secondaryOffset, shipOffset, targetShipId, secondaryShipId, blockState, controller,
                stiffness, yaw, muzzleOffset, tallyhoEntity, tallyhoState, tallyhoVariant, positionOffsetX,
                positionOffsetY, positionOffsetZ, interactionItem, interactionHand, interactionFace,
                interactionSneaking, 0, null, 0);
    }

    private VehicleSetupAction(VehicleSetupActionType type, @Nullable BlockPos targetOffset,
                               @Nullable BlockPos secondaryOffset, @Nullable BlockPos shipOffset,
                               long targetShipId, long secondaryShipId,
                               @Nullable CompoundTag blockState, @Nullable CompoundTag controller, float stiffness,
                               float yaw, int muzzleOffset, @Nullable String tallyhoEntity,
                               @Nullable CompoundTag tallyhoState, int tallyhoVariant,
                               double positionOffsetX, double positionOffsetY, double positionOffsetZ,
                               @Nullable CompoundTag interactionItem, int interactionHand, int interactionFace,
                               boolean interactionSneaking, int transmitterChannel,
                                @Nullable String transmitterPassword, int delayBeforeTicks) {
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
        this.interactionItem = interactionItem;
        this.interactionHand = interactionHand;
        this.interactionFace = interactionFace;
        this.interactionSneaking = interactionSneaking;
        this.transmitterChannel = transmitterChannel;
        this.transmitterPassword = transmitterPassword;
        this.delayBeforeTicks = Math.max(0, delayBeforeTicks);
    }

    private VehicleSetupAction(VehicleSetupActionType type, @Nullable BlockPos targetOffset,
                               @Nullable BlockPos secondaryOffset, @Nullable BlockPos shipOffset,
                               long targetShipId, long secondaryShipId,
                               @Nullable CompoundTag blockState, @Nullable CompoundTag controller, float stiffness,
                               float yaw, int muzzleOffset, @Nullable String tallyhoEntity,
                               @Nullable CompoundTag tallyhoState, int tallyhoVariant,
                               double positionOffsetX, double positionOffsetY, double positionOffsetZ,
                               @Nullable CompoundTag interactionItem, int interactionHand, int interactionFace) {
        this(type, targetOffset, secondaryOffset, shipOffset, targetShipId, secondaryShipId, blockState, controller,
                stiffness, yaw, muzzleOffset, tallyhoEntity, tallyhoState, tallyhoVariant, positionOffsetX,
                positionOffsetY, positionOffsetZ, interactionItem, interactionHand, interactionFace, false);
    }

    public static VehicleSetupAction placeBlock(long shipId, @Nullable BlockPos shipOffset,
                                                BlockPos anchorOffset, BlockState state) {
        return new VehicleSetupAction(VehicleSetupActionType.PLACE_BLOCK, anchorOffset, null, shipOffset,
                shipId, -1L, NbtUtils.writeBlockState(state), null, 0.0f, 0.0f, 0, null, null, 0, 0.0, 0.0, 0.0, null, 0, 0);
    }

    public static VehicleSetupAction removeBlock(long shipId, @Nullable BlockPos shipOffset, BlockPos anchorOffset) {
        return removeBlock(shipId, shipOffset, anchorOffset, null);
    }

    public static VehicleSetupAction removeBlock(long shipId, @Nullable BlockPos shipOffset, BlockPos anchorOffset,
                                                 @Nullable BlockState state) {
        return new VehicleSetupAction(VehicleSetupActionType.REMOVE_BLOCK, anchorOffset, null, shipOffset,
                shipId, -1L, state == null ? null : NbtUtils.writeBlockState(state), null,
                0.0f, 0.0f, 0, null, null, 0, 0.0, 0.0, 0.0, null, 0, 0);
    }

    public static VehicleSetupAction linkDbwBackups(long sourceShipId, BlockPos sourceOffset,
                                                     long targetShipId, BlockPos targetOffset) {
        return new VehicleSetupAction(VehicleSetupActionType.LINK_DBW_BACKUPS, sourceOffset, targetOffset, null,
                sourceShipId, targetShipId, null, null, 0.0f, 0.0f, 0, null, null, 0, 0.0, 0.0, 0.0, null, 0, 0);
    }

    public static VehicleSetupAction createTweakedController(long shipId, BlockPos hubOffset, ItemStack controller) {
        return new VehicleSetupAction(VehicleSetupActionType.CREATE_TWEAKED_CONTROLLER, hubOffset, null, null,
                shipId, -1L, null, controller.save(new CompoundTag()), 0.0f, 0.0f, 0, null, null, 0, 0.0, 0.0, 0.0, null, 0, 0);
    }

    public static VehicleSetupAction setTrackworkStiffness(float stiffness) {
        return new VehicleSetupAction(VehicleSetupActionType.SET_TRACKWORK_STIFFNESS, null, null, null, -1L, -1L,
                null, null, stiffness, 0.0f, 0, null, null, 0, 0.0, 0.0, 0.0, null, 0, 0);
    }

    public static VehicleSetupAction setTrackworkStiffness(long shipId, @Nullable BlockPos shipOffset,
                                                            BlockPos targetOffset, float stiffness) {
        return new VehicleSetupAction(VehicleSetupActionType.SET_TRACKWORK_STIFFNESS, targetOffset, null,
                shipOffset, shipId, -1L, null, null, stiffness, 0.0f, 0, null, null, 0,
                0.0, 0.0, 0.0, null, 0, 0);
    }

    public static VehicleSetupAction spawnTallyhoHullMg(long shipId, @Nullable BlockPos shipOffset,
                                                          BlockPos anchorOffset, float yaw, int muzzleOffset) {
        return new VehicleSetupAction(VehicleSetupActionType.SPAWN_TALLYHO_HULL_MG, anchorOffset, null, shipOffset,
                shipId, -1L, null, null, 0.0f, yaw, muzzleOffset, null, null, 0, 0.0, 0.0, 0.0, null, 0, 0);
    }

    public static VehicleSetupAction spawnTallyhoEntity(long shipId, @Nullable BlockPos shipOffset,
                                                         BlockPos anchorOffset, Vec3 positionOffset, String entityId,
                                                         float yaw, int variant, CompoundTag state) {
        return new VehicleSetupAction(VehicleSetupActionType.SPAWN_TALLYHO_ENTITY, anchorOffset, null, shipOffset,
                shipId, -1L, null, null, 0.0f, yaw, 0, entityId, state, variant,
                positionOffset.x, positionOffset.y, positionOffset.z, null, 0, 0);
    }

    public static VehicleSetupAction interactWithBlock(long shipId, @Nullable BlockPos shipOffset,
                                                         BlockPos anchorOffset, ItemStack item,
                                                         InteractionHand hand, Direction face, Vec3 hitOffset,
                                                         boolean sneaking) {
        return new VehicleSetupAction(VehicleSetupActionType.GENERIC_BLOCK_INTERACTION, anchorOffset, null, shipOffset,
                shipId, -1L, null, null, 0.0f, 0.0f, 0, null, null, 0,
                hitOffset.x, hitOffset.y, hitOffset.z, item.save(new CompoundTag()), hand.ordinal(), face.ordinal(), sneaking);
    }

    public static VehicleSetupAction interactWithBlock(long shipId, @Nullable BlockPos shipOffset,
                                                         BlockPos anchorOffset, ItemStack item,
                                                         InteractionHand hand, Direction face, Vec3 hitOffset) {
        return interactWithBlock(shipId, shipOffset, anchorOffset, item, hand, face, hitOffset, false);
    }

    public static VehicleSetupAction leftClickBlock(long shipId, @Nullable BlockPos shipOffset,
                                                     BlockPos anchorOffset, ItemStack item,
                                                     Direction face, boolean sneaking) {
        return new VehicleSetupAction(VehicleSetupActionType.GENERIC_BLOCK_LEFT_CLICK, anchorOffset, null, shipOffset,
                shipId, -1L, null, null, 0.0f, 0.0f, 0, null, null, 0,
                0.5, 0.5, 0.5, item.save(new CompoundTag()), InteractionHand.MAIN_HAND.ordinal(), face.ordinal(), sneaking);
    }

    public static VehicleSetupAction configureEnderTransmitter(long shipId, BlockPos shipOffset,
                                                                BlockPos anchorOffset, int channel,
                                                                String password) {
        return new VehicleSetupAction(VehicleSetupActionType.CONFIGURE_ENDER_TRANSMITTER, anchorOffset, null,
                shipOffset, shipId, -1L, null, null, 0.0f, 0.0f, 0, null, null, 0,
                0.0, 0.0, 0.0, null, 0, 0, false, channel, password, 0);
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
    @Nullable public CompoundTag interactionItem() { return interactionItem == null ? null : interactionItem.copy(); }
    public InteractionHand interactionHand() {
        return interactionHand == InteractionHand.OFF_HAND.ordinal() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    }
    public Direction interactionFace() { return Direction.from3DDataValue(interactionFace); }
    public boolean interactionSneaking() { return interactionSneaking; }
    public int transmitterChannel() { return transmitterChannel; }
    @Nullable public String transmitterPassword() { return transmitterPassword; }
    public int delayBeforeTicks() { return delayBeforeTicks; }

    public VehicleSetupAction withDelayBeforeTicks(int delayBeforeTicks) {
        return new VehicleSetupAction(type, targetOffset, secondaryOffset, shipOffset, targetShipId, secondaryShipId,
                blockState, controller, stiffness, yaw, muzzleOffset, tallyhoEntity, tallyhoState, tallyhoVariant,
                positionOffsetX, positionOffsetY, positionOffsetZ, interactionItem, interactionHand, interactionFace,
                interactionSneaking, transmitterChannel, transmitterPassword, delayBeforeTicks);
    }

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
        if (interactionItem != null) tag.put("InteractionItem", interactionItem.copy());
        tag.putInt("InteractionHand", interactionHand);
        tag.putInt("InteractionFace", interactionFace);
        tag.putBoolean("InteractionSneaking", interactionSneaking);
        tag.putInt("DelayBeforeTicks", delayBeforeTicks);
        if (type == VehicleSetupActionType.CONFIGURE_ENDER_TRANSMITTER) {
            tag.putInt("TransmitterChannel", transmitterChannel);
            if (transmitterPassword != null) tag.putString("TransmitterPassword", transmitterPassword);
        }
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
                    tag.getDouble("PositionOffsetY"), tag.getDouble("PositionOffsetZ"),
                    tag.contains("InteractionItem") ? tag.getCompound("InteractionItem").copy() : null,
                     tag.getInt("InteractionHand"), tag.getInt("InteractionFace"), tag.getBoolean("InteractionSneaking"),
                      tag.getInt("TransmitterChannel"),
                      tag.contains("TransmitterPassword") ? tag.getString("TransmitterPassword") : null,
                      tag.getInt("DelayBeforeTicks"));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
