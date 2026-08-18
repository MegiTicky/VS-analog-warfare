package com.erika.vsanalogwarfare.vehiclesetup.compat;

import com.erika.vsanalogwarfare.VSAnalogWarfare;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Optional VMod bridge for persistent ship-to-ground collision ownership. */
public final class GroundCollisionCompat {
    private static final String VMOD_MANAGER = "net.spaceeye.vmod.constraintsManaging.ConstraintManager";
    private static final Map<String, String> LAST_FAILURES = new HashMap<>();
    private static final Set<String> ACTIVE_SHIPS = new HashSet<>();
    private GroundCollisionCompat() { }

    public static long findShipId(Level level, BlockPos pos) {
        Object ship = VehicleSetupReflection.findShip(level, pos);
        if (ship == null) return -1L;
        try {
            Object id = VehicleSetupReflection.invoke(ship, "getId");
            return id instanceof Number number ? number.longValue() : -1L;
        } catch (ReflectiveOperationException ignored) {
            return -1L;
        }
    }

    public static void reportNoShip(ServerLevel level, BlockPos pos) {
        String key = level.dimension().location() + "/" + pos.asLong();
        String message = "block at " + pos.toShortString() + " is not part of a ship";
        if (!message.equals(LAST_FAILURES.put(key, message))) {
            VSAnalogWarfare.LOGGER.warn("[VSAW] Ground Collision Disabler: {}", message);
        }
    }

    public static boolean disableGroundCollision(ServerLevel level, long shipId) {
        try {
            Object companion = managerCompanion();
            Object manager = managerInstance(companion);
            long groundBodyId = groundBodyId(companion, level);
            if (groundBodyId == Long.MIN_VALUE) {
                reportFailure(level, shipId, missingGroundBodyMessage(companion, level));
                return false;
            }
            Method disable = findMethod(manager.getClass(), "disableCollisionBetween", ServerLevel.class,
                    long.class, long.class, callbackType());
            if (disable == null) {
                reportFailure(level, shipId, "VMod disableCollisionBetween method was not found");
                return false;
            }
            Object callback = callbackType() == null ? null : Proxy.newProxyInstance(
                    callbackType().getClassLoader(), new Class<?>[]{callbackType()}, (proxy, method, args) -> null);
            Object result = disable.invoke(manager, level, shipId, groundBodyId, callback);
            if (result instanceof Boolean success && !success) {
                reportFailure(level, shipId, "VMod rejected collision disable for ground body " + groundBodyId);
                return false;
            }
            reportSuccess(level, shipId, groundBodyId);
            return true;
        } catch (ReflectiveOperationException | LinkageError error) {
            reportFailure(level, shipId, "VMod collision API unavailable: " + error.getClass().getSimpleName());
            return false;
        }
    }

    public static boolean enableGroundCollision(ServerLevel level, long shipId) {
        try {
            Object companion = managerCompanion();
            Object manager = managerInstance(companion);
            long groundBodyId = groundBodyId(companion, level);
            if (groundBodyId == Long.MIN_VALUE) {
                reportFailure(level, shipId, missingGroundBodyMessage(companion, level));
                return false;
            }
            Method enable = findMethod(manager.getClass(), "enableCollisionBetween", ServerLevel.class,
                    long.class, long.class);
            if (enable == null) {
                reportFailure(level, shipId, "VMod enableCollisionBetween method was not found");
                return false;
            }
            enable.invoke(manager, level, shipId, groundBodyId);
            ACTIVE_SHIPS.remove(level.dimension().location() + "/" + shipId);
            return true;
        } catch (ReflectiveOperationException | LinkageError error) {
            reportFailure(level, shipId, "VMod collision-enable API unavailable: " + error.getClass().getSimpleName());
            return false;
        }
    }

    private static long groundBodyId(Object companion, ServerLevel level) throws ReflectiveOperationException {
        Object result = companion.getClass().getMethod("getDimensionToGroundBodyIdImmutable").invoke(companion);
        if (!(result instanceof Map<?, ?> groundBodies)) return Long.MIN_VALUE;
        Object id = groundBodies.get(vsDimensionId(level));
        return id instanceof Number number ? number.longValue() : Long.MIN_VALUE;
    }

    private static String missingGroundBodyMessage(Object companion, ServerLevel level) throws ReflectiveOperationException {
        Object result = companion.getClass().getMethod("getDimensionToGroundBodyIdImmutable").invoke(companion);
        String vsDimensionId = vsDimensionId(level);
        return "VMod has no ground body for VS dimension " + vsDimensionId
                + " (Minecraft dimension " + level.dimension().location() + ")"
                + (result instanceof Map<?, ?> groundBodies ? "; available: " + groundBodies.keySet() : "");
    }

    private static String vsDimensionId(ServerLevel level) throws ReflectiveOperationException {
        String dimensionId = VsGameUtilsBridge.dimensionId(level);
        if (dimensionId != null) return dimensionId;
        throw new ReflectiveOperationException("Valkyrien Skies returned no dimension ID");
    }

    private static Object managerInstance(Object companion) throws ReflectiveOperationException {
        return companion.getClass().getMethod("getInstance").invoke(companion);
    }

    private static void reportFailure(ServerLevel level, long shipId, String message) {
        String key = level.dimension().location() + "/" + shipId;
        ACTIVE_SHIPS.remove(key);
        if (!message.equals(LAST_FAILURES.put(key, message))) {
            VSAnalogWarfare.LOGGER.warn("[VSAW] Ground Collision Disabler for ship {}: {}", shipId, message);
        }
    }

    private static void reportSuccess(ServerLevel level, long shipId, long groundBodyId) {
        String key = level.dimension().location() + "/" + shipId;
        LAST_FAILURES.remove(key);
        if (ACTIVE_SHIPS.add(key)) {
            VSAnalogWarfare.LOGGER.info("[VSAW] Ground Collision Disabler activated for ship {} and ground body {}", shipId, groundBodyId);
        }
    }

    private static Class<?> manager() throws ClassNotFoundException {
        return Class.forName(VMOD_MANAGER);
    }

    private static Object managerCompanion() throws ReflectiveOperationException, ClassNotFoundException {
        return manager().getField("Companion").get(null);
    }

    private static Class<?> callbackType() {
        try {
            return Class.forName("kotlin.jvm.functions.Function0");
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        for (Method method : type.getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != parameterTypes.length) continue;
            Class<?>[] actual = method.getParameterTypes();
            boolean matches = true;
            for (int index = 0; index < actual.length; index++) {
                if (parameterTypes[index] != null && !wrap(actual[index]).equals(wrap(parameterTypes[index]))) {
                    matches = false;
                    break;
                }
            }
            if (matches) return method;
        }
        return null;
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == long.class) return Long.class;
        if (type == int.class) return Integer.class;
        if (type == boolean.class) return Boolean.class;
        return type;
    }
}
