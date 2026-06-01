package com.lauya.vsanalogwarfare.scope.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.lang.reflect.Method;
import java.util.Optional;

public final class VsCompat {
    private static Class<?> vsGameUtilsClass;
    private static Method getShipManagingPos;
    private static Method getRenderTransform;

    private VsCompat() {
    }

    public static Optional<Long> findShipId(Level level, BlockPos pos) {
        Object ship = findShip(level, pos);
        if (ship == null) {
            return Optional.empty();
        }
        try {
            Object id = ship.getClass().getMethod("getId").invoke(ship);
            if (id instanceof Number number) {
                return Optional.of(number.longValue());
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return Optional.empty();
    }

    public static Vec3 shipToWorldPosition(Level level, BlockPos anchorPos, Vec3 localPosition) {
        Object ship = findShip(level, anchorPos);
        if (ship == null) {
            return localPosition;
        }
        Vector3d transformed = invokeMatrixTransform(ship, localPosition, true);
        return transformed == null ? localPosition : new Vec3(transformed.x, transformed.y, transformed.z);
    }

    public static Vec3 shipToWorldDirection(Level level, BlockPos anchorPos, Vec3 localDirection) {
        Object ship = findShip(level, anchorPos);
        if (ship == null) {
            return localDirection.normalize();
        }
        Vector3d transformed = invokeMatrixTransform(ship, localDirection, false);
        if (transformed == null) {
            return localDirection.normalize();
        }
        return new Vec3(transformed.x, transformed.y, transformed.z).normalize();
    }

    public static Vec3 worldToShipDirection(Level level, BlockPos anchorPos, Vec3 worldDirection) {
        Object ship = findShip(level, anchorPos);
        if (ship == null) {
            return worldDirection.normalize();
        }
        Vector3d transformed = invokeInverseMatrixTransform(ship, worldDirection, false);
        if (transformed == null) {
            return worldDirection.normalize();
        }
        return new Vec3(transformed.x, transformed.y, transformed.z).normalize();
    }

    private static Object findShip(Level level, BlockPos pos) {
        try {
            if (vsGameUtilsClass == null) {
                vsGameUtilsClass = Class.forName("org.valkyrienskies.mod.common.VSGameUtilsKt");
            }
            if (getShipManagingPos == null) {
                getShipManagingPos = vsGameUtilsClass.getMethod("getShipManagingPos", Level.class, BlockPos.class);
            }
            return getShipManagingPos.invoke(null, level, pos);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private static Object getRenderTransformFromUtils(Object ship) throws ReflectiveOperationException {
        // VS2.3 exposes render transform as a Kotlin extension function compiled onto VSGameUtilsKt,
        // not necessarily as a member method on the ship object.
        if (vsGameUtilsClass == null) {
            vsGameUtilsClass = Class.forName("org.valkyrienskies.mod.common.VSGameUtilsKt");
        }
        if (getRenderTransform == null) {
            // Don't bind to an exact ship interface class here; use a 1-arg overload by name.
            for (Method m : vsGameUtilsClass.getMethods()) {
                if (m.getName().equals("getRenderTransform") && m.getParameterCount() == 1) {
                    getRenderTransform = m;
                    break;
                }
            }
        }
        if (getRenderTransform == null) {
            throw new NoSuchMethodException("VSGameUtilsKt.getRenderTransform(<ship>) not found");
        }
        return getRenderTransform.invoke(null, ship);
    }

    private static Vector3d invokeMatrixTransform(Object ship, Vec3 vector, boolean position) {
        try {
            Object matrix = getShipToWorldMatrix(ship);
            if (matrix == null) {
                return null;
            }
            Vector3d dest = new Vector3d();
            Method transform = matrix.getClass().getMethod(
                    position ? "transformPosition" : "transformDirection",
                    double.class, double.class, double.class, Vector3d.class);
            Object result = transform.invoke(matrix, vector.x, vector.y, vector.z, dest);
            return result instanceof Vector3d v ? v : dest;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private static Vector3d invokeInverseMatrixTransform(Object ship, Vec3 vector, boolean position) {
        try {
            Object matrix = getWorldToShipMatrix(ship);
            if (matrix == null) {
                return null;
            }
            Vector3d dest = new Vector3d();
            Method transform = matrix.getClass().getMethod(
                    position ? "transformPosition" : "transformDirection",
                    double.class, double.class, double.class, Vector3d.class);
            Object result = transform.invoke(matrix, vector.x, vector.y, vector.z, dest);
            return result instanceof Vector3d v ? v : dest;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private static Object getShipToWorldMatrix(Object ship) throws ReflectiveOperationException {
        try {
            Object renderTransform = getRenderTransformFromUtils(ship);
            return getShipToWorldMatrixFromTransform(renderTransform);
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
        try {
            Object renderTransform = ship.getClass().getMethod("getRenderTransform").invoke(ship);
            return getShipToWorldMatrixFromTransform(renderTransform);
        } catch (NoSuchMethodException ignored) {
        }
        try {
            return ship.getClass().getMethod("getShipToWorld").invoke(ship);
        } catch (NoSuchMethodException ignored) {
        }
        Object transform = ship.getClass().getMethod("getTransform").invoke(ship);
        return getShipToWorldMatrixFromTransform(transform);
    }

    private static Object getShipToWorldMatrixFromTransform(Object transform) throws ReflectiveOperationException {
        try {
            return transform.getClass().getMethod("getShipToWorld").invoke(transform);
        } catch (NoSuchMethodException ignored) {
            return transform.getClass().getMethod("getShipToWorldMatrix").invoke(transform);
        }
    }

    private static Object getWorldToShipMatrix(Object ship) throws ReflectiveOperationException {
        try {
            Object renderTransform = getRenderTransformFromUtils(ship);
            return getWorldToShipMatrixFromTransform(renderTransform);
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
        try {
            Object renderTransform = ship.getClass().getMethod("getRenderTransform").invoke(ship);
            return getWorldToShipMatrixFromTransform(renderTransform);
        } catch (NoSuchMethodException ignored) {
        }
        try {
            return ship.getClass().getMethod("getWorldToShip").invoke(ship);
        } catch (NoSuchMethodException ignored) {
        }
        Object transform = ship.getClass().getMethod("getTransform").invoke(ship);
        return getWorldToShipMatrixFromTransform(transform);
    }

    private static Object getWorldToShipMatrixFromTransform(Object transform) throws ReflectiveOperationException {
        try {
            return transform.getClass().getMethod("getWorldToShip").invoke(transform);
        } catch (NoSuchMethodException ignored) {
            return transform.getClass().getMethod("getWorldToShipMatrix").invoke(transform);
        }
    }
}
