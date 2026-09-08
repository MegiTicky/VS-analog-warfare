package com.erika.vsanalogwarfare.mouseaim;

import com.erika.vsanalogwarfare.config.CommonConfig;
import com.erika.vsanalogwarfare.registry.ModBlockEntities;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.CenteredSideValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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
    /** True when {@link #targetDirection} was captured in the scope ship's frame. */
    private boolean targetShipRelative;
    private long lastTargetGameTime = Long.MIN_VALUE;

    private final TurretYawController turretYaw = new TurretYawController();
    /** Latest commanded output, in Create RPM; read by the output interface. */
    private volatile float turretOutputRpm;
    private final MouseAimOutputInterface outputInterface;

    @Nullable
    private ScrollOptionBehaviour<MouseAimMode> mode;
    @Nullable
    private ScrollOptionBehaviour<TurretStrength> strength;

    public MouseAimBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MOUSE_AIM.get(), pos, state);
        outputInterface = new MouseAimOutputInterface(ModBlockEntities.MOUSE_AIM.get(), pos,
                state.setValue(MouseAimBlock.OUTPUT, Boolean.TRUE), this);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        mode = new ScrollOptionBehaviour<>(MouseAimMode.class,
                Component.translatable("vs_analog_warfare.mouse_aim.mode.label"),
                this,
                // East/west faces, so the strength setting never overlaps it.
                new CenteredSideValueBoxTransform((state, dir) -> dir.getAxis() == Direction.Axis.X));
        mode.requiresWrench();
        behaviours.add(mode);
        strength = new ScrollOptionBehaviour<>(TurretStrength.class,
                Component.translatable("vs_analog_warfare.mouse_aim.strength.label"),
                this,
                new CenteredSideValueBoxTransform((state, dir) -> dir.getAxis() == Direction.Axis.Z));
        strength.requiresWrench();
        behaviours.add(strength);
    }

    public MouseAimMode getMode() {
        return mode != null ? mode.get() : MouseAimMode.CANNON;
    }

    public TurretStrength getTurretStrength() {
        return strength != null ? strength.get() : TurretStrength.FIRM;
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
        applyTurretOutput(turretYaw.computeTargetRpm(this, targetScopePos, targetMountPos, target, targetShipRelative));
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

    public void setTarget(UUID playerId, BlockPos mountPos, BlockPos scopePos, Vec3 direction, boolean shipRelative) {
        this.targetPlayer = playerId;
        this.targetMountPos = mountPos.immutable();
        this.targetScopePos = scopePos.immutable();
        this.targetDirection = direction.normalize();
        this.targetShipRelative = shipRelative;
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
        this.targetShipRelative = false;
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
        Vec3i outputOffset = MouseAimBlock.getOutputFace(
                getBlockState()).getNormal();
        if (from.subtract(worldPosition).equals(outputOffset)) {
            return outputInterface;
        }
        return null;
    }

    @Override
    public List<KineticBlockEntity> getAllKineticBlockEntities() {
        return List.of(this, outputInterface);
    }

    @Override
    protected void write(CompoundTag compound, boolean clientPacket) {
        super.write(compound, clientPacket);
        if (!clientPacket) {
            compound.put("OutputInterface", outputInterface.writeServer(new CompoundTag()));
        }
    }

    @Override
    protected void read(CompoundTag compound, boolean clientPacket) {
        super.read(compound, clientPacket);
        if (clientPacket) {
            return;
        }
        if (compound.contains("OutputInterface")) {
            outputInterface.readServer(compound.getCompound("OutputInterface"));
        }
    }
}
