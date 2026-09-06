package com.erika.vsanalogwarfare.decorationbearing;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.OrientedContraptionEntity;
import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.content.contraptions.bearing.BearingContraption;
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
import org.joml.Vector3f;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.slf4j.Logger;

import javax.annotation.Nullable;

/**
 * Full Create contraption that follows a linked CBC cannon's pose.
 * <p>
 * Pose model (all vectors in Create/VS shipyard coordinate space):
 *
 * <pre>
 * pivotLocal = CBC entity pos at assembly - render origin at assembly
 * entityPos(t) = CBC entity pos(t) - pivotLocal
 * localPoint(t) = R(t) . (localPoint - pivotLocal) + pivotLocal
 * </pre>
 *
 * The invariant {@code entityPos + pivotLocal == CBC entity position} makes the
 * render rotation center land exactly on the CBC cannon's own visual pivot
 * ({@code cbcEntityPos + (0, 0.5, 0)}): CBC's renderer (PitchOrientedContraptionEntityMixin)
 * rotates around the model point {@code (0.5, 0.5, 0.5)} after a
 * {@code translate(-0.5, 0, -0.5)}, so its world pivot is
 * {@code cbcEntityPos + (0, 0.5, 0)} — and so is ours once the invariant holds.
 *
 * Yaw convention: our {@code yaw} field stores exactly what CBC's
 * {@code m_5675_(1.0f)} returns (the already-negated view yaw), so every
 * consumer adds {@code yaw + initialYaw} the same way CBC's transforms do.
 *
 * The entity position is therefore the *rotated-out assembly render origin*,
 * never the CBC anchor itself, so the contraption's local block coordinates
 * (relative to {@code contraption.anchor}) stay valid for rendering, actors,
 * collision and disassembly.
 * <p>
 * The server is authoritative for the pose; yaw/pitch reach the client through
 * vanilla synced entity data, with a direct CBC read used when available to
 * remove the sync tick of visual latency.
 */
public class DecorationBearingContraptionEntity extends OrientedContraptionEntity {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Identifies this build in [VSAW_DBC] logs. Guards against debugging a
     * stale jar: if this tag is absent from the assemble log, the deployed
     * jar predates the yaw/pivot fix.
     */
    public static final String BUILD_TAG = "dbc-facing-pivot";

    private static final EntityDataAccessor<Float> SYNCED_YAW =
            SynchedEntityData.defineId(DecorationBearingContraptionEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SYNCED_PITCH =
            SynchedEntityData.defineId(DecorationBearingContraptionEntity.class, EntityDataSerializers.FLOAT);
    /** Live CBC entity position, synced for the client-side pivot debug render. */
    private static final EntityDataAccessor<Vector3f> SYNCED_CBC_POS =
            SynchedEntityData.defineId(DecorationBearingContraptionEntity.class, EntityDataSerializers.VECTOR3);

    private BlockPos controllerPos;
    private int linkedCbcEntityId = -1;

    /**
     * CBC entity position expressed in the assembly render frame (render
     * origin at assembly = Vec3.atBottomCenterOf(contraption.anchor)). This
     * stays in Create/VS shipyard space, matching ordinary CBC contraptions.
     */
    private Vec3 pivotLocal = Vec3.ZERO;
    /**
     * The assembly render origin in Create/VS shipyard coordinates (sentinel
     * ZERO = unknown; fall back to the legacy position model).
     */
    private Vec3 renderOriginLocal = Vec3.ZERO;
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
        this.entityData.define(SYNCED_CBC_POS, new Vector3f());
    }

