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

        // Get the cannon's current aim direction in world space
        Vec3 direction = CbcCompat.getAimDirection(level, mount, Direction.NORTH, 1.0f, false)
                .orElse(null);
        if (direction == null) return;

        // Extract yaw/pitch that our applyRotation expects.
        // Our applyRotation does: rotate(pitch, pitchAxis) → rotate(yaw, Y) → rotate(initialYaw, Y)
        // For a cannon with horizontal facing H, CBC sets initialYaw = H.getVecF().yRot()
        // and the cannon's world direction = applyRotation(forward, yaw, pitch, initialYaw).
        //
        // To invert: undo initialYaw first.
        BlockPos mountPos = bearing.resolveMount();
        Direction verticalDir = bearing.level.getBlockState(mountPos).getValue(BlockStateProperties.VERTICAL_DIRECTION);
        BlockPos cannonStart = mountPos.relative(verticalDir, -2);

        // initialYaw = horizontalFacing.getVecF().yRot() — Direction.getVecF() doesn't exist in 1.20.1
        // Use the vector components directly: NORTH=(0,0,-1), SOUTH=(0,0,1), EAST=(1,0,0), WEST=(-1,0,0)
        Direction hFacing = bearing.level.getBlockState(mountPos).getValue(BlockStateProperties.HORIZONTAL_FACING);
        net.minecraft.world.phys.Vec3 hVec = net.minecraft.world.phys.Vec3.atLowerCornerOf(hFacing.getNormal());
        float initialYaw = (float) Math.toDegrees(Math.atan2(-hVec.x, hVec.z));

        // Undo initialYaw: rotate direction by -initialYaw around Y
        double cosIY = Math.cos(Math.toRadians(-initialYaw));
        double sinIY = Math.sin(Math.toRadians(-initialYaw));
        double rx = direction.x * cosIY + direction.z * sinIY;
        double ry = direction.y;
        double rz = -direction.x * sinIY + direction.z * cosIY;

        // After undoing initialYaw, we have the (pitch→yaw) result.
        // For pitchAxis=X: forward(0,0,1) → pitch → (0, sin(p), cos(p)) → yaw → (cos(p)*sin(y), sin(p), cos(p)*cos(y))
        // yaw = atan2(rx, rz), pitch = asin(ry)
        float nextYaw = (float) Math.toDegrees(Math.atan2(rx, rz));
        float nextPitch = (float) Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, ry))));

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

        // Match CBC: get the cannon's initialOrientation from the mount
        BlockPos mount = resolveMount();
        Direction verticalDir = level.getBlockState(mount).getValue(BlockStateProperties.VERTICAL_DIRECTION);
        // CBC: cannonStartPos = mount.relative(verticalDir, -2)
        BlockPos cannonStart = mount.relative(verticalDir, -2);
        // CBC: initialOrientation = abstractMountedCannonContraption.initialOrientation()
        // For a standard cannon mount, initialOrientation = verticalDirection (the direction the barrel points)
        Direction initialOrientation = verticalDir;

        // Create the entity — positioned at the trunnion (mount.relative(verticalDir, -2))
        // This matches CBC's resetContraptionToOffset() pattern
        movedContraption = DecorationBearingContraptionEntity.create(level, this, contraption, initialOrientation);

        // Compute initial rotation from the cannon's current aim direction
        Vec3 direction = CbcCompat.getAimDirection(level, mount, Direction.NORTH, 1.0f, false)
                .orElse(null);
        if (direction != null) {
            // Get initialYaw from horizontal facing
            Direction hFacing = level.getBlockState(mount).getValue(BlockStateProperties.HORIZONTAL_FACING);
            net.minecraft.world.phys.Vec3 hVec = net.minecraft.world.phys.Vec3.atLowerCornerOf(hFacing.getNormal());
            float initialYaw = (float) Math.toDegrees(Math.atan2(-hVec.x, hVec.z));

            // Undo initialYaw to extract the (yaw, pitch) that applyRotation expects
            double cosIY = Math.cos(Math.toRadians(-initialYaw));
            double sinIY = Math.sin(Math.toRadians(-initialYaw));
            double rx = direction.x * cosIY + direction.z * sinIY;
            double ry = direction.y;
            double rz = -direction.x * sinIY + direction.z * cosIY;

            yaw = (float) Math.toDegrees(Math.atan2(rx, rz));
            pitch = (float) Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, ry))));
        } else {
            yaw = 0;
            pitch = 0;
        }

        // Position at trunnion (same as CBC: mount.relative(verticalDir, -2))
        movedContraption.setPos(cannonStart.getX(), cannonStart.getY(), cannonStart.getZ());
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
        // Re-position at the trunnion (matches CBC pattern)
        BlockPos mount = resolveMount();
        if (mount != null) {
            Direction verticalDir = level.getBlockState(mount).getValue(BlockStateProperties.VERTICAL_DIRECTION);
            BlockPos trunnion = mount.relative(verticalDir, -2);
            decoration.setPos(trunnion.getX(), trunnion.getY(), trunnion.getZ());
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
