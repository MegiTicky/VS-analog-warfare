package com.erika.vsanalogwarfare.decorationbearing;

import com.erika.vsanalogwarfare.registry.ModBlockEntities;
import com.erika.vsanalogwarfare.scope.ScopeCannonLink;
import com.erika.vsanalogwarfare.scope.compat.CbcCompat;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.OrientedContraptionEntity;
import com.simibubi.create.content.contraptions.bearing.BearingContraption;
import com.simibubi.create.content.contraptions.bearing.IBearingBlockEntity;
import com.simibubi.create.content.contraptions.IDisplayAssemblyExceptions;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.foundation.utility.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DecorationBearingBlockEntity extends GeneratingKineticBlockEntity
        implements IBearingBlockEntity, IDisplayAssemblyExceptions {
    private ScopeCannonLink linkedMount;
    private DecorationBearingContraptionEntity movedContraption;
    private float yaw;
    private float pitch;
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
                    bearing.disassemble();
                }
            } else {
                if (bearing.getSpeed() == 0) return;
                bearing.assemble();
            }
        }

        if (!bearing.running) return;
        if (bearing.movedContraption != null && bearing.movedContraption.isStalled()) return;

        BlockPos mount = bearing.resolveMount();
        if (mount == null) return;

        // Get the cannon's current aim direction in local space (no ship transform)
        Vec3 direction = CbcCompat.getAimDirection(level, mount, Direction.NORTH, 1.0f, false)
                .orElse(null);
        if (direction == null) return;

        Direction hFacing = bearing.level.getBlockState(mount).getValue(BlockStateProperties.HORIZONTAL_FACING);
        Direction initialOrientation = CbcCompat.getInitialOrientationFromCannon(level, mount);
        if (initialOrientation == null) initialOrientation = hFacing;

        Direction.Axis pitchAxis = initialOrientation.getAxis() == Direction.Axis.X
                ? Direction.Axis.Z : Direction.Axis.X;
        float nextYaw;
        float nextPitch;
        if (pitchAxis == Direction.Axis.X) {
            nextYaw = (float) -Math.toDegrees(Math.atan2(direction.x, direction.z));
            float pitchMagnitude = (float) Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, direction.y))));
            nextPitch = initialOrientation == Direction.NORTH ? pitchMagnitude : -pitchMagnitude;
        } else {
            float initialYaw = initialOrientation.toYRot();
            Vec3 localDirection = VecHelper.rotate(direction, -initialYaw, Direction.Axis.Y);
            float axisSign = initialOrientation == Direction.EAST ? 1.0f : -1.0f;
            float viewYaw = (float) Math.toDegrees(Math.atan2(
                    -axisSign * localDirection.z,
                    axisSign * localDirection.x));
            nextYaw = -viewYaw;
            nextPitch = (float) Math.toDegrees(Math.asin(
                    Math.max(-1, Math.min(1, axisSign * localDirection.y))));
        }

        float deltaYaw = Math.abs(nextYaw - bearing.yaw);
        float deltaPitch = Math.abs(nextPitch - bearing.pitch);
        if (deltaYaw > 180) deltaYaw = 360 - deltaYaw;
        if (deltaYaw < 0.01f && deltaPitch < 0.01f) return;
        bearing.yaw = nextYaw;
        bearing.pitch = nextPitch;
        if (bearing.movedContraption != null)
            bearing.movedContraption.setDecorationRotation(nextYaw, nextPitch);
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
        if (level == null || level.isClientSide || running || resolveMount() == null) return;
        Direction facing = getBlockState().getValue(BlockStateProperties.FACING);
        BearingContraption contraption = new BearingContraption(false, facing);
        try {
            if (!contraption.assemble(level, worldPosition)) return;
        } catch (AssemblyException e) {
            lastException = e;
            sendData();
            return;
        }
        lastException = null;
        contraption.removeBlocksFromWorld(level, BlockPos.ZERO);

        // Match CBC: initialOrientation comes from the cannon entity, not mount's HORIZONTAL_FACING
        BlockPos mount = resolveMount();
        Direction hFacing = level.getBlockState(mount).getValue(BlockStateProperties.HORIZONTAL_FACING);
        // Read the cannon's actual initialOrientation via reflection (fallback to hFacing if unavailable)
        Direction initialOrientation = CbcCompat.getInitialOrientationFromCannon(level, mount);
        if (initialOrientation == null) {
            initialOrientation = hFacing;
        }

        // Create the entity — positioned at the trunnion (mount.relative(verticalDir, -2))
        movedContraption = DecorationBearingContraptionEntity.create(level, this, contraption, initialOrientation);

        // Compute initial rotation from the cannon's current aim direction
        Vec3 direction = CbcCompat.getAimDirection(level, mount, Direction.NORTH, 1.0f, false)
                .orElse(null);
        if (direction != null) {
            // tryDirectionFromContraption returns applyRotation(initialOrientation_normal, pt)
            // After R_Y(initialYaw) · initial_orientation_normal = (0,0,1), the result is
            // R_Y(yaw) · R_pitchAxis(pitch) · (0,0,1).
            //
            // For non-X-axis (pitchAxis = X):
            //   result = (cos(pitch)*sin(yaw), -sin(pitch), cos(pitch)*cos(yaw))
            //   yaw = atan2(x, z), pitch = -asin(y)
            //
            // For X-axis (pitchAxis = Z):
            //   result = (-sin(yaw), 0, cos(yaw))  [pitch has no effect on (0,0,1)]
            //   yaw = atan2(-x, z), pitch = 0
            Direction.Axis pitchAxis = initialOrientation.getAxis() == Direction.Axis.X
                    ? Direction.Axis.Z : Direction.Axis.X;
            if (pitchAxis == Direction.Axis.X) {
                yaw = (float) -Math.toDegrees(Math.atan2(direction.x, direction.z));
                float pitchMagnitude = (float) Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, direction.y))));
                pitch = initialOrientation == Direction.NORTH ? pitchMagnitude : -pitchMagnitude;
            } else {
                float initialYaw = initialOrientation.toYRot();
                Vec3 localDirection = VecHelper.rotate(direction, -initialYaw, Direction.Axis.Y);
                float axisSign = initialOrientation == Direction.EAST ? 1.0f : -1.0f;
                float viewYaw = (float) Math.toDegrees(Math.atan2(
                        -axisSign * localDirection.z,
                        axisSign * localDirection.x));
                yaw = -viewYaw;
                pitch = (float) Math.toDegrees(Math.asin(
                        Math.max(-1, Math.min(1, axisSign * localDirection.y))));
            }
        } else {
            yaw = 0;
            pitch = 0;
        }

        // Cannon pivot: 2 blocks in the bearing's facing direction past the mount
        BlockPos cannonPivot = mount.relative(facing, 2);

        // Shift contraption blocks so they're relative to cannonPivot instead of worldPosition.
        // This makes the entity (at cannonPivot) the correct rotation center.
        BlockPos blockOffset = cannonPivot.subtract(worldPosition);
        Map<BlockPos, StructureBlockInfo> blocks = contraption.getBlocks();
        Map<BlockPos, StructureBlockInfo> shifted = new HashMap<>();
        for (Map.Entry<BlockPos, StructureBlockInfo> entry : blocks.entrySet()) {
            BlockPos newPos = entry.getKey().subtract(blockOffset);
            StructureBlockInfo oldInfo = entry.getValue();
            shifted.put(newPos, new StructureBlockInfo(newPos, oldInfo.state(), oldInfo.nbt()));
        }
        blocks.clear();
        blocks.putAll(shifted);

        movedContraption.setPos(Vec3.atBottomCenterOf(cannonPivot));
        movedContraption.setDecorationRotation(yaw, pitch);

        level.addFreshEntity(movedContraption);
        running = true;
        angle = 0;
        sendData();
        setChanged();
    }

    public void disassemble() {
        if (movedContraption != null) {
            yaw = 0;
            pitch = 0;
            movedContraption.setDecorationRotation(0, 0);
            movedContraption.disassemble();
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
        // No behaviours needed
    }

    @Override
    public boolean isAttachedTo(AbstractContraptionEntity entity) {
        return movedContraption == entity;
    }

    @Override
    public void attach(ControlledContraptionEntity entity) {
        // Our entity doesn't extend ControlledContraptionEntity, so this method
        // shouldn't be called directly. The attach is handled by the tick loop.
    }

    public void attach(DecorationBearingContraptionEntity decoration) {
        movedContraption = decoration;
        // Re-position at the cannon pivot
        BlockPos mount = resolveMount();
        if (mount != null) {
            Direction facing = getBlockState().getValue(BlockStateProperties.FACING);
            BlockPos cannonPivot = mount.relative(facing, 2);
            decoration.setPos(Vec3.atBottomCenterOf(cannonPivot));
        }
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

    // IBearingBlockEntity
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

    // No kinetic power
    @Override
    public float getGeneratedSpeed() {
        return 0;
    }

    @Override
    public boolean isNoisy() {
        return false;
    }

    // Create's save/load hooks
    @Override
    protected void write(CompoundTag tag, boolean clientPacket) {
        super.write(tag, clientPacket);
        if (linkedMount != null) tag.put("LinkedMount", linkedMount.save());
        tag.putFloat("Yaw", yaw);
        tag.putFloat("Pitch", pitch);
        tag.putBoolean("Running", running);
    }

    @Override
    public void read(CompoundTag tag, boolean clientPacket) {
        super.read(tag, clientPacket);
        linkedMount = tag.contains("LinkedMount") ? ScopeCannonLink.load(tag.getCompound("LinkedMount")) : null;
        yaw = tag.getFloat("Yaw");
        pitch = tag.getFloat("Pitch");
        running = tag.getBoolean("Running");
        if (!running) {
            movedContraption = null;
        }
    }

    @Nullable
    @Override
    public AssemblyException getLastAssemblyException() {
        return lastException;
    }
}
