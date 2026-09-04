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
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
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
 * Pose model (all vectors in the same coordinate space as the CBC entity's
 * anchor, i.e. ship space when mounted on a VS ship):
 *
 * <pre>
 * pivotLocal = CBC anchor at assembly - render origin at assembly
 * entityPos(t) = CBC anchor(t) - pivotLocal
 * localPoint(t) = R(t) . (localPoint - pivotLocal) + pivotLocal
 * </pre>
 *
 * The entity position is therefore the *rotated-out assembly render origin*,
 * never the CBC anchor itself, so the contraption's local block coordinates
 * (relative to {@code contraption.anchor}) stay valid for rendering, actors,
 * collision and disassembly.
 * <p>
 * The server is authoritative for the pose; yaw/pitch reach the client through
 * vanilla synced entity data, so the client never needs to resolve the CBC
 * entity.
 */
public class DecorationBearingContraptionEntity extends OrientedContraptionEntity {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final EntityDataAccessor<Float> SYNCED_YAW =
            SynchedEntityData.defineId(DecorationBearingContraptionEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SYNCED_PITCH =
            SynchedEntityData.defineId(DecorationBearingContraptionEntity.class, EntityDataSerializers.FLOAT);

    private BlockPos controllerPos;
    private int linkedCbcEntityId = -1;

    /**
     * CBC pivot expressed in the assembly render frame (render origin at
     * assembly = Vec3.atBottomCenterOf(contraption.anchor)).
     */
    private Vec3 pivotLocal = Vec3.ZERO;
    /** True once pivotLocal has been captured from the live CBC anchor. */
    private boolean pivotCaptured = false;
    /** Consecutive server ticks without a resolvable CBC pose (diagnostics). */
    private int unresolvedTicks = 0;

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

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(SYNCED_YAW, 0.0f);
        this.entityData.define(SYNCED_PITCH, 0.0f);
    }

    // -----------------------------------------------------------------------
    // Canonical transform: one source of truth for all transform consumers
    // -----------------------------------------------------------------------

    /**
     * Apply the canonical decoration rotation to a local-space vector.
     * Matches CBC's rotation chain:
     *   1. Pitch around Z (X-axis initial) or X (other axes)
     *   2. View yaw + initial yaw around Y
     * Rotation happens around pivotLocal; translation is carried by the
     * entity position (see class doc).
     */
    @Override
    public Vec3 applyRotation(Vec3 vector, float partialTicks) {
        return rotateAroundPivot(vector, partialTicks, false);
    }

