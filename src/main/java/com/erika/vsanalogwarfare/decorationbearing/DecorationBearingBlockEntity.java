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
                    bearing.disassemble();
                }
            } else {
                if (bearing.getSpeed() == 0) return;
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
        Direction initialOrientation = CbcCompat.getInitialOrientationFromCannon(level, mount);
        if (initialOrientation == null) {
            Direction hFacing = level.getBlockState(mount).getValue(BlockStateProperties.HORIZONTAL_FACING);
            initialOrientation = hFacing;
        }

        // Resolve the live CBC entity for initial pose
        Object cbcEntity = CbcCompat.resolveLiveCbcEntity(level, mount);
        Direction finalInitialOrientation = initialOrientation;

        // Create the entity — positioned at the assembly origin (worldPosition)
        movedContraption = DecorationBearingContraptionEntity.create(level, this, contraption, initialOrientation);

        // Compute pivot offset: the CBC cannon trunnion is at mount.relative(facing, 2).
        // In contraption-local coords, this is (cannonPivot - worldPosition).
        BlockPos cannonPivot = mount.relative(facing, 2);
        Vec3 pivotLocal = Vec3.atLowerCornerOf(cannonPivot.subtract(worldPosition));
        movedContraption.setPivotOffset(pivotLocal);

        // Initialize rotation from CBC if available
        if (cbcEntity != null) {
            CbcCompat.CbcPoseData pose = CbcCompat.readCbcPoseData(cbcEntity);
            if (pose != null) {
                movedContraption.setDecorationRotation(pose.viewYaw(), pose.viewPitch());
            }
        }

        // Position entity at the assembly origin (NOT at the cannon pivot)
        movedContraption.setPos(Vec3.atBottomCenterOf(worldPosition));

        level.addFreshEntity(movedContraption);
        running = true;
        angle = 0;
        sendData();
        setChanged();
    }

    public void disassemble() {
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
