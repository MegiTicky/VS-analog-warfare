package com.erika.vsanalogwarfare.mouseaim;

import com.erika.vsanalogwarfare.config.CommonConfig;
import com.erika.vsanalogwarfare.registry.ModBlockEntities;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import rbasamoyai.createbigcannons.base.multiple_kinetic_interface.HasMultipleKineticInterfaces;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

public class MouseAimBlockEntity extends KineticBlockEntity implements HasMultipleKineticInterfaces {
    @Nullable
    private UUID targetPlayer;
    @Nullable
    private BlockPos targetMountPos;
    @Nullable
    private BlockPos targetScopePos;
    @Nullable
    private Vec3 targetDirection;
    private long lastTargetGameTime = Long.MIN_VALUE;

    private final TurretYawController turretYaw = new TurretYawController();
    /** Latest commanded output, in Create RPM; read by the output interface. */
    private volatile float turretOutputRpm;
    private final MouseAimOutputInterface outputInterface;
    /** Last seen input end; a change re-routes the output face. */
    @Nullable
    private Direction lastInputFace;

    /** Aim mode, set from the config screen. */
    private MouseAimMode mode = MouseAimMode.CANNON;
    /** Output aggressiveness, set from the config screen. */
    private TurretStrength strength = TurretStrength.FIRM;

