package com.erika.vsanalogwarfare.stabilizer;

import com.erika.vsanalogwarfare.VSAnalogWarfare;
import com.erika.vsanalogwarfare.config.CommonConfig;
import com.erika.vsanalogwarfare.scope.compat.CbcCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4dc;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hold-on-release vertical stabilizer control loop.
 *
 * Runs inside {@code CannonMountBlockEntity.tick()} via the mixin injection at
 * the pitch {@code getAngularSpeed} call site, on both server and client. While
 * the player rotates the gun (shaft input, mouse aim, scroll adjustment) the
 * held world elevation continuously re-captures to the gun's current elevation,
 * so the stabilizer is transparent during input. The moment input stops, the
 * current world elevation is frozen as the target.
 *
 * The servo is a deadbeat <b>position</b> controller: every tick it computes
 * the exact relative cannon pitch that places the aim direction at the held
 * world elevation (one Newton step through the elevation/pitch Jacobian) and
 * commands that, slew-limited by {@code maxCompensationDegPerTick}. Because the
 * command is a position rather than a rate, the hold cannot degrade: a gun
 * railed against a mechanical limit keeps its original target and returns the
 * moment geometry allows, and there is no integral to wind up and no drift
 * rate to estimate.
 *
 * VS physics runs at 60 TPS (3 subticks per game tick) but the control loop
 * runs at the 20 TPS mount tick; per-frame smoothness comes from CBC's
 * {@code getPitchOffset} render extrapolation, which the mixin feeds with the
 * offset produced this tick (see {@link #renderOffsetFor}).
 */
public final class StabilizerController {

    /** How long (ticks) a scroll/mouse-aim adjustment counts as active input. */
    private static final int EXTERNAL_INPUT_MEMORY_TICKS = 2;

    /**
     * A per-tick pitch change deviating from the predicted advance (base speed
     * + previous stabilizer offset, after CBC's degree wrap and elevation-limit
     * clamp) by more than this many degrees may mean an external writer moved
     * the gun (mouse aim, scroll step, CBC seat drag, block entity sync).
     */
    private static final float EXTERNAL_STEP_THRESHOLD_DEG = 0.3f;

    /** Consecutive mismatched ticks required to declare external input; absorbs recoil kicks. */
    private static final int EXTERNAL_STEP_DEBOUNCE_TICKS = 2;

    /**
     * If the mount's pitch advance loop did not run for this many ticks (seat
     * gunner control or a multi-tick stall — the mixin is not invoked then),
     * the held target is re-captured once on resume instead of fighting
     * whoever drove the gun meanwhile.
     */
    private static final int SUSPEND_RECAPTURE_TICKS = 2;

    /** Reuse the last found ship for this many ticks when the position query blinks. */
    private static final int SHIP_LOOKUP_GRACE_TICKS = 5;

    /** Server: mount pos -> stabilizer pos. Client: mount pos -> synced stabilizer pos. */
    private static final Map<Long, BlockPos> MOUNT_LINKS = new ConcurrentHashMap<>();
    /** Per-mount servo state, one per side. */
    private static final Map<Long, MountState> STATES = new ConcurrentHashMap<>();
    /** Last-known ship per mount, for lookup-gap grace. */
    private static final Map<Long, CachedShip> SHIP_CACHE = new ConcurrentHashMap<>();
    /** Recent stall / seat-control evidence per mount (server only). */
    private static final Map<Long, SuspensionTracker> SUSPENSION = new ConcurrentHashMap<>();

    /** Cached {@code mountedContraption} fields, keyed by mount BE class. */
    private static final Map<Class<?>, Optional<Field>> CONTRAPTION_FIELDS = new ConcurrentHashMap<>();
    /** Cached {@code clientPitchDiff} fields (client), keyed by mount BE class. */
    private static final Map<Class<?>, Optional<Field>> CLIENT_DIFF_FIELDS = new ConcurrentHashMap<>();
    /** Cached elevation-limit accessors, keyed by {@code class#method}. */
    private static final Map<String, Method> LIMIT_METHODS = new ConcurrentHashMap<>();

    /** Last-known ship for the lookup-gap grace window. */
    private record CachedShip(Object ship, long gameTime) {
    }

    /** Recent stall / seat-control evidence, sampled by the stabilizer block entity. */
    public static final class SuspensionTracker {
        public long lastSeatControlGameTime = Long.MIN_VALUE;
        public long lastStallGameTime = Long.MIN_VALUE;
    }

    public static final class MountState {
        public double targetElevDeg;
        public boolean targetValid;
        public boolean inputActive;
        public boolean dirty;
        public long lastExternalInputGameTime = Long.MIN_VALUE;
        /** Bookkeeping for local external-input detection. */
        public float prevCannonPitch;
        public float prevBaseSpeed;
        public float prevOffset;
        public boolean bookkeepingValid;
        public int mismatchStreak;
        /** Last game tick the pitch advance path ran (suspension detection). */
        public long lastRunGameTime = Long.MIN_VALUE;
        /** Offset the tick path produced this tick; consumed by the render path. */
        public float renderOffset;

        void reset() {
            targetValid = false;
            dirty = true;
        }
    }

    private StabilizerController() {
    }

    // ------------------------------------------------------------------
    // Link registry (server side managed by StabilizerBlockEntity)
    // ------------------------------------------------------------------

    public static void onLinked(BlockPos stabilizerPos, BlockPos mountPos) {
        onLinked(stabilizerPos, mountPos, Double.NaN);
    }

    /**
     * Links and optionally restores a previously held target (from the
     * stabilizer's NBT anchor) so relinks and chunk reloads do not drift the
     * hold. {@code restoredTargetElevDeg} of NaN means "no anchor; capture".
     */
    public static void onLinked(BlockPos stabilizerPos, BlockPos mountPos, double restoredTargetElevDeg) {
        MOUNT_LINKS.put(mountPos.asLong(), stabilizerPos.immutable());
        MountState state = STATES.computeIfAbsent(mountPos.asLong(), k -> new MountState());
        if (!state.targetValid && Double.isFinite(restoredTargetElevDeg)) {
            state.targetElevDeg = restoredTargetElevDeg;
            state.targetValid = true;
            state.dirty = true;
        } else {
            state.reset();
        }
    }

    public static void onUnlinked(BlockPos mountPos) {
        MOUNT_LINKS.remove(mountPos.asLong());
        MountState state = STATES.remove(mountPos.asLong());
        if (state != null) {
            state.dirty = true;
        }
        SHIP_CACHE.remove(mountPos.asLong());
        SUSPENSION.remove(mountPos.asLong());
        ClientStabilizerState.clear(mountPos);
    }

    /**
     * Removes controller state for every mount linked to this stabilizer, for
     * the cases where the mount position can no longer be resolved but the
     * registration must not leak.
     */
    public static void forgetStabilizer(BlockPos stabilizerPos) {
        List<Long> mountKeys = new ArrayList<>();
        for (Map.Entry<Long, BlockPos> entry : MOUNT_LINKS.entrySet()) {
            if (entry.getValue().equals(stabilizerPos)) {
                mountKeys.add(entry.getKey());
            }
        }
        for (Long key : mountKeys) {
            onUnlinked(BlockPos.of(key));
        }
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
        // prediction and suspension detection stay valid even when
        // compensation is inactive.
        if (mountBe instanceof BlockEntity be && be.getLevel() != null) {
            MountState state = STATES.get(be.getBlockPos().asLong());
            if (state != null) {
                state.prevCannonPitch = cannonPitch;
                state.prevBaseSpeed = baseSpeed;
                state.prevOffset = offset;
                state.lastRunGameTime = be.getLevel().getGameTime();
                state.renderOffset = offset;
                state.bookkeepingValid = true;
            }
        }
        return offset;
    }

    /**
     * The offset the tick path produced this game tick, for CBC's client render
     * extrapolation: {@code getPitchOffset} re-derives the per-tick angular
     * speed from the shaft alone and would otherwise under-project every step
     * the stabilizer commands (visible as a 20 Hz snap in the zoomed scope).
     * Read-only; safe to call from the render thread path.
     */
    public static float renderOffsetFor(Object mountBe) {
        if (mountBe instanceof BlockEntity be) {
            MountState state = STATES.get(be.getBlockPos().asLong());
            if (state != null) {
                return state.renderOffset;
            }
        }
        return 0.0f;
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
            // The position-vs-AABB query blinks during violent ship motion;
            // ride through short gaps on the last known ship rather than
            // dropping the servo (a dropped servo lets the gun bounce free).
            CachedShip cached = SHIP_CACHE.get(mountPos.asLong());
            if (cached != null && level.getGameTime() - cached.gameTime() <= SHIP_LOOKUP_GRACE_TICKS) {
                ship = cached.ship();
            }
        }
        if (ship == null) {
            return 0.0f;
        }
        SHIP_CACHE.put(mountPos.asLong(), new CachedShip(ship, level.getGameTime()));

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

        // --- Suspension: the advance loop was paused (seat gunner, stall)
        // Only a seat gunner legitimately moves the gun while the loop is
        // paused; a physics stall moves nothing, so the held target survives
        // it instead of adopting the transient elevation.
        boolean suspended = state.bookkeepingValid
                && state.lastRunGameTime != Long.MIN_VALUE
                && level.getGameTime() - state.lastRunGameTime > SUSPEND_RECAPTURE_TICKS;
        boolean suspendRecapture = suspended && seatControlledDuringGap(level, mountPos, state.lastRunGameTime);
        if (suspendRecapture) {
            state.targetElevDeg = elevDeg;
            state.targetValid = true;
            state.dirty = true;
            state.renderOffset = 0.0f;
        }

        // --- Input detection: hold-on-release ---------------------------
        // Local detection: did the pitch advance match what we predicted from
        // last tick's (base speed + stabilizer offset), folded through CBC's
        // own degree wrap and elevation-limit clamp exactly like
        // CannonMountBlockEntity.tick does? A clamp-eaten advance (gun railed
        // against a limit) predicts to zero delta and is NOT external input.
        // A persistent mismatch means an external writer is moving the gun.
        boolean railedByLimit = false;
        if (state.bookkeepingValid) {
            float predicted = state.prevCannonPitch + (state.prevBaseSpeed + state.prevOffset) * sgn;
            predicted %= 360.0f;
            float[] limits = mountPitchLimits(be);
            if (limits != null) {
                float clamped = Math.max(-limits[0], Math.min(limits[1], predicted));
                railedByLimit = clamped != predicted;
                predicted = clamped;
            }
            float predictedDelta = predicted - state.prevCannonPitch;
            float actualDelta = cannonPitch - state.prevCannonPitch;
            if (Math.abs(actualDelta - predictedDelta) > EXTERNAL_STEP_THRESHOLD_DEG) {
                state.mismatchStreak++;
            } else {
                state.mismatchStreak = 0;
            }
        } else {
            state.mismatchStreak = 0;
        }
        if (clientSide) {
            Field diffField = clientPitchDiffField(be.getClass());
            if (diffField != null) {
                try {
                    if (Math.abs(diffField.getFloat(be)) > 1.0e-3f) {
                        // A block-entity sync yanked the client pitch to the
                        // server's value; that is CBC's own correction, not
                        // player input.
                        state.mismatchStreak = 0;
                    }
                } catch (RuntimeException | LinkageError | ReflectiveOperationException ignored) {
                    // fall through: evaluate this tick normally
                }
            }
        }
        boolean externalStep = state.mismatchStreak >= EXTERNAL_STEP_DEBOUNCE_TICKS;
        if (externalStep) {
            state.lastExternalInputGameTime = level.getGameTime();
        }

        boolean inputActive = externalStep || isShaftDriving(be) || isExternalInputRecent(level, state);
        boolean wasInputActive = state.inputActive;
        state.inputActive = inputActive;
        if (inputActive) {
            state.targetElevDeg = elevDeg;
            state.targetValid = true;
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
            // hold output and keep the target so the servo resumes unchanged.
            return 0.0f;
        }

        // --- Position servo: command the exact pitch that achieves the target
        double errorDeg = state.targetElevDeg - elevDeg;
        if (Math.abs(errorDeg) < CommonConfig.stabilizerDeadZoneDeg()) {
            errorDeg = 0.0;
        }

        // Convert the desired world-elevation correction into a ship-space
        // pitch change, then back into CBC pre-sign pitchSpeed units; the
        // slew limit keeps large corrections looking like a deliberate slew.
        double deltaPitch = errorDeg / jacobian;
        float offset = (float) (deltaPitch / sgn);
        float maxRate = (float) CommonConfig.stabilizerMaxDegPerTick();
        offset = Math.max(-maxRate, Math.min(maxRate, offset));

        if (!clientSide && CommonConfig.stabilizerDebug() && level.getGameTime() % 20 == 0) {
            VSAnalogWarfare.LOGGER.info(
                    "[VSAW Stabilizer] {} elev={} target={} offset={} input={} ext={} railed={} suspend={}",
                    mountPos.toShortString(),
                    String.format("%.2f", elevDeg),
                    String.format("%.2f", state.targetElevDeg),
                    String.format("%.3f", offset),
                    inputActive, externalStep, railedByLimit, suspendRecapture);
        }
        return offset;
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

    // ------------------------------------------------------------------
    // Suspension context (stall vs seat gunner) and sync-yank immunity
    // ------------------------------------------------------------------

    private static boolean seatControlledDuringGap(Level level, BlockPos mountPos, long gapStartTime) {
        SuspensionTracker tracker = SUSPENSION.get(mountPos.asLong());
        if (tracker == null || tracker.lastSeatControlGameTime == Long.MIN_VALUE) {
            return false; // no evidence of a gunner: keep the target (stall-safe default)
        }
        long now = level.getGameTime();
        return now - tracker.lastSeatControlGameTime <= (now - gapStartTime) + SUSPEND_RECAPTURE_TICKS;
    }

    /**
     * Called every tick by the stabilizer block entity: records whether the
     * mount's contraption is currently stalled or seat-controlled, so a
     * suspension of the advance loop can be attributed to a cause.
     */
    public static void sampleMountSuspension(Level level, BlockPos mountPos) {
        try {
            BlockEntity be = level.getBlockEntity(mountPos);
            if (be == null) {
                return;
            }
            Field field = contraptionField(be.getClass());
            if (field == null) {
                return;
            }
            Object contraption = field.get(be);
            if (contraption == null) {
                return;
            }
            Class<?> cls = contraption.getClass();
            SuspensionTracker tracker = SUSPENSION.computeIfAbsent(mountPos.asLong(), k -> new SuspensionTracker());
            Method stalled = methodFor(cls, "isStalled");
            if (stalled != null && stalled.invoke(contraption) instanceof Boolean b && b) {
                tracker.lastStallGameTime = level.getGameTime();
            }
            Method seat = methodForSingleArg(cls, "canBeTurnedByController");
            if (seat != null && seat.invoke(contraption, be) instanceof Boolean b && !b) {
                tracker.lastSeatControlGameTime = level.getGameTime();
            }
        } catch (RuntimeException | ReflectiveOperationException | LinkageError ignored) {
            // sampling is best-effort
        }
    }

    @Nullable
    private static Method methodForSingleArg(Class<?> cls, String name) {
        String key = cls.getName() + '#' + name + "/1";
        Method cached = LIMIT_METHODS.get(key);
        if (cached != null) {
            return cached;
        }
        for (Method m : cls.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == 1) {
                LIMIT_METHODS.put(key, m);
                return m;
            }
        }
        return null;
    }

    @Nullable
    private static Field clientPitchDiffField(Class<?> mountClass) {
        Optional<Field> cached = CLIENT_DIFF_FIELDS.get(mountClass);
        if (cached == null) {
            cached = Optional.ofNullable(findDeclaredField(mountClass, "clientPitchDiff"));
            CLIENT_DIFF_FIELDS.put(mountClass, cached);
        }
        return cached.orElse(null);
    }

    // ------------------------------------------------------------------
    // Elevation limits (reflection; per-concrete-class caches)
    // ------------------------------------------------------------------

    /**
     * {@code [maxDepress, maxElevate]} of the mounted cannon in CBC pitch
     * degrees, or null when unavailable (fall back to an unclamped prediction).
     * Read via reflection so the compile classpath stays free of CBC classes.
     * Methods are cached per concrete class: server and client ship different
     * classes in one JVM (the integrated-server lesson from the link crash).
     */
    @Nullable
    private static float[] mountPitchLimits(Object mountBe) {
        try {
            if (!(mountBe instanceof BlockEntity be)) {
                return null;
            }
            Field field = contraptionField(be.getClass());
            if (field == null) {
                return null;
            }
            Object contraption = field.get(be);
            if (contraption == null) {
                return null;
            }
            Class<?> cls = contraption.getClass();
            Method depression = methodFor(cls, "maximumDepression");
            Method elevation = methodFor(cls, "maximumElevation");
            if (depression == null || elevation == null) {
                return null;
            }
            return new float[] {(float) depression.invoke(contraption), (float) elevation.invoke(contraption)};
        } catch (RuntimeException | ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }

    @Nullable
    private static Field contraptionField(Class<?> mountClass) {
        Optional<Field> cached = CONTRAPTION_FIELDS.get(mountClass);
        if (cached == null) {
            cached = Optional.ofNullable(findDeclaredField(mountClass, "mountedContraption"));
            CONTRAPTION_FIELDS.put(mountClass, cached);
        }
        return cached.orElse(null);
    }

    @Nullable
    private static Field findDeclaredField(Class<?> cls, String name) {
        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
                // walk up to the declaring superclass
            } catch (RuntimeException | LinkageError e) {
                return null;
            }
        }
        return null;
    }

    @Nullable
    private static Method methodFor(Class<?> cls, String name) {
        String key = cls.getName() + '#' + name;
        Method cached = LIMIT_METHODS.get(key);
        if (cached != null) {
            return cached;
        }
        try {
            Method m = cls.getMethod(name);
            LIMIT_METHODS.put(key, m);
            return m;
        } catch (RuntimeException | ReflectiveOperationException | LinkageError e) {
            return null;
        }
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
