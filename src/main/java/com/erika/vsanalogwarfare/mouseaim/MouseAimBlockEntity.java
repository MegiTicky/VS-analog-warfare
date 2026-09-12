package com.erika.vsanalogwarfare.mouseaim;

import com.erika.vsanalogwarfare.config.CommonConfig;
import com.erika.vsanalogwarfare.registry.ModBlockEntities;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
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
    /**
     * Output end as of the last time the block had input, persisted so the
     * power persona keeps excluding the output end after a world reload.
     * Create re-walks kinetic networks on the first tick after load from
     * NBT-restored state; until the input network re-forms, a live-derived
     * output face is null and claiming both axis ends lets the input walk
     * bridge into the transmitter chain, where the two networks' sources
     * conflict and Create destroys blocks.
     */
    @Nullable
    private Direction storedOutputFace;

    /** Aim mode, set from the config screen. */
    private MouseAimMode mode = MouseAimMode.CANNON;

    private final TurretAutotuner autotuner = new TurretAutotuner();
    /** Per-block servo gains from a calibration; {@code null} runs the global config template. */
    @Nullable
    private TurretTuning tuning;

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
     * opposite — or {@code null} when the block has no input. Falls back to
     * the persisted output face while unpowered so the personas keep their
     * input/output split across a world reload (see {@link #storedOutputFace}).
     */
    @Nullable
    public Direction getOutputFace() {
        Direction input = getInputFace();
        if (input != null) {
            return input.getOpposite();
        }
        Direction.Axis axis = getBlockState().getValue(MouseAimBlock.AXIS);
        return storedOutputFace != null && storedOutputFace.getAxis() == axis ? storedOutputFace : null;
    }

    public MouseAimMode getMode() {
        return mode;
    }

    /**
     * Fixed turret pitch slew rate in degrees per tick, derived from the
     * output ceiling through the same conversion and multiplier as the
     * cannon-mode chase rate, so elevation speed matches the yaw authority.
     */
    public double getTurretPitchSlewDegPerTick() {
        return Math.abs(convertToAngular((float) CommonConfig.turretMaxOutputRpm()))
                * CommonConfig.mouseAimRateMultiplier();
    }

    public void setMode(MouseAimMode mode) {
        if (mode == null || mode == this.mode) {
            return;
        }
        this.mode = mode;
        setChanged();
    }

    /** Gains the yaw servo runs this tick: calibrated values or the global template. */
    public TurretTuning getEffectiveTuning() {
        return tuning == null ? TurretTuning.fromConfig() : tuning;
    }

    /** Adopts calibrated gains. Called by {@link TurretAutotuner} on completion. */
    void storeTuning(TurretTuning values) {
        this.tuning = values;
        setChanged();
    }

    public boolean isCalibrating() {
        return autotuner.active();
    }

    /**
     * Starts a step-test calibration. Prefers the mount of a fresh aim
     * target, falling back to any adjacent cannon mount. Reports progress and
     * the result to the triggering player via the action bar.
     */
    public void startCalibration(ServerPlayer player) {
        if (level == null) {
            return;
        }
        BlockPos mount = targetMountPos;
        if (mount == null
                || level.getGameTime() - lastTargetGameTime > CommonConfig.mouseAimTargetTimeoutTicks()) {
            mount = MouseAimController.findAdjacentMount(level, worldPosition).orElse(null);
        }
        if (mount == null) {
            player.displayClientMessage(
                    Component.translatable("vs_analog_warfare.mouse_aim.calibrate.no_mount"), true);
            return;
        }
        String error = autotuner.start(this, mount, player);
        if (error != null) {
            player.displayClientMessage(Component.translatable(error), true);
        }
    }

    /** Drops calibrated gains (and any running calibration), back to the global template. */
    public void resetTuning(ServerPlayer player) {
        autotuner.cancel();
        if (tuning != null) {
            tuning = null;
            setChanged();
        }
        player.displayClientMessage(
                Component.translatable("vs_analog_warfare.mouse_aim.tuning.reset"), true);
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
        // flipped input end needs an explicit re-route of the output network —
        // and of this BE's own connections, since the power persona's accepted
        // faces exclude the current output end.
        Direction inputFace = getInputFace();
        if (inputFace != lastInputFace) {
            lastInputFace = inputFace;
            if (inputFace != null) {
                storedOutputFace = inputFace.getOpposite();
                setChanged();
            }
            outputInterface.refreshConnections();
            detachKinetics();
            attachKinetics();
        }
        if (getMode() == MouseAimMode.CANNON) {
            autotuner.cancel();
            float idleRpm = turretYaw.idle((float) getEffectiveTuning().slewPerTick());
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
        if (autotuner.active()) {
            // Calibration drives the output itself; normal aiming resumes
            // (slewing from the test's last command) once it finishes.
            float command = autotuner.tick(this);
            if (!autotuner.active()) {
                turretYaw.syncOutput(command);
            }
            applyTurretOutput(command);
            return;
        }
        boolean fresh = isMouseAimActive()
                && targetDirection != null && targetMountPos != null && targetScopePos != null
                && level.getGameTime() - lastTargetGameTime <= CommonConfig.mouseAimTargetTimeoutTicks();
        if (!fresh) {
            clearTarget();
            applyTurretOutput(turretYaw.idle((float) getEffectiveTuning().slewPerTick()));
            return;
        }
        Vec3 target = targetDirection.normalize();
        MouseAimController.tickTurretPitch(this, targetMountPos, target, getTurretPitchSlewDegPerTick());
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

    /**
     * Cannon-mode chase rate in degrees per tick: the input shaft speed scaled
     * by the multiplier, clamped to the same ceiling the turret servo uses
     * ({@code maxOutputRpm}) — without the clamp, a 256 RPM input chases at
     * 9.6 deg/tick and the physical mount actuator slings past the target.
     * The input speed still sets the rate below the ceiling, so a slow crank
     * fine-aims.
     */
    public double getMouseAimRateDegreesPerTick() {
        double ceiling = Math.abs(convertToAngular((float) CommonConfig.turretMaxOutputRpm()))
                * CommonConfig.mouseAimRateMultiplier();
        return Math.min(Math.abs(convertToAngular(getSpeed())) * CommonConfig.mouseAimRateMultiplier(), ceiling);
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
        // CBC's propagation mixin replaces the BE a rotation walk arrives at
        // with the interface bound to the arrival face (`from` is this block's
        // face toward the walking neighbour). Bind only the output end:
        // binding the input end swallows input-network walks, so a source
        // speed change never reaches this block and its rotation goes stale
        // until it is broken and replaced.
        return from.equals(BlockPos.ZERO.relative(outputFace)) ? outputInterface : null;
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
            compound.put("OutputInterface", outputInterface.writeServer(new CompoundTag()));
            if (tuning != null) {
                compound.put("Tuning", tuning.write());
            }
            if (storedOutputFace != null) {
                compound.putByte("OutputFace", (byte) storedOutputFace.get3DDataValue());
            }
            compound.putFloat("TurretOutput", turretOutputRpm);
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
        if (compound.contains("OutputInterface")) {
            outputInterface.readServer(compound.getCompound("OutputInterface"));
        }
        tuning = TurretTuning.read(compound.contains("Tuning") ? compound.getCompound("Tuning") : null);
        if (compound.contains("OutputFace")) {
            storedOutputFace = Direction.from3DDataValue(compound.getByte("OutputFace"));
        }
        if (compound.contains("TurretOutput")) {
            turretOutputRpm = compound.getFloat("TurretOutput");
        }
        // Seed from the restored source so the flip-repair does not fire
        // spuriously on the first tick after a world load.
        lastInputFace = getInputFace();
    }
}