    public MouseAimBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MOUSE_AIM.get(), pos, state);
        outputInterface = new MouseAimOutputInterface(ModBlockEntities.MOUSE_AIM.get(), pos,
                state.setValue(MouseAimBlock.OUTPUT, Boolean.TRUE), this);
    }

    /**
     * Direction from this block toward the end that currently powers it, or
     * {@code null} when no shaft is driving it. Follows Create's
     * {@code DirectionalShaftHalvesBlockEntity} pattern: the kinetic network
     * records the source block we receive speed from.
     */
    @Nullable
    public Direction getInputFace() {
        if (!hasSource()) {
            return null;
        }
        Vec3i offset = source.subtract(worldPosition);
        Direction face = Direction.getNearest(offset.getX(), offset.getY(), offset.getZ());
        return face.getAxis() == getBlockState().getValue(MouseAimBlock.AXIS) ? face : null;
    }

    /**
     * Direction toward the current rotation-output end — the input end's
     * opposite — or {@code null} when the block has no input.
     */
    @Nullable
    public Direction getOutputFace() {
        Direction input = getInputFace();
        return input == null ? null : input.getOpposite();
    }

    public MouseAimMode getMode() {
        return mode;
    }

    public TurretStrength getTurretStrength() {
        return strength;
    }

    public void setMode(MouseAimMode mode) {
        if (mode == null || mode == this.mode) {
            return;
        }
        this.mode = mode;
        setChanged();
    }

    public void setStrength(TurretStrength strength) {
        if (strength == null || strength == this.strength) {
            return;
        }
        this.strength = strength;
        setChanged();
        // The max-RPM clamp is part of the generated speed capability.
        outputInterface.updateGeneratedRotation();
    }

    /** Latest PID output command for the turret rotation face, in RPM. */
    public float getTurretOutputRpm() {
        return turretOutputRpm;
    }

    @Override
    public void tick() {
        super.tick();
        outputInterface.tick();
        if (level == null || level.isClientSide) {
            return;
        }
        // Create only re-runs rotation propagation on block add/remove, so a
        // flipped input end needs an explicit re-route of the output network.
        Direction inputFace = getInputFace();
        if (inputFace != lastInputFace) {
            lastInputFace = inputFace;
            outputInterface.refreshConnections();
        }
        if (getMode() == MouseAimMode.CANNON) {
            float idleRpm = turretYaw.idle();
            if (idleRpm == 0.0F) {
                turretYaw.reset();
            }
            applyTurretOutput(idleRpm);
            tickCannon();
            return;
        }
        tickTurretMode();
    }

    private void tickCannon() {
        if (!isMouseAimActive() || targetDirection == null || targetMountPos == null) {
            return;
        }
        if (level.getGameTime() - lastTargetGameTime > CommonConfig.mouseAimTargetTimeoutTicks()) {
            clearTarget();
            return;
        }
        MouseAimController.tick(this, targetMountPos, targetDirection.normalize(), getMouseAimRateDegreesPerTick());
    }

    private void tickTurretMode() {
        boolean fresh = isMouseAimActive()
                && targetDirection != null && targetMountPos != null && targetScopePos != null
                && level.getGameTime() - lastTargetGameTime <= CommonConfig.mouseAimTargetTimeoutTicks();
        if (!fresh) {
            clearTarget();
            applyTurretOutput(turretYaw.idle());
            return;
        }
        Vec3 target = targetDirection.normalize();
        MouseAimController.tickTurretPitch(this, targetMountPos, target, getMouseAimRateDegreesPerTick());
        applyTurretOutput(turretYaw.computeTargetRpm(this, targetMountPos, target));
    }

    private void applyTurretOutput(float rpm) {
        if (Math.abs(rpm - turretOutputRpm) > 0.01F) {
            turretOutputRpm = rpm;
            outputInterface.updateGeneratedRotation();
        }
    }

    public boolean isMouseAimActive() {
        return Math.abs(getSpeed()) >= CommonConfig.mouseAimMinSpeed() && !isOverStressed();
    }

    public double getMouseAimRateDegreesPerTick() {
        return Math.abs(convertToAngular(getSpeed())) * CommonConfig.mouseAimRateMultiplier();
    }

    public void setTarget(UUID playerId, BlockPos mountPos, BlockPos scopePos, Vec3 direction) {
        this.targetPlayer = playerId;
        this.targetMountPos = mountPos.immutable();
        this.targetScopePos = scopePos.immutable();
        this.targetDirection = direction.normalize();
        this.lastTargetGameTime = level == null ? 0L : level.getGameTime();
        setChanged();
    }

    public boolean controls(BlockPos mountPos) {
        if (level == null || mountPos == null) {
            return false;
        }
        return MouseAimController.findAdjacentMount(level, worldPosition).filter(mountPos::equals).isPresent();
    }

    public void clearTarget() {
        if (targetDirection == null) {
            return;
        }
        this.targetPlayer = null;
        this.targetMountPos = null;
        this.targetScopePos = null;
        this.targetDirection = null;
        this.lastTargetGameTime = Long.MIN_VALUE;
        this.turretYaw.reset();
        setChanged();
    }

    @Nullable
    public UUID targetPlayer() {
        return targetPlayer;
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        outputInterface.setLevel(level);
    }

    @Nullable
    @Override
    public KineticBlockEntity getInterfacingBlockEntity(BlockPos from) {
        Direction outputFace = getOutputFace();
        if (outputFace == null) {
            return null;
        }
        BlockPos outputOffset = BlockPos.ZERO.relative(outputFace);
        return from.equals(outputOffset) || from.equals(BlockPos.ZERO.subtract(outputOffset))
                ? outputInterface : null;
    }

    @Override
    public List<KineticBlockEntity> getAllKineticBlockEntities() {
        return List.of(this, outputInterface);
    }

    @Override
    protected void write(CompoundTag compound, boolean clientPacket) {
        super.write(compound, clientPacket);
        if (!clientPacket) {
            compound.putString("Mode", mode.name());
            compound.putString("Strength", strength.name());
            compound.put("OutputInterface", outputInterface.writeServer(new CompoundTag()));
        }
    }

    @Override
    protected void read(CompoundTag compound, boolean clientPacket) {
        super.read(compound, clientPacket);
        if (clientPacket) {
            return;
        }
        if (compound.contains("Mode")) {
            mode = MouseAimMode.valueOf(compound.getString("Mode"));
        }
        if (compound.contains("Strength")) {
            strength = TurretStrength.valueOf(compound.getString("Strength"));
        }
        if (compound.contains("OutputInterface")) {
            outputInterface.readServer(compound.getCompound("OutputInterface"));
        }
    }
}
