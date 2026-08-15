package com.erika.vsanalogwarfare.vehiclesetup.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.lang.reflect.Method;

public final class VehicleSetupReflection {
    private VehicleSetupReflection() { }

    @Nullable
    public static Object findShip(Level level, BlockPos pos) {
        try {
            Class<?> utils = Class.forName("org.valkyrienskies.mod.common.VSGameUtilsKt");
            for (Method method : utils.getMethods()) {
                if (method.getName().equals("getShipObjectManagingPos") && method.getParameterCount() == 2
                        && method.getParameterTypes()[0].isInstance(level)
                        && method.getParameterTypes()[1].isInstance(pos)) {
                    return method.invoke(null, level, pos);
                }
            }
        } catch (ReflectiveOperationException | LinkageError ignored) { }
        return null;
    }

    @Nullable
    static ShipPosition shipPosition(Level level, BlockPos pos) {
        Object ship = findShip(level, pos);
        if (ship == null) return null;
        try {
            Object id = invoke(ship, "getId");
            Object box = invoke(ship, "getShipAABB");
            if (!(id instanceof Number number) || box == null) return null;
            return new ShipPosition(number.longValue(), pos.offset(-coordinate(box, "minX"),
                    -coordinate(box, "minY"), -coordinate(box, "minZ")));
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    @Nullable
    public static BlockPos positionOnShip(Object ship, BlockPos offset) {
        try {
            Object box = invoke(ship, "getShipAABB");
            return box == null ? null : new BlockPos(coordinate(box, "minX"), coordinate(box, "minY"),
                    coordinate(box, "minZ")).offset(offset);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    @Nullable
    static Object invokeStatic(Class<?> type, String name, Object... arguments) throws ReflectiveOperationException {
        Method method = findMethod(type, name, arguments);
        if (method == null) throw new NoSuchMethodException(type.getName() + "." + name);
        method.setAccessible(true);
        return method.invoke(null, arguments);
    }

    @Nullable
    static Object invoke(Object target, String name, Object... arguments) throws ReflectiveOperationException {
        Method method = findMethod(target.getClass(), name, arguments);
        if (method == null) throw new NoSuchMethodException(target.getClass().getName() + "." + name);
        method.setAccessible(true);
        return method.invoke(target, arguments);
    }

    @Nullable
    static Method findMethod(Class<?> type, String name, Object... arguments) {
        for (Method method : type.getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != arguments.length) continue;
            Class<?>[] parameters = method.getParameterTypes();
            boolean matches = true;
            for (int index = 0; index < parameters.length; index++) {
                if (arguments[index] != null && !wrap(parameters[index]).isInstance(arguments[index])) {
                    matches = false;
                    break;
                }
            }
            if (matches) return method;
        }
        return null;
    }

    private static int coordinate(Object box, String name) throws ReflectiveOperationException {
        Object value = box.getClass().getMethod(name).invoke(box);
        if (!(value instanceof Number number)) throw new ReflectiveOperationException("Invalid ship AABB coordinate");
        return number.intValue();
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == boolean.class) return Boolean.class;
        if (type == byte.class) return Byte.class;
        if (type == short.class) return Short.class;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        if (type == char.class) return Character.class;
        return Void.class;
    }

    record ShipPosition(long shipId, BlockPos offset) { }
}
