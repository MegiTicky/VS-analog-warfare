package com.erika.vsanalogwarfare.decorationbearing;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.OrientedContraptionEntity;
import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.foundation.collision.Matrix3d;
import com.simibubi.create.foundation.utility.AngleHelper;
import com.simibubi.create.foundation.utility.VecHelper;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.erika.vsanalogwarfare.scope.compat.CbcCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import com.mojang.logging.LogUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.slf4j.Logger;

import javax.annotation.Nullable;

/**
 * Full Create contraption that follows a linked CBC cannon's pose.
 * <p>
 * The BearingContraption coordinate frame is never mutated. Rotation is applied
 * around a local pivot offset from the original assembly anchor. The canonical
 * transform is shared by rendering, collision, ray-trace, and disassembly.
 */
public class DecorationBearingContraptionEntity extends OrientedContraptionEntity {
    private static final Logger LOGGER = LogUtils.getLogger();
    private BlockPos controllerPos;
    private int linkedCbcEntityId = -1;
    private int prevLinkedCbcEntityId = -1;

    // Local pivot offset from the original assembly anchor (in contraption-local coords).
    // The DBC rotates around assemblyAnchor + pivotOffset, NOT around assemblyAnchor.
    private Vec3 pivotOffset = Vec3.ZERO;

    // Current and previous CBC pose snapshots for interpolation.
    private Vec3 currentCbcAnchor = null;
    private Vec3 previousCbcAnchor = null;

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

    // -----------------------------------------------------------------------
    // Canonical transform: one source of truth for all transform consumers
    // -----------------------------------------------------------------------

    /**
     * Apply the canonical decoration rotation to a local-space vector.
     * Matches CBC's rotation chain:
     *   1. Pitch around Z (X-axis initial) or X (other axes)
     *   2. View yaw around Y
     *   3. Initial yaw around Y
     */
    @Override
    public Vec3 applyRotation(Vec3 vector, float partialTicks) {
        return rotateAroundPivot(vector, partialTicks, false);
    }

    private Vec3 rotateAroundPivot(Vec3 vector, float partialTicks, boolean reverse) {
        Vec3 centered = vector.subtract(pivotOffset);
        Direction.Axis pitchAxis = getInitialOrientation().getAxis() == Direction.Axis.X
                ? Direction.Axis.Z : Direction.Axis.X;
        Vec3 rotated;
        if (reverse) {
            // Inverse of (Ry * Rp) is (Rp^-1 * Ry^-1): undo yaw first, then pitch
            rotated = VecHelper.rotate(centered, -(getInterpolatedYaw(partialTicks) + getInitialYaw()), Direction.Axis.Y);
            rotated = VecHelper.rotate(rotated, -getInterpolatedPitch(partialTicks), pitchAxis);
        } else {
            // Forward: pitch first, then yaw+initialYaw
            rotated = VecHelper.rotate(centered, getInterpolatedPitch(partialTicks), pitchAxis);
            rotated = VecHelper.rotate(rotated, getInterpolatedYaw(partialTicks) + getInitialYaw(), Direction.Axis.Y);
        }
        return rotated.add(pivotOffset);
    }

