package com.erika.vsanalogwarfare.vehiclesetup.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import javax.annotation.Nullable;
import java.lang.reflect.Method;

public final class VehicleSetupReflection {
    private VehicleSetupReflection() { }

    @Nullable public static Object findShip(Level level, BlockPos pos) {
        return VsGameUtilsBridge.shipObjectManagingPos(level, pos);
    }

    @Nullable static ShipPosition shipPosition(Level level, BlockPos pos) {
        Object ship = findShip(level, pos);
        if (ship == null) return null;
        try {
            Object id = invoke(ship, "getId");
            Object box = invoke(ship, "getShipAABB");
            if (!(id instanceof Number number) || box == null) return null;
            return new ShipPosition(number.longValue(), pos.offset(-coordinate(box, "minX"), -coordinate(box, "minY"), -coordinate(box, "minZ")));
        } catch (ReflectiveOperationException ignored) { return null; }
    }

    @Nullable static Vec3 shipToWorldPosition(Object ship, Vec3 position) {
        try {
            Object matrix = invoke(ship, "getShipToWorld");
            if (matrix == null) return null;
            Vector3d destination = new Vector3d();
            Method transform = matrix.getClass().getMethod("transformPosition",
                    double.class, double.class, double.class, Vector3d.class);
            Object result = transform.invoke(matrix, position.x, position.y, position.z, destination);
            Vector3d transformed = result instanceof Vector3d vector ? vector : destination;
            return new Vec3(transformed.x, transformed.y, transformed.z);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    @Nullable public static BlockPos positionOnShip(Object ship, BlockPos offset) {
        try {
            Object box = invoke(ship, "getShipAABB");
            return box == null ? null : new BlockPos(coordinate(box, "minX"), coordinate(box, "minY"), coordinate(box, "minZ")).offset(offset);
        } catch (ReflectiveOperationException ignored) { return null; }
    }

    @Nullable static Object invokeStatic(Class<?> type, String name, Object... arguments) throws ReflectiveOperationException {
        Method method = findMethod(type, name, arguments);
        if (method == null) throw new NoSuchMethodException(type.getName() + "." + name);
        return method.invoke(null, arguments);
    }

    @Nullable public static Object invoke(Object target, String name, Object... arguments) throws ReflectiveOperationException {
        Method method = findMethod(target.getClass(), name, arguments);
        if (method == null) throw new NoSuchMethodException(target.getClass().getName() + "." + name);
        return method.invoke(target, arguments);
    }

    @Nullable static Object invokeDeclared(Object target, String name, Object... arguments)
            throws ReflectiveOperationException {
        for (Class<?> current = target.getClass(); current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (!method.getName().equals(name) || method.getParameterCount() != arguments.length) continue;
                Class<?>[] parameters = method.getParameterTypes();
                boolean matches = true;
                for (int i = 0; i < parameters.length; i++) {
                    if (arguments[i] != null && !wrap(parameters[i]).isInstance(arguments[i])) {
                        matches = false;
                        break;
                    }
                }
                if (!matches) continue;
                method.setAccessible(true);
                return method.invoke(target, arguments);
            }
        }
        throw new NoSuchMethodException(target.getClass().getName() + "." + name);
    }

    @Nullable static Method findMethod(Class<?> type, String name, Object... arguments) {
        for (Method method : type.getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != arguments.length) continue;
            Class<?>[] parameters = method.getParameterTypes();
            boolean matches = true;
            for (int i = 0; i < parameters.length; i++) if (arguments[i] != null && !wrap(parameters[i]).isInstance(arguments[i])) matches = false;
            if (matches) return method;
        }
        return null;
    }

    private static int coordinate(Object box, String name) throws ReflectiveOperationException {
        Object value = box.getClass().getMethod(name).invoke(box);
        if (!(value instanceof Number number)) throw new ReflectiveOperationException("Invalid ship bounding-box coordinate");
        return number.intValue();
    }
    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == long.class) return Long.class;
        if (type == float.class) return Float.class;
        if (type == int.class) return Integer.class;
        if (type == boolean.class) return Boolean.class;
        return type;
    }
    record ShipPosition(long shipId, BlockPos offset) { }
}
