package com.erika.vsanalogwarfare.decorationbearing;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.foundation.utility.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Create's controlled entity only has one angle; this entity carries both cannon axes. */
public class DecorationBearingContraptionEntity extends ControlledContraptionEntity {
    private static final EntityDataAccessor<Float> SYNCED_YAW = SynchedEntityData.defineId(
            DecorationBearingContraptionEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SYNCED_PITCH = SynchedEntityData.defineId(
            DecorationBearingContraptionEntity.class, EntityDataSerializers.FLOAT);

    private float previousYaw;
    private float decorationYaw;
    private float previousPitch;
    private float decorationPitch;
    private Vec3 pivotOffset = Vec3.ZERO;

    public DecorationBearingContraptionEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    public static DecorationBearingContraptionEntity create(Level level, DecorationBearingBlockEntity controller,
                                                             Contraption contraption, Direction facing,
                                                             Vec3 pivotOffset) {
        DecorationBearingContraptionEntity entity = new DecorationBearingContraptionEntity(
                com.erika.vsanalogwarfare.registry.ModEntities.DECORATION_BEARING_CONTRAPTION.get(), level);
        entity.setContraption(contraption);
        entity.controllerPos = controller.getBlockPos();
        entity.setInitialPosition(facing);
        entity.setRotationAxis(facing.getAxis());
        entity.pivotOffset = pivotOffset;
        return entity;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(SYNCED_YAW, 0.0f);
        entityData.define(SYNCED_PITCH, 0.0f);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (!level().isClientSide) return;
        if (key == SYNCED_YAW) {
            previousYaw = decorationYaw;
            decorationYaw = entityData.get(SYNCED_YAW);
        } else if (key == SYNCED_PITCH) {
            previousPitch = decorationPitch;
            decorationPitch = entityData.get(SYNCED_PITCH);
        }
    }

    private void setInitialPosition(Direction facing) {
        BlockPos position = controllerPos.relative(facing);
        setPos(position.getX(), position.getY(), position.getZ());
    }

    public void setDecorationRotation(float yaw, float pitch) {
        previousYaw = decorationYaw;
        previousPitch = decorationPitch;
        decorationYaw = yaw;
        decorationPitch = pitch;
        if (!level().isClientSide) {
            entityData.set(SYNCED_YAW, yaw);
            entityData.set(SYNCED_PITCH, pitch);
        }
    }

    public float getDecorationYaw(float partialTicks) {
        float difference = ((decorationYaw - previousYaw + 540.0f) % 360.0f) - 180.0f;
        return previousYaw + difference * partialTicks;
    }

    public float getDecorationPitch(float partialTicks) {
        return previousPitch + (decorationPitch - previousPitch) * partialTicks;
    }

    @Override
    public AbstractContraptionEntity.ContraptionRotationState getRotationState() {
        AbstractContraptionEntity.ContraptionRotationState state =
                new AbstractContraptionEntity.ContraptionRotationState();
        state.xRotation = decorationPitch;
        state.yRotation = decorationYaw;
        state.zRotation = 0;
        return state;
    }

    @Override
    public Vec3 applyRotation(Vec3 vector, float partialTicks) {
        Vec3 rotated = VecHelper.rotate(vector, getDecorationPitch(partialTicks), Direction.Axis.X);
        return VecHelper.rotate(rotated, getDecorationYaw(partialTicks), Direction.Axis.Y);
    }

    @Override
    public Vec3 reverseRotation(Vec3 vector, float partialTicks) {
        Vec3 rotated = VecHelper.rotate(vector, -getDecorationYaw(partialTicks), Direction.Axis.Y);
        return VecHelper.rotate(rotated, -getDecorationPitch(partialTicks), Direction.Axis.X);
    }

    @Override
    public Vec3 toGlobalVector(Vec3 localVec, float partialTicks, boolean prevAnchor) {
        Vec3 anchor = prevAnchor ? getPrevAnchorVec() : getAnchorVec();
        Vec3 rotated = localVec.subtract(pivotOffset);
        rotated = applyRotation(rotated, partialTicks).add(pivotOffset);
        return rotated.add(anchor);
    }

    @Override
    public Vec3 toLocalVector(Vec3 globalVec, float partialTicks, boolean prevAnchor) {
        Vec3 anchor = prevAnchor ? getPrevAnchorVec() : getAnchorVec();
        Vec3 local = globalVec.subtract(anchor).subtract(pivotOffset);
        return reverseRotation(local, partialTicks).add(pivotOffset);
    }

    @Override
    public void applyLocalTransforms(PoseStack poseStack, float partialTicks) {
        poseStack.translate(pivotOffset.x, pivotOffset.y, pivotOffset.z);
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(getDecorationYaw(partialTicks)));
        poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(getDecorationPitch(partialTicks)));
        poseStack.translate(-pivotOffset.x, -pivotOffset.y, -pivotOffset.z);
    }

    @Override
    protected StructureTransform makeStructureTransform() {
        BlockPos offset = BlockPos.containing(getAnchorVec().add(0.5, 0.5, 0.5));
        Direction.Axis axis = getRotationAxis();
        float rotX = axis == Direction.Axis.X ? decorationYaw : decorationPitch;
        float rotY = axis == Direction.Axis.Y ? decorationYaw : 0;
        float rotZ = axis == Direction.Axis.Z ? decorationYaw : decorationPitch;
        return new StructureTransform(offset, rotX, rotY, rotZ);
    }

    @Override
    protected float getStalledAngle() {
        return decorationYaw;
    }

    @Override
    protected void handleStallInformation(double x, double y, double z, float angle) {
        decorationYaw = angle;
        previousYaw = angle;
    }

    @Override
    protected void writeAdditional(CompoundTag tag, boolean clientPacket) {
        super.writeAdditional(tag, clientPacket);
        tag.putFloat("DecorationYaw", decorationYaw);
        tag.putFloat("DecorationPitch", decorationPitch);
        tag.putDouble("PivotX", pivotOffset.x);
        tag.putDouble("PivotY", pivotOffset.y);
        tag.putDouble("PivotZ", pivotOffset.z);
    }

    @Override
    protected void readAdditional(CompoundTag tag, boolean clientPacket) {
        super.readAdditional(tag, clientPacket);
        decorationYaw = tag.getFloat("DecorationYaw");
        previousYaw = decorationYaw;
        decorationPitch = tag.getFloat("DecorationPitch");
        previousPitch = decorationPitch;
        pivotOffset = new Vec3(tag.getDouble("PivotX"), tag.getDouble("PivotY"), tag.getDouble("PivotZ"));
    }
}