    @Override
    public Vec3 reverseRotation(Vec3 vector, float partialTicks) {
        return rotateAroundPivot(vector, partialTicks, true);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void applyLocalTransforms(PoseStack matrixStack, float partialTicks) {
        float initialYaw = getInitialYaw();
        float interpYaw = getInterpolatedYaw(partialTicks);
        float interpPitch = getInterpolatedPitch(partialTicks);

        // Translate to render origin (block center convention for OrientedContraptionEntity)
        matrixStack.translate(-.5f, 0, -.5f);

        // Apply pivot-aware rotation matching applyRotation():
        //   vertex' = R(vertex + pivotOffset) where R = Ryaw+initial * Rpitch
        // PoseStack applies right-to-left to vertices, so:
        //   translate(pivotOffset) * Ryaw * Rpitch * translate(-pivotOffset)
        // But renderer already centers at block origin, so pivot is in block-local coords.

        // Move pivot to origin
        matrixStack.translate(pivotOffset.x, pivotOffset.y, pivotOffset.z);

        // Apply yaw+initialYaw (Y axis)
        matrixStack.mulPose(Axis.YP.rotationDegrees(interpYaw + initialYaw));

        // Apply pitch (Z axis for X-axis orientation, X axis otherwise)
        if (getInitialOrientation().getAxis() == Direction.Axis.X) {
            matrixStack.mulPose(Axis.ZP.rotationDegrees(interpPitch));
        } else {
            matrixStack.mulPose(Axis.XP.rotationDegrees(interpPitch));
        }

        // Move pivot back
        matrixStack.translate(-pivotOffset.x, -pivotOffset.y, -pivotOffset.z);

        // Vertical offset to match OrientedContraptionEntity render convention
        matrixStack.translate(0, 1f, 0);
    }

    // -----------------------------------------------------------------------
    // Rotation state for collision/rendering
    // -----------------------------------------------------------------------

    /**
     * Custom rotation state that produces the correct matrix for VS collision
     * and Create render compatibility. Continuous — no branch on zero angles.
     */
    private class DecorationRotationState extends AbstractContraptionEntity.ContraptionRotationState {
        private final float entityYaw;
        private final float entityPitch;
        private final float stateYaw;
        private final boolean isXAxis;
        private Matrix3d cachedMatrix;

        DecorationRotationState() {
            this.entityYaw = yaw;
            this.entityPitch = pitch;
            this.isXAxis = getInitialOrientation().getAxis() == Direction.Axis.X;
            this.stateYaw = entityYaw + getInitialYaw();
        }

        @Override
        public Matrix3d asMatrix() {
            if (cachedMatrix != null) return cachedMatrix;
            cachedMatrix = new Matrix3d().asIdentity();

            // Match applyRotation order: pitch first, then yaw+initialYaw
            if (entityPitch != 0) {
                if (isXAxis) {
                    cachedMatrix.multiply(new Matrix3d().asZRotation(AngleHelper.rad(entityPitch)));
                } else {
                    cachedMatrix.multiply(new Matrix3d().asXRotation(AngleHelper.rad(entityPitch)));
                }
            }

            cachedMatrix.multiply(new Matrix3d().asYRotation(AngleHelper.rad(stateYaw)));
            return cachedMatrix;
        }

        @Override
        public boolean hasVerticalRotation() {
            return entityPitch != 0;
        }

        @Override
        public float getYawOffset() {
            return 0;
        }
    }

    @Override
    public AbstractContraptionEntity.ContraptionRotationState getRotationState() {
        return new DecorationRotationState();
    }

    @Override
    protected StructureTransform makeStructureTransform() {
        // During disassembly, blocks (stored in assembly frame) must be placed back
        // at the correct world positions. The StructureTransform applies:
        //   worldPos = rotateCentered(localPos) + offset
        //
        // The pivot is the cannon trunnion in assembly-local coords. We need the
        // offset to be the pivot's world position at disassembly time, accounting
        // for the fact that rotateCentered rotates around the block center of the offset.
        //
        // Since the DBC entity position follows the CBC anchor, and blocks were
        // assembled relative to controllerPos, use controllerPos as the base.
        // The pivot offset from assembly origin determines the disassembly anchor.
        BlockPos basePos = controllerPos != null ? controllerPos : blockPosition();
        BlockPos offset = BlockPos.containing(
                basePos.getX() + pivotOffset.x,
                basePos.getY() + pivotOffset.y,
                basePos.getZ() + pivotOffset.z);
        return new StructureTransform(offset, 0, -yaw + getInitialYaw(), 0);
    }

    // -----------------------------------------------------------------------
    // Disable Create's orientation updater (matches CBC)
    // -----------------------------------------------------------------------

    @Override
    protected boolean updateOrientation(boolean checkAngleLimit, boolean checkCollision,
                                         net.minecraft.world.entity.Entity vehicle, boolean limitVanillaOrientation) {
        return false;
    }

    // -----------------------------------------------------------------------
    // Tick: sync CBC state BEFORE super processes transforms
    // -----------------------------------------------------------------------

    @Override
    protected void tickContraption() {
        // Snapshot previous values before copying new CBC state
        prevYaw = yaw;
        prevPitch = pitch;
        previousCbcAnchor = currentCbcAnchor;
        prevLinkedCbcEntityId = linkedCbcEntityId;

        // Synchronize from linked CBC entity
        if (level() != null) {
            Object cbcEntity = resolveLinkedCbcEntity();
            if (cbcEntity == null) {
                LOGGER.debug("[VSAW_DBC] tick: no CBC entity (controllerPos={}, linkedId={}, client={})",
                        controllerPos, linkedCbcEntityId, level().isClientSide);
            } else {
                CbcCompat.CbcPoseData pose = CbcCompat.readCbcPoseData(cbcEntity);
                if (pose == null) {
                    LOGGER.debug("[VSAW_DBC] tick: readCbcPoseData returned null for {}",
                            cbcEntity.getClass().getSimpleName());
                } else {
                    // CBC's m_5675_() (getViewYRot) negates the yaw for rendering.
                    // At partialTicks=1.0 it short-circuits to the raw yaw field.
                    // Our applyRotation() uses the raw field directly (no negation),
                    // so we negate here to match CBC's rotation convention.
                    yaw = -pose.viewYaw();
                    pitch = pose.viewPitch();
                    previousCbcAnchor = pose.prevAnchorVec();
                    currentCbcAnchor = pose.anchorVec();
                    linkedCbcEntityId = pose.entityId();
                    setPos(currentCbcAnchor);
                    LOGGER.debug("[VSAW_DBC] tick: yaw={} pitch={} anchor={} id={} client={}",
                            yaw, pitch, currentCbcAnchor, linkedCbcEntityId, level().isClientSide);
                }
            }
        }

        // Do NOT update contraption.anchor — it must remain at the original assembly
        // position for correct block coordinate mapping during disassembly.
        // The entity position (getAnchorVec) follows the CBC; the contraption block
        // map and anchor stay in the original assembly frame.

        super.tickContraption();

        // Re-attach to controller if needed
        if (controllerPos != null && level() != null && !level().isClientSide) {
            var be = level().getBlockEntity(controllerPos);
            if (be instanceof DecorationBearingBlockEntity bearing) {
                if (!bearing.isAttachedTo(this)) {
                    bearing.attach(this);
                }
            }
        }
    }

    @Nullable
    private Object resolveLinkedCbcEntity() {
        if (controllerPos == null || level() == null) {
            LOGGER.debug("[VSAW_DBC] resolveLinkedCbcEntity: controllerPos={} level={}", controllerPos, level() != null);
            return null;
        }
        var be = level().getBlockEntity(controllerPos);
        if (!(be instanceof DecorationBearingBlockEntity bearing)) {
            LOGGER.debug("[VSAW_DBC] resolveLinkedCbcEntity: controller BE at {} is {}", controllerPos, be != null ? be.getClass().getSimpleName() : "null");
            return null;
        }
        Object byId = CbcCompat.resolveLiveCbcEntityById(level(), linkedCbcEntityId);
        if (byId != null) {
            LOGGER.debug("[VSAW_DBC] resolveLinkedCbcEntity: found CBC by entity ID {}", linkedCbcEntityId);
            return byId;
        }
        BlockPos mount = bearing.getLinkedMountPos();
        if (mount == null) {
            LOGGER.debug("[VSAW_DBC] resolveLinkedCbcEntity: linkedMount is null on bearing at {}", controllerPos);
            return null;
        }
        Object resolved = CbcCompat.resolveLiveCbcEntity(level(), mount);
        if (resolved == null) {
            LOGGER.debug("[VSAW_DBC] resolveLinkedCbcEntity: resolveLiveCbcEntity returned null for mount={}", mount);
        }
        return resolved;
    }

    // -----------------------------------------------------------------------
    // Interpolation
    // -----------------------------------------------------------------------

    private float getInterpolatedYaw(float partialTicks) {
        if (partialTicks >= 1.0f) return yaw;
        return AngleHelper.angleLerp(partialTicks, prevYaw, yaw);
    }

    private float getInterpolatedPitch(float partialTicks) {
        if (partialTicks >= 1.0f) return pitch;
        return AngleHelper.angleLerp(partialTicks, prevPitch, pitch);
    }

    // -----------------------------------------------------------------------
    // Stalled angle
    // -----------------------------------------------------------------------

    @Override
    protected float getStalledAngle() {
        return yaw;
    }

    @Override
    protected void handleStallInformation(double x, double y, double z, float angle) {
        this.yaw = angle;
        this.prevYaw = angle;
    }

    // -----------------------------------------------------------------------
    // NBT persistence
    // -----------------------------------------------------------------------

    @Override
    protected void writeAdditional(CompoundTag tag, boolean clientPacket) {
        super.writeAdditional(tag, clientPacket);
        // Save controllerPos as absolute — the entity position moves to follow CBC,
        // so a relative offset would become stale.
        if (controllerPos != null) {
            tag.put("ControllerAbsolute", NbtUtils.writeBlockPos(controllerPos));
        }
        tag.putFloat("PivotOffsetX", (float) pivotOffset.x);
        tag.putFloat("PivotOffsetY", (float) pivotOffset.y);
        tag.putFloat("PivotOffsetZ", (float) pivotOffset.z);
        if (linkedCbcEntityId >= 0) {
            tag.putInt("LinkedCbcEntityId", linkedCbcEntityId);
        }
    }

    @Override
    protected void readAdditional(CompoundTag tag, boolean clientPacket) {
        super.readAdditional(tag, clientPacket);
        if (tag.contains("ControllerAbsolute")) {
            controllerPos = NbtUtils.readBlockPos(tag.getCompound("ControllerAbsolute"));
        } else if (tag.contains("ControllerRelative")) {
            // Legacy fallback
            controllerPos = NbtUtils.readBlockPos(
                    tag.getCompound("ControllerRelative")).offset(blockPosition());
        }
        if (tag.contains("PivotOffsetX")) {
            pivotOffset = new Vec3(
                    tag.getFloat("PivotOffsetX"),
                    tag.getFloat("PivotOffsetY"),
                    tag.getFloat("PivotOffsetZ"));
        }
        if (tag.contains("LinkedCbcEntityId")) {
            linkedCbcEntityId = tag.getInt("LinkedCbcEntityId");
        }
    }

    // -----------------------------------------------------------------------
    // Public accessors
    // -----------------------------------------------------------------------

    public BlockPos getControllerPos() {
        return controllerPos;
    }

    /**
     * Set initial rotation from the block entity during assembly.
     * This is NOT used at runtime — tickContraption() copies CBC state directly.
     */
    public void setDecorationRotation(float newYaw, float newPitch) {
        this.prevYaw = newYaw;
        this.yaw = newYaw;
        this.prevPitch = newPitch;
        this.pitch = newPitch;
    }

    public void setPivotOffset(Vec3 offset) {
        this.pivotOffset = offset;
    }

    public Vec3 getPivotOffset() {
        return pivotOffset;
    }

    public int getLinkedCbcEntityId() {
        return linkedCbcEntityId;
    }
}
