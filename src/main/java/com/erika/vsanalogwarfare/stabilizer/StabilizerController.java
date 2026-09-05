package com.erika.vsanalogwarfare.stabilizer;

import com.erika.vsanalogwarfare.config.CommonConfig;
import com.erika.vsanalogwarfare.scope.compat.CbcCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4dc;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hold-on-release vertical stabilizer control loop.
 *
 * Runs inside {@code CannonMountBlockEntity.tick()} via the mixin injection at
 * the pitch {@code getAngularSpeed} call site, on both server and client. While
 * the player rotates the gun (shaft input, mouse aim, scroll adjustment) the
 * held world elevation continuously re-captures to the gun's current elevation,
 * so the stabilizer is transparent during input. The moment input stops, the
 * current world elevation is frozen as the target and a PI controller with
 * ship-rotation feedforward drives CBC's pitch advance so the gun keeps that
 * elevation in world space regardless of ship pitch and roll.
 *
 * VS physics runs at 60 TPS (3 subticks per game tick) but the control loop
 * runs at the 20 TPS mount tick: the feedforward term uses the ship's
 * continuous rotation rate (synced omega, finite-difference fallback), which is
 * exact between ticks, while the PI terms correct residual drift per game tick.
 */
public final class StabilizerController {

    /** How long (ticks) a scroll/mouse-aim adjustment counts as active input. */
    private static final int EXTERNAL_INPUT_MEMORY_TICKS = 2;

    /**
     * A per-tick pitch change deviating from the predicted advance (base speed
     * + previous stabilizer offset) by more than this many degrees means an
     * external writer moved the gun (mouse aim, scroll, CBC seat drag, block
     * entity sync replacing the pitch) — treat it as player input and
     * re-capture the held elevation instead of fighting it.
     */
    private static final float EXTERNAL_STEP_THRESHOLD_DEG = 0.3f;

    /** Low-pass factor for the client-side feedforward rate (bursty sync). */
    private static final double CLIENT_RATE_SMOOTHING = 0.5;

    /** Server: mount pos -> stabilizer pos. Client: mount pos -> synced stabilizer pos. */
    private static final Map<Long, BlockPos> MOUNT_LINKS = new ConcurrentHashMap<>();
    /** Per-mount servo state, one per side. */
    private static final Map<Long, MountState> STATES = new ConcurrentHashMap<>();

    public static final class MountState {
        public double targetElevDeg;
        public boolean targetValid;
        public boolean inputActive;
        public double integral;
        public long lastExternalInputGameTime = Long.MIN_VALUE;
        public boolean dirty;
        /** Bookkeeping for local external-input detection. */
        public float prevCannonPitch;
        public float prevBaseSpeed;
        public float prevOffset;
        public boolean bookkeepingValid;
        /** Low-pass state for the client feedforward rate. */
        public double smoothedElevRatePerTick;

        void reset() {
            targetValid = false;
            integral = 0.0;
            dirty = true;
        }
    }

    private StabilizerController() {
    }

    // ------------------------------------------------------------------
    // Link registry (server side managed by StabilizerBlockEntity)
    // ------------------------------------------------------------------

    public static void onLinked(BlockPos stabilizerPos, BlockPos mountPos) {
        MOUNT_LINKS.put(mountPos.asLong(), stabilizerPos.immutable());
        STATES.computeIfAbsent(mountPos.asLong(), k -> new MountState()).reset();
    }

    public static void onUnlinked(BlockPos mountPos) {
        MOUNT_LINKS.remove(mountPos.asLong());
        MountState state = STATES.remove(mountPos.asLong());
        if (state != null) {
            state.dirty = true;
        }
        ClientStabilizerState.clear(mountPos);
    }

    @Nullable
    public static BlockPos linkedStabilizer(BlockPos mountPos) {
        return MOUNT_LINKS.get(mountPos.asLong());
    }

    @Nullable
    public static MountState stateFor(BlockPos mountPos) {
        return STATES.get(mountPos.asLong());
    }

    /** True when the stabilizer for this mount has an unsent target/active change. */
    public static boolean consumeDirty(BlockPos mountPos) {
        MountState state = STATES.get(mountPos.asLong());
        if (state == null) {
            return false;
        }
        if (state.dirty) {
            state.dirty = false;
            return true;
        }
        return false;
    }

    /**
     * Called by mouse aim / scroll pitch adjustments so the hold-on-release
     * target follows the gun while other VSAW features drive it.
     */
    public static void notifyExternalInput(Level level, BlockPos mountPos) {
        if (level == null || level.isClientSide) {
            return;
        }
        MountState state = STATES.get(mountPos.asLong());
        if (state != null) {
            state.lastExternalInputGameTime = level.getGameTime();
        }
    }