    /**
     * Preserve the copied pose across synced-data updates: Create's
     * {@code startAtInitialYaw()} runs on the client when INITIAL_ORIENTATION
     * syncs and would otherwise clobber the pose mid-spawn.
     */
    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        float prevYaw = this.yaw;
        float prevPitch = this.pitch;
        super.onSyncedDataUpdated(key);
        this.yaw = prevYaw;
        this.pitch = prevPitch;
    }

    /** Live CBC entity position as last synced by the server (0,0,0 if unknown). */
    public Vec3 getCbcEntityPosSynced() {
        Vector3f v = this.entityData.get(SYNCED_CBC_POS);
        if (v == null || (v.x == 0.0f && v.y == 0.0f && v.z == 0.0f)) {
            return Vec3.ZERO;
        }
        return new Vec3(v.x, v.y, v.z);
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
     * Rotation state for VS collision, mirroring CBC's
     * {@code CBCContraptionRotationState} exactly (translated to our yaw
     * convention: our {@code yaw} = −CBC's raw yaw field). Mirroring CBC keeps
     * the decoration's ship-space collision identical to the cannon's.
     */
    private class DecorationRotationState extends AbstractContraptionEntity.ContraptionRotationState {
        private final boolean isXAxis;
        private final boolean verticalBranch;
        private final float stateYaw;
        private final float yawOffsetValue;
        private final float pitchValue;
        private Matrix3d cachedMatrix;

        DecorationRotationState() {
            this.pitchValue = pitch;
            this.isXAxis = getInitialOrientation().getAxis() == Direction.Axis.X;
            this.verticalBranch = pitchValue != 0.0f && yaw != 0.0f;
            if (verticalBranch) {
                // CBC: yawOffset = entity.yaw (raw); state yaw = -entity.getYawOffset() (= -initialYaw)
                this.yawOffsetValue = -yaw;
                this.stateYaw = -getInitialYaw();
            } else {
                // CBC: state yaw = entity.yaw + initialYaw = -our yaw + initialYaw
                this.yawOffsetValue = 0.0f;
                this.stateYaw = -yaw + getInitialYaw();
            }
        }

        @Override
        public Matrix3d asMatrix() {
            if (cachedMatrix != null) return cachedMatrix;
            cachedMatrix = new Matrix3d().asIdentity();
            boolean hasVertical = hasVerticalRotation();
            if (hasVertical) {
                if (isXAxis) {
                    cachedMatrix.multiply(new Matrix3d().asZRotation(AngleHelper.rad(-pitchValue)));
                } else {
                    cachedMatrix.multiply(new Matrix3d().asXRotation(AngleHelper.rad(-pitchValue)));
                }
            }
            float yawAdjust = isXAxis && !hasVertical ? stateYaw + 180.0f : stateYaw;
            cachedMatrix.multiply(new Matrix3d().asYRotation(AngleHelper.rad(yawAdjust)));
            return cachedMatrix;
        }

        @Override
        public boolean hasVerticalRotation() {
            return pitchValue != 0.0f;
        }

        @Override
        public float getYawOffset() {
            return -yawOffsetValue;
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
        // render space. Rendered world center of local block b (block coords):
        //   W = E + Rs(b - pivotLocal) + pivotLocal + (0, 0.5, 0)
        // (E = entity pos; Rs = yaw-only rotation snapped to a multiple of 90°;
        // pitch cannot be expressed in a StructureTransform, so — like every
        // Create bearing — disassembly is yaw-only).
        // StructureTransform operates in the same Create/VS shipyard frame as
        // the contraption anchor. VS applies the ship transform separately.
        //   offset = renderOriginLocal - Rs(pivotLocal) + pivotLocal + (0, 0.5, 0) - c
        float angle = yaw + getInitialYaw();
        float snapped = (float) (Math.round(angle / 90.0) * 90);

        Vec3 rotatedPivot = VecHelper.rotate(pivotLocal, snapped, Direction.Axis.Y);
        Vec3 offsetLocal = renderOriginLocal != Vec3.ZERO ? renderOriginLocal : position();
        Vec3 offset = offsetLocal
                .subtract(rotatedPivot)
                .add(pivotLocal)
                .add(0.0, 0.5, 0.0)
                .subtract(0.5, 0.5, 0.5);
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
            // Read the live CBC entity directly on the client for zero-lag
            // pose, eliminating the 1-tick server sync delay.  Fall back to
            // synced entity data when the CBC entity is not yet available.
            Object cbcEntity = resolveLinkedCbcEntity();
            if (cbcEntity != null) {
                CbcCompat.CbcPoseData livePose = CbcCompat.readCbcPoseData(cbcEntity);
                if (livePose != null) {
                    applyRotationModeFilter(livePose.viewYaw(), livePose.viewPitch());
                    linkedCbcEntityId = livePose.entityId();
                } else {
                    yaw = this.entityData.get(SYNCED_YAW);
                    pitch = this.entityData.get(SYNCED_PITCH);
                }
            } else {
                yaw = this.entityData.get(SYNCED_YAW);
                pitch = this.entityData.get(SYNCED_PITCH);
            }
        } else {
            Object cbcEntity = resolveLinkedCbcEntity();
            if (cbcEntity == null) {
                unresolvedTicks++;
            } else {
                CbcCompat.CbcPoseData pose = CbcCompat.readCbcPoseData(cbcEntity);
                if (pose == null) {
                    unresolvedTicks++;
                    LOGGER.debug("[VSAW_DBC] tick: readCbcPoseData returned null for {}",
                            cbcEntity.getClass().getSimpleName());
                } else {
                    unresolvedTicks = 0;
                    // pose.viewYaw() is CBC's m_5675_(1.0f) — the already-negated
                    // view yaw. CBC's render and applyRotation add this value
                    // (+ initialYaw) directly; our transform consumers do the
                    // same, so store it unmodified.
                    applyRotationModeFilter(pose.viewYaw(), pose.viewPitch());
                    linkedCbcEntityId = pose.entityId();

                    // If assembly could not use the live CBC anchor (assembled
                    // before the cannon entity resolved), re-capture the pivot
                    // now: the decoration stays put, the rotation center snaps
                    // to the exact CBC pivot.
                    if (!pivotCaptured) {
                        Vec3 cbcPosLocal = pose.entityPos();
                        pivotLocal = renderOriginLocal != Vec3.ZERO
                                ? cbcPosLocal.subtract(renderOriginLocal)
                                : cbcPosLocal.subtract(position());
                        pivotCaptured = true;
                        LOGGER.info("[VSAW_DBC] tick: pivot re-captured from live CBC entityPos={} pivotLocal={}",
                                pose.entityPos(), pivotLocal);
                    }

                    // Keep the entity in Create/VS shipyard space. VS's generic
                    // AbstractContraptionEntity mixin owns the ship-to-world
                    // conversion for contraption entities, just as it does for
                    // CBC's own PitchOrientedContraptionEntity.
                    if (renderOriginLocal != Vec3.ZERO) {
                        setPos(renderOriginLocal);
                    } else {
                        // Legacy fallback (entity saved before this field existed)
                        setPos(pose.entityPos().subtract(pivotLocal));
                    }
                    // Keep Create/VS actor positioning tracking the entity,
                    // matching CBC's own behavior.
                    if (contraption != null) {
                        contraption.anchor = blockPosition();
                    }

                    this.entityData.set(SYNCED_YAW, yaw);
                    this.entityData.set(SYNCED_PITCH, pitch);
                    this.entityData.set(SYNCED_CBC_POS, new Vector3f(
                            (float) pose.entityPos().x, (float) pose.entityPos().y, (float) pose.entityPos().z));
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

    /**
     * Copy a CBC pose (yaw, pitch) into this entity's rotation fields,
     * honoring the controller bearing's rotation mode: the frozen axis simply
     * keeps its current field value, so the decoration holds whatever pose it
     * had when the mode was (or became) active — no snap on mode switches.
     * No-op for YAW_AND_PITCH. The controller's mode reaches the client
     * through the behaviour framework, so both the server branch and the
     * client's live-CBC read filter identically.
     */
    private void applyRotationModeFilter(float newYaw, float newPitch) {
        DecorationRotationMode mode = resolveRotationMode();
        if (mode == DecorationRotationMode.PITCH_ONLY) {
            pitch = newPitch;
        } else if (mode == DecorationRotationMode.YAW_ONLY) {
            yaw = newYaw;
        } else {
            yaw = newYaw;
            pitch = newPitch;
        }
    }

    private DecorationRotationMode resolveRotationMode() {
        if (controllerPos != null && level() != null
                && level().getBlockEntity(controllerPos) instanceof DecorationBearingBlockEntity bearing) {
            return bearing.getRotationMode();
        }
        return DecorationRotationMode.YAW_AND_PITCH;
    }

    @Nullable
    private Object resolveLinkedCbcEntity() {        if (controllerPos == null || level() == null) {
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
        if (mount != null) {
            Object resolved = CbcCompat.resolveLiveCbcEntity(level(), mount);
            if (resolved != null) {
                return resolved;
            }
        } else if (unresolvedTicks % 40 == 0 && repairStaleMountLink(bearing)) {
            mount = bearing.getLinkedMountPos();
            if (mount != null) {
                Object resolved = CbcCompat.resolveLiveCbcEntity(level(), mount);
                if (resolved != null) {
                    return resolved;
                }
            }
        }
        LOGGER.debug("[VSAW_DBC] resolveLinkedCbcEntity: no live CBC entity (bearing at {}, mount={})",
                controllerPos, mount);
        return null;
    }

    /**
     * Recover a mount link that a VMod schematic paste left pointing at the
     * ORIGINAL ship: the pasted bearing's {@link ScopeCannonLink} still holds
     * the old ship id / fallback position, so it never resolves on the pasted
     * ship. Find the nearest CBC cannon mount to the bearing's current
     * position and re-link to it.
     */
    private boolean repairStaleMountLink(DecorationBearingBlockEntity bearing) {
        BlockPos found = CbcCompat.findNearestMount(level(), bearing.getBlockPosition(), 16).orElse(null);
        if (found == null) {
            return false;
        }
        LOGGER.info("[VSAW_DBC] stale mount link on bearing at {} — re-linking to nearest CBC mount at {}",
                bearing.getBlockPosition(), found);
        bearing.relinkMount(found);
        return true;
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
        tag.putFloat("RenderOriginX", (float) renderOriginLocal.x);
        tag.putFloat("RenderOriginY", (float) renderOriginLocal.y);
        tag.putFloat("RenderOriginZ", (float) renderOriginLocal.z);
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
        if (tag.contains("RenderOriginX")) {
            renderOriginLocal = new Vec3(
                    tag.getFloat("RenderOriginX"),
                    tag.getFloat("RenderOriginY"),
                    tag.getFloat("RenderOriginZ"));
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
        if (!clientPacket && level() != null && !level().isClientSide) {
            repairStaleNbt();
        }
    }

    /**
     * Repair NBT that a VMod ship-schematic save/paste left stale. VMod
     * captures every {@code AbstractContraptionEntity} and remaps only the
     * vanilla {@code Pos} and Create's {@code Contraption.Anchor} /
     * {@code ControllerRelative} to the paste location; our absolute fields
     * ({@code ControllerAbsolute}, {@code RenderOrigin*}, CBC entity id)
     * still point at the ORIGINAL ship. On the pasted ship they drag the
     * entity back to the old assembly position and attach it to the old
     * bearing.
     * <p>
     * Detection: on a normal chunk reload {@code position()} equals
     * {@code renderOriginLocal} exactly (we {@code setPos(renderOriginLocal)}
     * every pose tick and before save). A mismatch therefore means the
     * entity was re-created at a remapped {@code Pos} — i.e. schematic
     * paste — while the stored render origin was not remapped.
     */
    private void repairStaleNbt() {
        if (renderOriginLocal == Vec3.ZERO) {
            return; // legacy entity: tick falls back to position()-relative math
        }
        if (renderOriginLocal.distanceToSqr(position()) <= 1.0e-4) {
            return; // consistent — normal save/load
        }
        Vec3 oldRenderOrigin = renderOriginLocal;
        BlockPos oldController = controllerPos;

        // The remapped contraption anchor is the authoritative render-origin
        // frame: VMod remaps Pos and Contraption.Anchor with slightly
        // different rounding (toInt truncation on negative coords), so prefer
        // atBottomCenterOf(anchor) over position().
        if (contraption != null) {
            renderOriginLocal = Vec3.atBottomCenterOf(contraption.anchor);
        } else {
            renderOriginLocal = position();
        }
        pivotCaptured = false; // re-capture from the live CBC on the next pose tick
        linkedCbcEntityId = -1; // entity ids do not survive schematic paste
        controllerPos = deriveControllerPosFromContraption();

        LOGGER.info("[VSAW_DBC] repaired stale schematic NBT: renderOrigin {} -> {}, controllerPos {} -> {} (anchor={}, facing={})",
                oldRenderOrigin, renderOriginLocal, oldController, controllerPos,
                contraption != null ? contraption.anchor : null,
                contraption instanceof BearingContraption bearing ? bearing.getFacing() : null);
    }

    /**
     * Locate the assembly bearing in shipyard coordinates from the DBC's own
     * (remapped) contraption. Create's BearingContraption anchors at
     * {@code bearing.relative(facing)}, so the bearing is exactly one step
     * against the contraption's stored facing — that facing is persisted in
     * the contraption NBT and remapped coherently with the blocks by VMod's
     * schematic paste. The contraption's block map must NOT be scanned: the
     * assembly bearing itself is never part of it (Create keeps that block in
     * the world), but decorative bearing blocks stored on the decoration can
     * be, and matching one of those yields the wrong controller.
     */
    private BlockPos deriveControllerPosFromContraption() {
        if (contraption == null) {
            return controllerPos;
        }
        BlockPos anchor = contraption.anchor;
        if (contraption instanceof BearingContraption bearing) {
            return anchor.relative(bearing.getFacing().getOpposite());
        }
        Direction initial = getInitialOrientation();
        if (initial != null) {
            return anchor.relative(initial.getOpposite());
        }
        return controllerPos;
    }

    // -----------------------------------------------------------------------
    // Public accessors
    // -----------------------------------------------------------------------

    public BlockPos getControllerPos() {
        return controllerPos;
    }

    /**
     * Set initial rotation from the block entity during assembly.
     * Used until the first live CBC pose tick; must use the internal yaw
     * convention (CBC's m_5675_ view yaw, already negated).
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
     * Capture the CBC entity position in the assembly render frame from the
     * live CBC entity. CBC and Create already use the same Create/VS shipyard
     * frame, so no world-to-ship conversion is needed.
     * Must be called before the entity is spawned.
     */
    public void capturePivot(Vec3 cbcEntityPosWorld, Vec3 renderOriginAtAssembly) {
        capturePivot(cbcEntityPosWorld, renderOriginAtAssembly, true);
    }

    /**
     * Capture the CBC entity position in the assembly render frame. When
     * {@code fromLiveCbc} is false (approximate anchor), the pivot is
     * re-captured from the live CBC entity on the first successful pose tick.
     * CBC and Create already use the same shipyard frame, so no additional ship
     * transform is applied here.
     */
    public void capturePivot(Vec3 cbcEntityPosWorld, Vec3 renderOriginAtAssembly, boolean fromLiveCbc) {
        this.renderOriginLocal = renderOriginAtAssembly;
        this.pivotLocal = cbcEntityPosWorld.subtract(renderOriginAtAssembly);
        this.pivotCaptured = fromLiveCbc;
    }


    /**
     * Fallback variant for approximate anchors that are already expressed in
     * Create/VS shipyard coordinates. The pivot is re-captured from the live CBC
     * entity on the first successful pose tick.
     */
    public void capturePivotLocal(Vec3 cbcEntityPosLocal, Vec3 renderOriginAtAssembly) {
        this.renderOriginLocal = renderOriginAtAssembly;
        this.pivotLocal = cbcEntityPosLocal.subtract(renderOriginAtAssembly);
        this.pivotCaptured = false;
    }

    public Vec3 getPivotLocal() {
        return pivotLocal;
    }

    /** Create/VS shipyard assembly render origin (Vec3.ZERO when unknown). */
    public Vec3 getRenderOriginLocal() {
        return renderOriginLocal;
    }

    public boolean isPivotCaptured() {
        return pivotCaptured;
    }

    public int getLinkedCbcEntityId() {
        return linkedCbcEntityId;
    }
}
