package com.erika.vsanalogwarfare.decorationbearing;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.OrientedContraptionEntity;
import com.simibubi.create.foundation.collision.Matrix3d;
import com.simibubi.create.foundation.utility.AngleHelper;
import com.simibubi.create.foundation.utility.VecHelper;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Matches CBC's PitchOrientedContraptionEntity: extends OrientedContraptionEntity,
 * owns yaw+pitch, disables Create's orientation updater, and syncs anchor every tick.
 */
public class DecorationBearingContraptionEntity extends OrientedContraptionEntity {
    private static final EntityDataAccessor<Float> SYNCED_YAW = SynchedEntityData.defineId(
            DecorationBearingContraptionEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SYNCED_PITCH = SynchedEntityData.defineId(
            DecorationBearingContraptionEntity.class, EntityDataSerializers.FLOAT);

    private BlockPos controllerPos;

    public DecorationBearingContraptionEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    public static DecorationBearingContraptionEntity create(Level level, DecorationBearingBlockEntity controller,
                                                             Contraption contraption, Direction initialOrientation) {
        DecorationBearingContraptionEntity entity = new DecorationBearingContraptionEntity(
                com.erika.vsanalogwarfare.registry.ModEntities.DECORATION_BEARING_CONTRAPTION.get(), level);
        entity.setContraption(contraption);
        entity.setInitialOrientation(initialOrientation);
        entity.startAtInitialYaw();
        entity.controllerPos = controller.getBlockPos();
        return entity;
    }

    // --- Interpolated rotation (matches OrientedContraptionEntity.m_5675_/m_5686_) ---

    private float getInterpolatedYaw(float partialTicks) {
        if (partialTicks >= 1.0f) return yaw;
        return prevYaw + (yaw - prevYaw) * partialTicks;
    }

    private float getInterpolatedPitch(float partialTicks) {
        if (partialTicks >= 1.0f) return pitch;
        return prevPitch + (pitch - prevPitch) * partialTicks;
    }

    // --- Synched data ---

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(SYNCED_YAW, 0.0f);
        entityData.define(SYNCED_PITCH, 0.0f);
    }

    /**
     * CBC overrides this to prevent the parent's synched-data handler from overwriting
     * the rotation fields. We do the same: save yaw before super, restore after.
     */
    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        float savedYaw = this.yaw;
        float savedPitch = this.pitch;
        float savedPrevYaw = this.prevYaw;
        float savedPrevPitch = this.prevPitch;
        super.onSyncedDataUpdated(key);
        this.yaw = savedYaw;
        this.pitch = savedPitch;
        this.prevYaw = savedPrevYaw;
        this.prevPitch = savedPrevPitch;

