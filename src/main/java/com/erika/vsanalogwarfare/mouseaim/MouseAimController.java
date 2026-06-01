package com.erika.vsanalogwarfare.mouseaim;

import com.erika.vsanalogwarfare.scope.compat.CbcCompat;
import com.erika.vsanalogwarfare.scope.compat.VsCompat;
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

        Vec3 localTarget = VsCompat.worldToShipDirection(level, mountPos, targetWorldDirection);
        AimAngles desired = AimAngles.fromDirection(localTarget);

        float currentYaw = readFloat(mount, "getYawOffset", 1.0f).orElse(desired.yaw());
        float currentPitch = readFloat(mount, "getPitchOffset", 1.0f).orElse(desired.pitch());
        float yawStep = clampAngleStep(shortestAngleDiff(currentYaw, desired.yaw()), maxDegreesPerTick);
        float pitchStep = clampAngleStep(desired.pitch() - currentPitch, maxDegreesPerTick);

        float nextYaw = wrapDegrees(currentYaw + yawStep);
        float nextPitch = currentPitch + pitchStep;
        nextPitch = clampPitchToMount(mount, nextPitch);

        writeYawPitch(mount, nextYaw, nextPitch);
        callNoArg(mount, "applyRotation");
        callNoArg(mount, "sendData");
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
}
