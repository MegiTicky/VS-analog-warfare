package com.erika.vsanalogwarfare.decorationbearing;

import com.erika.vsanalogwarfare.registry.ModBlockEntities;
import com.erika.vsanalogwarfare.scope.ScopeCannonLink;
import com.erika.vsanalogwarfare.scope.compat.CbcCompat;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.bearing.BearingContraption;
import com.simibubi.create.content.contraptions.bearing.IBearingBlockEntity;
import com.simibubi.create.content.contraptions.IDisplayAssemblyExceptions;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.List;

public class DecorationBearingBlockEntity extends GeneratingKineticBlockEntity
        implements IBearingBlockEntity, IDisplayAssemblyExceptions {
    private static final Logger LOGGER = LogUtils.getLogger();
    private ScopeCannonLink linkedMount;
    private DecorationBearingContraptionEntity movedContraption;
    private boolean running;
    private boolean assembleNextTick;
    private float angle;
    private AssemblyException lastException;

    public DecorationBearingBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DECORATION_BEARING.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, DecorationBearingBlockEntity bearing) {
        if (level.isClientSide) return;

        bearing.tick();

        if (bearing.assembleNextTick) {
            bearing.assembleNextTick = false;
            if (bearing.running) {
                if (bearing.movedContraption != null && !bearing.movedContraption.isStalled()) {
                    // already running, do nothing
                } else {
                    LOGGER.info("[VSAW_DBC] tick: disassembling (running={}, stalled={})",
                            bearing.running, bearing.movedContraption != null && bearing.movedContraption.isStalled());
                    bearing.disassemble();
                }
            } else {
                if (bearing.getSpeed() == 0) return;
                LOGGER.info("[VSAW_DBC] tick: assembling (speed={}, linkedMount={}, pos={})",
                        bearing.getSpeed(), bearing.linkedMount, pos);
                bearing.assemble();
            }
        }

        if (!bearing.running) return;
        if (bearing.movedContraption != null && bearing.movedContraption.isStalled()) return;

        // Validate the entity still exists and the link is still valid
        if (bearing.movedContraption == null) {
            bearing.disassemble();
            return;
        }

        BlockPos mount = bearing.resolveMount();
        if (mount == null) {
            // Linked mount no longer valid — disassemble
            bearing.disassemble();
            return;
        }

        // Validate the live CBC entity still exists
        Object cbcEntity = CbcCompat.resolveLiveCbcEntity(level, mount);
        if (cbcEntity == null) {
            // CBC entity temporarily unavailable — keep running, the entity will retry next tick
            return;
        }

    }

    @Nullable
    private BlockPos resolveMount() {
        if (level == null || linkedMount == null) return null;
        BlockPos resolved = linkedMount.resolve(level, null);
        return resolved != null && CbcCompat.isCannonMount(level.getBlockEntity(resolved)) ? resolved : null;
    }

    public boolean link(BlockPos target) {
        if (level == null || !CbcCompat.isCannonMount(level.getBlockEntity(target))) return false;
        linkedMount = ScopeCannonLink.fromTarget(level, target);
        setChanged();
        if (!level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        return true;
    }

    @Nullable
    public BlockPos getLinkedMountPos() {
        return resolveMount();
    }

    public boolean isRunning() {
        return running;
    }

    public void assemble() {
        if (level == null || level.isClientSide || running || resolveMount() == null) {
            LOGGER.info("[VSAW_DBC] assemble: SKIPPED (level={}, clientSide={}, running={}, mount={})",
                    level != null, level != null && level.isClientSide, running, resolveMount());
            return;
        }
        Direction facing = getBlockState().getValue(BlockStateProperties.FACING);
        BearingContraption contraption = new BearingContraption(false, facing);
        boolean assembled;
        try {
            assembled = contraption.assemble(level, worldPosition);
        } catch (AssemblyException e) {
            LOGGER.info("[VSAW_DBC] assemble: AssemblyException at {}", worldPosition, e);
            lastException = e;
            sendData();
            return;
        }
        if (!assembled) {
            LOGGER.info("[VSAW_DBC] assemble: contraption.assemble() returned false at {}", worldPosition);
            return;
        }
        lastException = null;
        // Resolve the live CBC entity for the initial pose and pivot. When it
        // is not yet resolvable (cannon not assembled yet, chunk race, etc.)
        // fall back to an approximate pivot and zero pose — the entity
        // re-captures the exact pivot from the live CBC anchor on the first
        // successful pose tick.
        BlockPos mount = resolveMount();
        Object cbcEntity = CbcCompat.resolveLiveCbcEntity(level, mount);
        CbcCompat.CbcPoseData pose = cbcEntity != null ? CbcCompat.readCbcPoseData(cbcEntity) : null;
        if (pose == null) {
            LOGGER.info("[VSAW_DBC] assemble: live CBC entity unavailable at {} (mount={}, mountBE={}, reason={})",
                    worldPosition, mount,
                    mount != null ? level.getBlockEntity(mount) : null,
                    CbcCompat.describeResolutionFailure(level, mount));
        }

        Direction initialOrientation = CbcCompat.getInitialOrientationFromCannon(level, mount);
        if (initialOrientation == null) {
            Direction hFacing = level.getBlockState(mount).getValue(BlockStateProperties.HORIZONTAL_FACING);
            initialOrientation = hFacing;
        }

        movedContraption = DecorationBearingContraptionEntity.create(level, this, contraption, initialOrientation);

        // Render origin = bottom-center of the contraption's own anchor block
        // (BearingContraption anchors at bearingPos.relative(facing), so this
        // is where local block (0,0,0) renders from).
        Vec3 renderOrigin = Vec3.atBottomCenterOf(contraption.anchor);
        if (pose != null) {
            // pivotLocal is relative to the CBC entity's raw position (not its
            // anchorVec, which carries a +0.5 x/z offset) — that's what makes
            // the render rotation center land on the cannon's visual pivot.
            movedContraption.capturePivot(pose.entityPos(), renderOrigin);
            // pose.viewYaw() is already CBC's m_5675_ (negated) convention;
            // store it unmodified.
            movedContraption.setDecorationRotation(pose.viewYaw(), pose.viewPitch());
        } else {
            // Approximate the POCE spawn position (atLowerCornerOf(mount - 2 along
            // the vertical axis)); re-captured from the live entity on the first
            // pose tick.
            movedContraption.capturePivot(Vec3.atLowerCornerOf(mount.below(2)), renderOrigin, false);
            // Neutral pose in the internal convention: render applies
            // yaw + initialYaw, so -initialYaw yields identity.
            movedContraption.setDecorationRotation(-initialOrientation.toYRot(), 0.0f);
        }

        movedContraption.setPos(renderOrigin);

        contraption.removeBlocksFromWorld(level, BlockPos.ZERO);

        level.addFreshEntity(movedContraption);
        LOGGER.info("[VSAW_DBC] assemble: build={} entity CREATED id={} pivotLocal={} renderOrigin={} mount={} fromLiveCbc={}",
                DecorationBearingContraptionEntity.BUILD_TAG,
                movedContraption.getId(), movedContraption.getPivotLocal(), renderOrigin, mount, pose != null);
        running = true;
        angle = 0;
        sendData();
        setChanged();
    }

    public void disassemble() {
        LOGGER.info("[VSAW_DBC] disassemble: entity={}", movedContraption);
        if (movedContraption != null) {
            movedContraption.disassemble();
            if (!movedContraption.isRemoved()) movedContraption.discard();
        }
        movedContraption = null;
        running = false;
        assembleNextTick = false;
        sendData();
        setChanged();
    }

    @Override
    public void remove() {
        if (level != null && !level.isClientSide) disassemble();
        super.remove();
    }

    @Override
    public void addBehaviours(List<com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour> behaviours) {
    }

    @Override
    public boolean isAttachedTo(AbstractContraptionEntity entity) {
        return movedContraption == entity;
    }

    @Override
    public void attach(ControlledContraptionEntity entity) {
    }

    public void attach(DecorationBearingContraptionEntity decoration) {
        movedContraption = decoration;
        running = true;
        setChanged();
    }

    @Override
    public void onStall() {
        sendData();
    }

    @Override
    public boolean isValid() {
        return !isRemoved();
    }

    @Override
    public BlockPos getBlockPosition() {
        return worldPosition;
    }

    @Override
    public float getInterpolatedAngle(float partialTicks) {
        return angle;
    }

    @Override
    public boolean isWoodenTop() {
        return false;
    }

    @Override
    public void setAngle(float angle) {
        this.angle = angle;
    }

    @Override
    public float getGeneratedSpeed() {
        return 0;
    }

    @Override
    public boolean isNoisy() {
        return false;
    }

    @Override
    protected void write(CompoundTag tag, boolean clientPacket) {
        super.write(tag, clientPacket);
        if (linkedMount != null) tag.put("LinkedMount", linkedMount.save());
        tag.putBoolean("Running", running);
    }

    @Override
    public void read(CompoundTag tag, boolean clientPacket) {
        super.read(tag, clientPacket);
        linkedMount = tag.contains("LinkedMount") ? ScopeCannonLink.load(tag.getCompound("LinkedMount")) : null;
        // Do NOT restore running=true from saved state — the entity may not exist after reload.
        // The block entity will reassemble naturally if conditions are met.
        running = false;
        movedContraption = null;
    }

    @Nullable
    @Override
    public AssemblyException getLastAssemblyException() {
        return lastException;
    }
}
