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

import javax.annotation.Nullable;
import java.util.List;

public class DecorationBearingBlockEntity extends GeneratingKineticBlockEntity
        implements IBearingBlockEntity, IDisplayAssemblyExceptions {
    private ScopeCannonLink linkedMount;
    private ControlledContraptionEntity movedContraption;
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
        Vec3 direction = CbcCompat.getAimDirection(level, mount, Direction.NORTH, 1.0f, false)
                .orElse(null);
        if (direction == null) return;
        float nextYaw = (float) -Math.toDegrees(Math.atan2(-direction.x, direction.z));
        float nextPitch = (float) -Math.toDegrees(Math.asin(direction.y));
        bearing.yaw = nextYaw;
        bearing.pitch = nextPitch;
        if (bearing.movedContraption instanceof DecorationBearingContraptionEntity decoration)
            decoration.setDecorationRotation(nextYaw, nextPitch);
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
        BlockPos mount = resolveMount();
        BlockPos anchor = worldPosition.relative(facing);
        Direction verticalDir = level.getBlockState(mount).getValue(BlockStateProperties.VERTICAL_DIRECTION);
        BlockPos trunnion = mount.relative(verticalDir, -2);
        Vec3 pivotOffset = Vec3.atCenterOf(trunnion).subtract(Vec3.atLowerCornerOf(anchor));
        movedContraption = DecorationBearingContraptionEntity.create(level, this, contraption, facing, pivotOffset);
        movedContraption.setRotationAxis(facing.getAxis());
        level.addFreshEntity(movedContraption);
        running = true;
        angle = 0;
        if (movedContraption instanceof DecorationBearingContraptionEntity decoration)
            decoration.setDecorationRotation(yaw, pitch);
        sendData();
        setChanged();
    }

    public void disassemble() {
        if (movedContraption != null) {
            yaw = 0;
            pitch = 0;
            if (movedContraption instanceof DecorationBearingContraptionEntity decoration)
                decoration.setDecorationRotation(0, 0);
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
        if (!(entity instanceof DecorationBearingContraptionEntity decoration)) return;
        movedContraption = decoration;
        Direction facing = getBlockState().getValue(BlockStateProperties.FACING);
        BlockPos anchor = worldPosition.relative(facing);
        decoration.setPos(anchor.getX(), anchor.getY(), anchor.getZ());
        decoration.setRotationAxis(facing.getAxis());
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