        if (!level().isClientSide) return;
        if (key == SYNCED_YAW) {
            float val = entityData.get(SYNCED_YAW);
            this.prevYaw = this.yaw;
            this.yaw = val;
        } else if (key == SYNCED_PITCH) {
            float val = entityData.get(SYNCED_PITCH);
            this.prevPitch = this.pitch;
            this.pitch = val;
        }
    }

    // --- Rotation set from BE ---

    public void setDecorationRotation(float newYaw, float newPitch) {
        this.prevYaw = this.yaw;
        this.prevPitch = this.pitch;
        this.yaw = newYaw;
        this.pitch = newPitch;
        if (!level().isClientSide) {
            entityData.set(SYNCED_YAW, newYaw);
            entityData.set(SYNCED_PITCH, newPitch);
        }
    }

    // --- Rotation chain: pitch → yaw → initialYaw ---

    private float getViewPitch(float partialTicks) {
        return getInterpolatedPitch(partialTicks);
    }

    @Override
    public Vec3 applyRotation(Vec3 vector, float partialTicks) {
        Direction.Axis pitchAxis = getInitialOrientation().getAxis() == Direction.Axis.X
                ? Direction.Axis.Z : Direction.Axis.X;
        Vec3 rotated = VecHelper.rotate(vector, getInterpolatedPitch(partialTicks), pitchAxis);
        rotated = VecHelper.rotate(rotated, getInterpolatedYaw(partialTicks), Direction.Axis.Y);
        rotated = VecHelper.rotate(rotated, getInitialYaw(), Direction.Axis.Y);
        return rotated;
    }

    @Override
    public Vec3 reverseRotation(Vec3 vector, float partialTicks) {
        Vec3 rotated = VecHelper.rotate(vector, -getInitialYaw(), Direction.Axis.Y);
        rotated = VecHelper.rotate(rotated, -getInterpolatedYaw(partialTicks), Direction.Axis.Y);
        Direction.Axis pitchAxis = getInitialOrientation().getAxis() == Direction.Axis.X
                ? Direction.Axis.Z : Direction.Axis.X;
        rotated = VecHelper.rotate(rotated, -getInterpolatedPitch(partialTicks), pitchAxis);
        return rotated;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void applyLocalTransforms(PoseStack matrixStack, float partialTicks) {
        float initialYaw = getInitialYaw();
        float viewYaw = -getInterpolatedYaw(partialTicks);
        float viewPitch = getInterpolatedPitch(partialTicks);

        matrixStack.translate(-.5f, 0, -.5f);

        matrixStack.translate(.5f, .5f, .5f);
        matrixStack.mulPose(Axis.YP.rotationDegrees(viewYaw + initialYaw));
        if (getInitialOrientation().getAxis() == Direction.Axis.X) {
            matrixStack.mulPose(Axis.ZP.rotationDegrees(viewPitch));
        } else {
            matrixStack.mulPose(Axis.XP.rotationDegrees(viewPitch));
        }
        matrixStack.translate(-.5f, -.5f, -.5f);
        matrixStack.translate(0, 1f, 0);
    }

    // --- Rotation state for collision/rendering (matches CBCContraptionRotationState) ---

    /**
     * Inner rotation state that overrides asMatrix() directly, exactly like CBC's
     * CBCContraptionRotationState. This avoids the double-negation and composition-order
     * issues that arise from trying to map CBC's 2-rotation matrix onto Create's
     * base xRotation/yRotation/zRotation fields.
     *
     * The parent's 'matrix' field is package-private (accessible to CBC in the same package
     * but not to us). We work around this by storing our computed matrix in a side field
     * and always building it fresh on asMatrix() calls (safe because getRotationState()
     * creates a new instance each time).
     */
    private class DecorationRotationState extends AbstractContraptionEntity.ContraptionRotationState {
        private final float entityYaw;
        private final float entityPitch;
        private final float stateYaw;
        private final float stateYawOffset;
        private final boolean isXAxis;
        private Matrix3d cachedMatrix;

        DecorationRotationState() {
            this.entityYaw = yaw;
            this.entityPitch = pitch;
            this.isXAxis = getInitialOrientation().getAxis() == Direction.Axis.X;
            float initialYaw = getInitialYaw();

            if (entityPitch != 0 && entityYaw != 0) {
                this.stateYawOffset = entityYaw;
                this.stateYaw = -initialYaw;
            } else {
                this.stateYawOffset = 0;
                this.stateYaw = entityYaw + initialYaw;
            }
        }

        @Override
        public Matrix3d asMatrix() {
            if (cachedMatrix != null) return cachedMatrix;

            cachedMatrix = new Matrix3d().asIdentity();

            if (entityPitch != 0) {
                if (isXAxis) {
                    cachedMatrix.multiply(new Matrix3d().asZRotation(AngleHelper.rad(-entityPitch)));
                } else {
                    cachedMatrix.multiply(new Matrix3d().asXRotation(AngleHelper.rad(-entityPitch)));
                }
            }

            float yawAdjust = isXAxis && !hasVerticalRotation() ? stateYaw + 180 : stateYaw;
            cachedMatrix.multiply(new Matrix3d().asYRotation(AngleHelper.rad(yawAdjust)));
            return cachedMatrix;
        }

        @Override
        public boolean hasVerticalRotation() {
            return entityPitch != 0;
        }

        @Override
        public float getYawOffset() {
            return -stateYawOffset;
        }
    }

    @Override
    public AbstractContraptionEntity.ContraptionRotationState getRotationState() {
        return new DecorationRotationState();
    }

    // --- Disable Create's orientation updater (matches CBC) ---

    @Override
    protected boolean updateOrientation(boolean checkAngleLimit, boolean checkCollision,
                                         net.minecraft.world.entity.Entity vehicle, boolean limitVanillaOrientation) {
        return false;
    }

    // --- Tick: sync anchor, snapshot prev angles (matches CBC) ---

    @Override
    protected void tickContraption() {
        super.tickContraption();

        prevYaw = yaw;
        prevPitch = pitch;

        contraption.anchor = blockPosition();

        // Re-attach to controller if needed (matches CBC pattern)
        if (controllerPos != null && level() != null) {
            if (!level().isClientSide) {
                var be = level().getBlockEntity(controllerPos);
                if (be instanceof DecorationBearingBlockEntity bearing) {
                    if (!bearing.isAttachedTo(this)) {
                        bearing.attach(this);
                    }
                }
            }
        }
    }

    // --- Stalled angle ---

    @Override
    protected float getStalledAngle() {
        return yaw;
    }

    @Override
    protected void handleStallInformation(double x, double y, double z, float angle) {
        this.yaw = angle;
        this.prevYaw = angle;
    }

    // --- NBT persistence (matches CBC's ControllerRelative pattern) ---

    @Override
    protected void writeAdditional(CompoundTag tag, boolean clientPacket) {
        super.writeAdditional(tag, clientPacket);
        if (controllerPos != null) {
            tag.put("ControllerRelative", net.minecraft.nbt.NbtUtils.writeBlockPos(
                    controllerPos.subtract(blockPosition())));
        }
    }

    @Override
    protected void readAdditional(CompoundTag tag, boolean clientPacket) {
        super.readAdditional(tag, clientPacket);
        if (tag.contains("ControllerRelative")) {
            controllerPos = net.minecraft.nbt.NbtUtils.readBlockPos(
                    tag.getCompound("ControllerRelative")).offset(blockPosition());
        }
    }

    public BlockPos getControllerPos() {
        return controllerPos;
    }
}