    private Vec3 rotateAroundPivot(Vec3 vector, float partialTicks, boolean reverse) {
        Vec3 centered = vector.subtract(pivotLocal);
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
        return rotated.add(pivotLocal);
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

        // Translate to render origin (block center convention for
        // OrientedContraptionEntity; entity pos = atBottomCenterOf(contraption.anchor)).
        // The CBC POCE renderer (PitchOrientedContraptionEntityMixin) applies:
        //   translate(-0.5, 0, -0.5) -> centre() -> R -> unCentre()
        // where centre() = translate(0.5, 0.5, 0.5) rotates around the block center.
        // Our pivot-aware variant replaces centre()/unCentre() with a
        // translate around pivotLocal + (0.5, 0.5, 0.5) so that the rotation
        // argument matches toGlobalVector's convention:
        //   R * (v - (0.5, 0.5, 0.5) - pivotLocal)
        matrixStack.translate(-.5f, 0, -.5f);
        matrixStack.translate(pivotLocal.x + 0.5, pivotLocal.y + 0.5, pivotLocal.z + 0.5);
        matrixStack.mulPose(Axis.YP.rotationDegrees(interpYaw + initialYaw));
        if (getInitialOrientation().getAxis() == Direction.Axis.X) {
            matrixStack.mulPose(Axis.ZP.rotationDegrees(interpPitch));
        } else {
            matrixStack.mulPose(Axis.XP.rotationDegrees(interpPitch));
        }
        matrixStack.translate(-pivotLocal.x - 0.5, -pivotLocal.y - 0.5, -pivotLocal.z - 0.5);
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

    // -----------------------------------------------------------------------
    // Disassembly
    // -----------------------------------------------------------------------

    @Override
    protected StructureTransform makeStructureTransform() {
        // Blocks must return to the world positions they currently occupy in
        // render/collision space. Desired world center of local block b:
        //   E + R(b + c - pivotLocal) + pivotLocal - h      (c = (.5,.5,.5), h = (.5,0,.5))
        // StructureTransform places block b at:
        //   offset + R90(b) + c
        // with R90 the yaw-only rotation snapped to a multiple of 90°. Solve
        // offset from the two expressions (pitch cannot be expressed in a
        // StructureTransform, so — like every Create bearing — disassembly is
        // yaw-only).
        float angle = yaw + getInitialYaw();
        float snapped = (float) (Math.round(angle / 90.0) * 90);

        Vec3 c = new Vec3(0.5, 0.5, 0.5);
        Vec3 h = new Vec3(0.5, 0.0, 0.5);
        Vec3 entityPos = position();
        Vec3 rotatedC = VecHelper.rotate(c.subtract(pivotLocal), snapped, Direction.Axis.Y);
        Vec3 offset = entityPos.add(pivotLocal).add(rotatedC).subtract(h).subtract(c);

        return new StructureTransform(BlockPos.containing(offset.x, offset.y, offset.z), 0, snapped, 0);
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
    // Tick: server copies CBC pose; client reads synced entity data
    // -----------------------------------------------------------------------

    @Override
    protected void tickContraption() {
        // Snapshot previous values before copying new state
        prevYaw = yaw;
        prevPitch = pitch;

        if (level().isClientSide) {
            yaw = this.entityData.get(SYNCED_YAW);
            pitch = this.entityData.get(SYNCED_PITCH);
        } else {
            Object cbcEntity = resolveLinkedCbcEntity();
            if (cbcEntity == null) {
                unresolvedTicks++;
                if (unresolvedTicks % 60 == 1) {
                    LOGGER.info("[VSAW_DBC] tick: no CBC entity (controllerPos={}, linkedId={}, failures={})",
                            controllerPos, linkedCbcEntityId, unresolvedTicks);
                } else {
                    LOGGER.debug("[VSAW_DBC] tick: no CBC entity (controllerPos={}, linkedId={}, failures={})",
                            controllerPos, linkedCbcEntityId, unresolvedTicks);
                }
            } else {
                CbcCompat.CbcPoseData pose = CbcCompat.readCbcPoseData(cbcEntity);
                if (pose == null) {
                    unresolvedTicks++;
                    LOGGER.debug("[VSAW_DBC] tick: readCbcPoseData returned null for {}",
                            cbcEntity.getClass().getSimpleName());
                } else {
                    unresolvedTicks = 0;
                    // CBC's getViewYRot(1.0f) returns the raw yaw field; Create's
                    // rendering convention negates it (m_5675_ = -yaw). Our
                    // applyRotation()/applyLocalTransforms() use the raw field,
                    // so negate here to adopt CBC's orientation as ours.
                    yaw = -pose.viewYaw();
                    pitch = pose.viewPitch();
                    linkedCbcEntityId = pose.entityId();

                    // If assembly could not use the live CBC anchor (assembled
                    // before the cannon entity resolved), re-capture the pivot
                    // now: the decoration stays put, the rotation center snaps
                    // to the exact CBC anchor.
                    if (!pivotCaptured) {
                        pivotLocal = pose.anchorVec().subtract(position());
                        pivotCaptured = true;
                        LOGGER.info("[VSAW_DBC] tick: pivot re-captured from live CBC anchor={} pivotLocal={}",
                                pose.anchorVec(), pivotLocal);
                    }

                    // Entity position = rotated-out assembly render origin.
                    // pivotLocal is the CBC pivot in the assembly frame, so
                    // anchoring the pivot to the CBC's live anchor positions
                    // the whole contraption correctly under any rotation.
                    setPos(pose.anchorVec().subtract(pivotLocal));
                    // Keep Create/VS actor positioning tracking the entity,
                    // matching CBC's own behavior.
                    if (contraption != null) {
                        contraption.anchor = blockPosition();
                    }

                    this.entityData.set(SYNCED_YAW, yaw);
                    this.entityData.set(SYNCED_PITCH, pitch);
                    LOGGER.debug("[VSAW_DBC] tick: yaw={} pitch={} anchor={} id={}",
                            yaw, pitch, pose.anchorVec(), linkedCbcEntityId);
                }
            }
        }

        // Create's OrientedContraptionEntity.tickContraption() early-returns
        // when the entity has no vehicle (this one never has one), which would
        // skip actor ticking entirely — tick actors explicitly instead.
        super.tickContraption();
        if (getVehicle() == null) {
            tickActors();
        }

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
            return null;
        }
        var be = level().getBlockEntity(controllerPos);
        if (!(be instanceof DecorationBearingBlockEntity bearing)) {
            LOGGER.debug("[VSAW_DBC] resolveLinkedCbcEntity: controller BE at {} is {}",
                    controllerPos, be != null ? be.getClass().getSimpleName() : "null");
            return null;
        }
        Object byId = CbcCompat.resolveLiveCbcEntityById(level(), linkedCbcEntityId);
        if (byId != null) {
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
    // Stalled angle — this entity is never driven by Create's stall packet
    // path; a stall packet must not freeze the copied pose.
    // -----------------------------------------------------------------------

    @Override
    protected float getStalledAngle() {
        return yaw;
    }

    @Override
    protected void handleStallInformation(double x, double y, double z, float angle) {
        // No-op: pose authority is the linked CBC entity, not stall packets.
    }

    // -----------------------------------------------------------------------
    // NBT persistence
    // -----------------------------------------------------------------------

    @Override
    protected void writeAdditional(CompoundTag tag, boolean clientPacket) {
        super.writeAdditional(tag, clientPacket);
        // controllerPos is absolute; the entity position moves with the CBC.
        if (controllerPos != null) {
            tag.put("ControllerAbsolute", NbtUtils.writeBlockPos(controllerPos));
        }
        tag.putFloat("PivotLocalX", (float) pivotLocal.x);
        tag.putFloat("PivotLocalY", (float) pivotLocal.y);
        tag.putFloat("PivotLocalZ", (float) pivotLocal.z);
        tag.putBoolean("PivotCaptured", pivotCaptured);
        tag.putFloat("SavedYaw", yaw);
        tag.putFloat("SavedPitch", pitch);
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
        if (tag.contains("PivotLocalX")) {
            pivotLocal = new Vec3(
                    tag.getFloat("PivotLocalX"),
                    tag.getFloat("PivotLocalY"),
                    tag.getFloat("PivotLocalZ"));
        } else if (tag.contains("PivotOffsetX")) {
            // Legacy key from the approximate block-space pivot
            pivotLocal = new Vec3(
                    tag.getFloat("PivotOffsetX"),
                    tag.getFloat("PivotOffsetY"),
                    tag.getFloat("PivotOffsetZ"));
        }
        pivotCaptured = tag.getBoolean("PivotCaptured");
        if (!clientPacket) {
            // Spawn packets come before the first pose tick; restoring yaw
            // here would fight the synced entity data on the client.
            yaw = tag.getFloat("SavedYaw");
            pitch = tag.getFloat("SavedPitch");
            prevYaw = yaw;
            prevPitch = pitch;
            this.entityData.set(SYNCED_YAW, yaw);
            this.entityData.set(SYNCED_PITCH, pitch);
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
     * Used until the first live CBC pose tick; must already use the internal
     * yaw convention (negated CBC view yaw).
     */
    public void setDecorationRotation(float newYaw, float newPitch) {
        this.prevYaw = newYaw;
        this.yaw = newYaw;
        this.prevPitch = newPitch;
        this.pitch = newPitch;
        this.entityData.set(SYNCED_YAW, newYaw);
        this.entityData.set(SYNCED_PITCH, newPitch);
    }

    /**
     * Capture the CBC pivot in the assembly render frame from the live CBC
     * anchor. Must be called before the entity is spawned.
     */
    public void capturePivot(Vec3 cbcAnchorAtAssembly, Vec3 renderOriginAtAssembly) {
        capturePivot(cbcAnchorAtAssembly, renderOriginAtAssembly, true);
    }

    /**
     * Capture the CBC pivot in the assembly render frame. When
     * {@code fromLiveCbc} is false (approximate anchor), the pivot is
     * re-captured from the live CBC anchor on the first successful pose tick.
     */
    public void capturePivot(Vec3 cbcAnchorAtAssembly, Vec3 renderOriginAtAssembly, boolean fromLiveCbc) {
        this.pivotLocal = cbcAnchorAtAssembly.subtract(renderOriginAtAssembly);
        this.pivotCaptured = fromLiveCbc;
    }

    public Vec3 getPivotLocal() {
        return pivotLocal;
    }

    public boolean isPivotCaptured() {
        return pivotCaptured;
    }

    public int getLinkedCbcEntityId() {
        return linkedCbcEntityId;
    }
}