    // ------------------------------------------------------------------
    // Control loop (called from the mount mixin, both sides)
    // ------------------------------------------------------------------

    /**
     * Extra pitch speed (CBC {@code pitchSpeed} units, deg/tick before the
     * mount's {@code sgn}) to add to the cannon's pitch advance this tick.
     * {@code baseSpeed} is CBC's own computed pitch speed for this tick (the
     * value the mixin intercepted), used for external-input bookkeeping.
     *
     * Guarded end-to-end: this runs inside the CBC mount tick, so any
     * unexpected failure must degrade to "no compensation" instead of
     * crashing the game.
     */
    public static float computeOffsetSpeed(Object mountBe, float cannonPitch, float baseSpeed) {
        float offset;
        try {
            offset = computeOffsetSpeedInner(mountBe, cannonPitch);
        } catch (RuntimeException | LinkageError e) {
            offset = 0.0f;
        }
        // Bookkeeping runs on every path so next tick's external-input
        // prediction stays valid even when compensation is inactive.
        if (mountBe instanceof BlockEntity be && be.getLevel() != null) {
            MountState state = STATES.get(be.getBlockPos().asLong());
            if (state != null) {
                state.prevCannonPitch = cannonPitch;
                state.prevBaseSpeed = baseSpeed;
                state.prevOffset = offset;
                state.bookkeepingValid = true;
            }
        }
        return offset;
    }

