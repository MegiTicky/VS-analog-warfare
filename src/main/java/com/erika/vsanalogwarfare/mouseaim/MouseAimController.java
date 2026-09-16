package com.erika.vsanalogwarfare.mouseaim;

import com.erika.vsanalogwarfare.config.CommonConfig;
import com.erika.vsanalogwarfare.scope.compat.CbcCompat;
import com.erika.vsanalogwarfare.scope.compat.VsCompat;
import com.erika.vsanalogwarfare.stabilizer.StabilizerController;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;

public final class MouseAimController {
    private static final Direction[] FACE_PRIORITY = new Direction[]{
            Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP, Direction.DOWN
    };

    /**
     * Sanity cap on per-tick hull-motion compensation, in degrees: no real
     * hull rotates this fast, while a ship-lookup glitch (the world→ship
     * transform silently falls back to identity, and that query blinks during
     * violent motion) reads as a huge instantaneous frame rotation. A larger
     * "rotation" skips the compensation for the tick instead of snapping the
     * gun.
     */
    private static final float HULL_COMP_MAX_DEG_PER_TICK = 10.0f;

    private MouseAimController() {
    }

    public static Optional<BlockPos> findAdjacentMount(Level level, BlockPos controllerPos) {
        for (Direction direction : FACE_PRIORITY) {
            BlockPos candidate = controllerPos.relative(direction);
            if (!level.isLoaded(candidate)) {
                continue;
            }
            if (CbcCompat.isCannonMount(level.getBlockEntity(candidate))) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    public static Optional<MouseAimBlockEntity> findControllerForMount(Level level, BlockPos mountPos) {
        for (Direction direction : FACE_PRIORITY) {
            BlockPos candidate = mountPos.relative(direction);
            if (!level.isLoaded(candidate)) {
                continue;
            }
            if (level.getBlockEntity(candidate) instanceof MouseAimBlockEntity mouseAim && mouseAim.controls(mountPos)) {
                return Optional.of(mouseAim);
            }
        }
        return Optional.empty();
    }

    public static void tick(MouseAimBlockEntity controller, BlockPos mountPos, Vec3 targetWorldDirection, double maxDegreesPerTick) {
        Level level = controller.getLevel();
        if (level == null || maxDegreesPerTick <= 0.0) {
            return;
        }
        BlockEntity mount = level.getBlockEntity(mountPos);
        if (!CbcCompat.isCannonMount(mount)) {
            controller.clearTarget();
            return;
        }

        AimAngles desired = AimAngles.fromDirection(toMountLocal(level, mountPos, targetWorldDirection));

        float currentYaw = readFloat(mount, "getYawOffset", 1.0f).orElse(desired.yaw());
        float currentPitch = readFloat(mount, "getPitchOffset", 1.0f).orElse(desired.pitch());
        float[] steps = aimSteps(controller, level, mountPos, targetWorldDirection, desired,
                currentYaw, currentPitch, maxDegreesPerTick);

        float nextYaw = wrapDegrees(currentYaw + steps[0]);
        float nextPitch = hardSetPitch(level, mountPos)
                ? clampPitchToMount(mount, desired.pitch())
                : clampPitchToMount(mount, currentPitch + steps[1]);

        writeYawPitch(mount, nextYaw, nextPitch);
        callNoArg(mount, "applyRotation");
        callNoArg(mount, "sendData");
        com.erika.vsanalogwarfare.stabilizer.StabilizerController.notifyExternalInput(level, mountPos);
    }

    /**
     * Turret mode: slew only the mount's pitch and hold its yaw untouched —
     * yaw authority belongs to the physics-bearing-driven turret structure.
     */
    public static void tickTurretPitch(MouseAimBlockEntity controller, BlockPos mountPos, Vec3 targetWorldDirection, double maxDegreesPerTick) {
        Level level = controller.getLevel();
        if (level == null || maxDegreesPerTick <= 0.0) {
            return;
        }
        BlockEntity mount = level.getBlockEntity(mountPos);
        if (!CbcCompat.isCannonMount(mount)) {
            controller.clearTarget();
            return;
        }

        AimAngles desired = AimAngles.fromDirection(toMountLocal(level, mountPos, targetWorldDirection));

        float currentYaw = readFloat(mount, "getYawOffset", 1.0f).orElse(desired.yaw());
        float currentPitch = readFloat(mount, "getPitchOffset", 1.0f).orElse(desired.pitch());
        float[] steps = aimSteps(controller, level, mountPos, targetWorldDirection, desired,
                currentYaw, currentPitch, maxDegreesPerTick);

        float nextPitch = hardSetPitch(level, mountPos)
                ? clampPitchToMount(mount, desired.pitch())
                : clampPitchToMount(mount, currentPitch + steps[1]);

        writeYawPitch(mount, currentYaw, nextPitch);
        callNoArg(mount, "applyRotation");
        callNoArg(mount, "sendData");
        com.erika.vsanalogwarfare.stabilizer.StabilizerController.notifyExternalInput(level, mountPos);
    }

    /**
     * A linked, enabled gyro stabilizer upgrades mouse aim to an absolute
     * elevation hold: the mount pitch is written directly from the aim
     * direction each tick instead of chasing, so the bore elevation pins to
     * the free-look direction with no chase lag. The gyro's own servo stays
     * suppressed while aiming ({@code notifyExternalInput}), so the two
     * never write the same axis in the same tick.
     */
    private static boolean hardSetPitch(Level level, BlockPos mountPos) {
        return !level.isClientSide
                && CommonConfig.stabilizerEnabled()
                && StabilizerController.linkedStabilizer(mountPos) != null;
    }

    /**
     * Split the tick's aim correction into hull-motion compensation and
     * player-aimed slew. The mount-local angle of a fixed world direction
     * changes each tick either because the mount's frame rotated (ship
     * pitch/roll) or because the target direction itself moved (mouse input).
     * The frame share is the stabilization mouse aim must provide while it
     * owns the axis — the gyro block stays suppressed by
     * {@code notifyExternalInput} — so it is applied at full speed; the mouse
     * share keeps the input-scaled slew limit so shaft speed still scales aim
     * speed.
     *
     * <p>The previous tick's target is re-mapped through the current frame to
     * isolate the frame rotation. Returns {@code {yawStep, pitchStep}}.
     */
    private static float[] aimSteps(MouseAimBlockEntity controller, Level level, BlockPos mountPos,
                                    Vec3 targetWorldDirection, AimAngles desired,
                                    float currentYaw, float currentPitch, double maxDegreesPerTick) {
        float hullYaw = 0.0f;
        float hullPitch = 0.0f;
        Vec3 prevTarget = controller.prevAimTarget();
        if (prevTarget != null && mountPos.equals(controller.prevAimMountPos())) {
            AimAngles prevNow = AimAngles.fromDirection(toMountLocal(level, mountPos, prevTarget));
            float rawYaw = wrapDegrees(prevNow.yaw() - controller.prevDesiredYaw());
            float rawPitch = prevNow.pitch() - controller.prevDesiredPitch();
            if (Math.abs(rawYaw) <= HULL_COMP_MAX_DEG_PER_TICK && Math.abs(rawPitch) <= HULL_COMP_MAX_DEG_PER_TICK) {
                hullYaw = rawYaw;
                hullPitch = rawPitch;
            }
        }
        controller.storePrevAim(targetWorldDirection, mountPos, desired.yaw(), desired.pitch());
        // Wrap the mouse share so a large aim error still takes the short way
        // around once the hull share is subtracted out of the total.
        return new float[]{
                hullYaw + clampAngleStep(wrapDegrees(shortestAngleDiff(currentYaw, desired.yaw()) - hullYaw), maxDegreesPerTick),
                hullPitch + clampAngleStep(desired.pitch() - currentPitch - hullPitch, maxDegreesPerTick)};
    }

    private static Vec3 toMountLocal(Level level, BlockPos mountPos, Vec3 targetWorldDirection) {
        // The packet direction is always world-frame (the client seeds its
        // free-look angles from the world-frame sight direction), so it must
        // be expressed in the mount ship's frame no matter where the player
        // sits — feeding it through unchanged made the aim rotate along with
        // the ship/turret the player is mounted on.
        return VsCompat.worldToShipDirection(level, mountPos, targetWorldDirection);
    }

    /** Set a linked cannon to the requested world-space bore direction. */
    public static void setAimDirection(Level level, BlockPos mountPos, Vec3 targetWorldDirection) {
        if (level == null || mountPos == null || targetWorldDirection == null
                || targetWorldDirection.lengthSqr() < 1.0e-8) return;
        BlockEntity mount = level.getBlockEntity(mountPos);
        if (!CbcCompat.isCannonMount(mount)) return;
        Vec3 localTarget = VsCompat.worldToShipDirection(level, mountPos, targetWorldDirection.normalize());
        AimAngles desired = AimAngles.fromDirection(localTarget);
        float pitch = clampPitchToMount(mount, desired.pitch());
        writeYawPitch(mount, desired.yaw(), pitch);
        callNoArg(mount, "applyRotation");
        callNoArg(mount, "sendData");
        com.erika.vsanalogwarfare.stabilizer.StabilizerController.notifyExternalInput(level, mountPos);
    }

    private static float clampPitchToMount(Object mount, float pitch) {
        Object contraption = callNoArgResult(mount, "getContraption");
        if (contraption == null) {
            return Mth.clamp(pitch, -89.0f, 89.0f);
        }
        float maxDepress = readFloat(contraption, "maximumDepression").orElse(89.0f);
        float maxElevate = readFloat(contraption, "maximumElevation").orElse(89.0f);
        return Mth.clamp(pitch, -maxDepress, maxElevate);
    }

    private static void writeYawPitch(Object mount, float yaw, float pitch) {
        boolean wroteYaw = callFloatSetter(mount, "setYaw", yaw);
        boolean wrotePitch = callFloatSetter(mount, "setPitch", pitch);
        if (!wroteYaw) {
            writeFloatField(mount, "cannonYaw", yaw);
        }
        if (!wrotePitch) {
            writeFloatField(mount, "cannonPitch", pitch);
        }
    }

    private static Optional<Float> readFloat(Object target, String method, float arg) {
        try {
            Method m = target.getClass().getMethod(method, float.class);
            Object result = m.invoke(target, arg);
            return result instanceof Number number ? Optional.of(number.floatValue()) : Optional.empty();
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return Optional.empty();
        }
    }

    private static Optional<Float> readFloat(Object target, String method) {
        try {
            Method m = target.getClass().getMethod(method);
            Object result = m.invoke(target);
            return result instanceof Number number ? Optional.of(number.floatValue()) : Optional.empty();
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return Optional.empty();
        }
    }

    private static boolean callFloatSetter(Object target, String method, float value) {
        try {
            Method m = target.getClass().getMethod(method, float.class);
            m.invoke(target, value);
            return true;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    private static void callNoArg(Object target, String method) {
        callNoArgResult(target, method);
    }

    private static Object callNoArgResult(Object target, String method) {
        try {
            Method m = findNoArgMethod(target.getClass(), method);
            if (m == null) {
                return null;
            }
            m.setAccessible(true);
            return m.invoke(target);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private static Method findNoArgMethod(Class<?> type, String method) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                return c.getDeclaredMethod(method);
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    private static void writeFloatField(Object target, String field, float value) {
        try {
            Field f = findField(target.getClass(), field);
            if (f != null) {
                f.setAccessible(true);
                f.setFloat(target, value);
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
    }

    private static Field findField(Class<?> type, String field) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField(field);
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }

    private static float clampAngleStep(float value, double maxAbs) {
        return (float) Mth.clamp(value, -maxAbs, maxAbs);
    }

    private static float shortestAngleDiff(float from, float to) {
        return wrapDegrees(to - from);
    }

    private static float wrapDegrees(float value) {
        value %= 360.0f;
        if (value >= 180.0f) value -= 360.0f;
        if (value < -180.0f) value += 360.0f;
        return value;
    }

    private record AimAngles(float yaw, float pitch) {
        static AimAngles fromDirection(Vec3 direction) {
            Vec3 d = direction.normalize();
            float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
            // CBC cannon mount pitch uses positive values for elevation and negative values
            // for depression.  The incoming target direction is a world/ship direction where
            // positive Y means "aim up", so keep that sign when converting to mount pitch.
            float pitch = (float) Math.toDegrees(Math.asin(Mth.clamp(d.y, -1.0, 1.0)));
            return new AimAngles(wrapDegrees(yaw), pitch);
        }
    }

    public static void adjustPitch(Level level, BlockPos mountPos, float deltaPitch) {
        BlockEntity mount = level.getBlockEntity(mountPos);
        if (!CbcCompat.isCannonMount(mount)) {
            return;
        }

        // Read the current position of the mount
        float currentYaw = readFloat(mount, "getYawOffset", 1.0f).orElse(0.0f);
        float currentPitch = readFloat(mount, "getPitchOffset", 1.0f).orElse(0.0f);

        // Add the delta and clamp it so the gun doesn't break its physical limits
        float nextPitch = clampPitchToMount(mount, currentPitch + deltaPitch);

        // Apply the new position
        writeYawPitch(mount, currentYaw, nextPitch);
        callNoArg(mount, "applyRotation");
        callNoArg(mount, "sendData");
        com.erika.vsanalogwarfare.stabilizer.StabilizerController.notifyExternalInput(level, mountPos);
    }
}