    private static float computeOffsetSpeedInner(Object mountBe, float cannonPitch) {
        if (!(mountBe instanceof BlockEntity be) || be.getLevel() == null) {
            return 0.0f;
        }
        Level level = be.getLevel();
        BlockPos mountPos = be.getBlockPos();

        boolean clientSide = level.isClientSide;
        BlockPos stabilizerPos = MOUNT_LINKS.get(mountPos.asLong());
        if (!clientSide && stabilizerPos == null) {
            return 0.0f;
        }
        if (clientSide && ClientStabilizerState.get(mountPos) == null) {
            return 0.0f; // Most mounts exit here: no stabilizer, single map lookup.
        }
        if (!CommonConfig.stabilizerEnabled()) {
            return 0.0f;
        }

        Object ship = StabilizerMath.shipManaging(level, mountPos);
        if (ship == null) {
            return 0.0f;
        }

        DirectionHolder holder = DirectionHolder.of(be, level, mountPos);
        if (holder == null) {
            return 0.0f;
        }

        MountState state = STATES.computeIfAbsent(mountPos.asLong(), k -> new MountState());

        // --- Measure the current pose -----------------------------------
        Matrix4dc rotation = StabilizerMath.getTickShipToWorld(ship);
        if (rotation == null) {
            return 0.0f;
        }
        Matrix4dc prevRotation = StabilizerMath.getPrevTickShipToWorld(ship);
        Vec3 axisShip = StabilizerMath.pitchAxisShipLocal(holder.initialOrientation);
        float sgn = StabilizerMath.cbcPitchSign(holder.initialOrientation);

        Vec3 aimShip = CbcCompat.getAimDirection(level, mountPos, holder.initialOrientation, 1.0f, false)
                .orElse(null);
        if (aimShip == null || aimShip.lengthSqr() < 1.0e-6) {
            return 0.0f;
        }
        Vec3 aimWorld = StabilizerMath.transformDirection(rotation, aimShip);
        double elevDeg = StabilizerMath.elevationDeg(aimWorld);

        double jacobian = StabilizerMath.pitchToElevationJacobian(rotation, aimShip, axisShip, sgn,
                Math.toRadians(elevDeg));

        // --- Adopt the server's held target on the client ---------------
        if (clientSide) {
            ClientStabilizerState.Entry synced = ClientStabilizerState.get(mountPos);
            if (synced != null && synced.active()) {
                state.targetElevDeg = synced.targetElevDeg();
                state.targetValid = true;
            }
        }

        // --- Input detection: hold-on-release ---------------------------
        // Local detection: did the pitch advance match what we predicted from
        // last tick's (base speed + stabilizer offset)? A mismatch means an
        // external writer moved the gun — mouse aim, scroll step, CBC seat
        // drag, or a block-entity sync replacing the pitch wholesale. This
        // works identically on server and client with no packet lag.
        boolean externalStep = false;
        if (state.bookkeepingValid) {
            float actualDelta = cannonPitch - state.prevCannonPitch;
            float predictedDelta = (state.prevBaseSpeed + state.prevOffset) * sgn;
            if (Math.abs(actualDelta - predictedDelta) > EXTERNAL_STEP_THRESHOLD_DEG) {
                externalStep = true;
            }
        }
        if (externalStep) {
            state.lastExternalInputGameTime = level.getGameTime();
        }

        boolean inputActive = externalStep || isShaftDriving(be) || isExternalInputRecent(level, state);
        boolean wasInputActive = state.inputActive;
        state.inputActive = inputActive;
        if (inputActive) {
            state.targetElevDeg = elevDeg;
            state.targetValid = true;
            state.integral = 0.0;
            if (clientSide) {
                ClientStabilizerState.set(mountPos, true, elevDeg, level.getGameTime());
            }
        } else {
            if (!state.targetValid) {
                state.targetElevDeg = elevDeg;
                state.targetValid = true;
                state.dirty = true;
                if (clientSide) {
                    ClientStabilizerState.set(mountPos, true, elevDeg, level.getGameTime());
                }
            } else if (wasInputActive) {
                // Input just stopped: the target is frozen now, publish it.
                state.dirty = true;
            }
        }

        if (Math.abs(jacobian) < 1.0e-3) {
            // Pitch axis cannot influence world elevation here (gimbal lock);
            // freeze the target so the gun does not wind up.
            state.targetElevDeg = elevDeg;
            state.integral = 0.0;
            return 0.0f;
        }

        // --- Feedforward: cancel this tick's ship-induced elevation drift
        double elevRateDegPerTick = StabilizerMath.elevationRatePerTick(ship, rotation, prevRotation, aimShip, aimWorld);
        if (clientSide) {
            // Client ship transforms arrive in bursts, so the raw rate
            // alternates zero/double; low-pass it to keep the injected speed
            // steady between sync packets.
            state.smoothedElevRatePerTick += (elevRateDegPerTick - state.smoothedElevRatePerTick)
                    * CLIENT_RATE_SMOOTHING;
            elevRateDegPerTick = state.smoothedElevRatePerTick;
        }

        // --- PI feedback on the held elevation --------------------------
        double errorDeg = state.targetElevDeg - elevDeg;
        if (Math.abs(errorDeg) > CommonConfig.stabilizerRecaptureThresholdDeg()) {
            // Error beyond anything the servo should correct: an external
            // writer is slowly slewing the gun (per-tick steps under the
            // detection threshold) or the gun rails at a mechanical limit.
            // Follow it instead of fighting: re-capture the held elevation.
            state.targetElevDeg = elevDeg;
            state.integral = 0.0;
            errorDeg = 0.0;
        }
        if (Math.abs(errorDeg) < CommonConfig.stabilizerDeadZoneDeg()) {
            errorDeg = 0.0;
        }
        state.integral += errorDeg;
        double integralLimit = CommonConfig.stabilizerIntegralLimit();
        state.integral = Math.max(-integralLimit, Math.min(integralLimit, state.integral));

        double correctionDeg = CommonConfig.stabilizerProportionalGain() * errorDeg
                + CommonConfig.stabilizerIntegralGain() * state.integral
                - CommonConfig.stabilizerFeedforwardGain() * elevRateDegPerTick;

        // Convert the desired world-elevation correction into a ship-space
        // pitch change, then back into CBC pre-sign pitchSpeed units.
        double deltaPitch = correctionDeg / jacobian;
        float offset = (float) (deltaPitch / sgn);
        float maxRate = (float) CommonConfig.stabilizerMaxDegPerTick();
        return Math.max(-maxRate, Math.min(maxRate, offset));
    }

    public static boolean isShaftDriving(Object mountBe) {
        try {
            Object pitchInterface = CbcCompat.invokeNoArg(mountBe, "getPitchInterface");
            if (pitchInterface == null) {
                return false;
            }
            return Math.abs(CbcCompat.invokeFloatNoArg(pitchInterface, "getSpeed")) > 1.0e-4f;
        } catch (ReflectiveOperationException | LinkageError e) {
            return false;
        }
    }

    private static boolean isExternalInputRecent(Level level, MountState state) {
        return state.lastExternalInputGameTime != Long.MIN_VALUE
                && level.getGameTime() - state.lastExternalInputGameTime <= EXTERNAL_INPUT_MEMORY_TICKS;
    }

    /** Cached reflection result for the contraption's initial orientation. */
    private static final class DirectionHolder {
        final Direction initialOrientation;

        DirectionHolder(Direction initialOrientation) {
            this.initialOrientation = initialOrientation;
        }

        @Nullable
        static DirectionHolder of(Object mountBe, Level level, BlockPos mountPos) {
            try {
                Direction dir = CbcCompat.getInitialOrientationFromCannon(level, mountPos);
                return dir == null ? null : new DirectionHolder(dir);
            } catch (RuntimeException | LinkageError e) {
                return null;
            }
        }
    }

}
