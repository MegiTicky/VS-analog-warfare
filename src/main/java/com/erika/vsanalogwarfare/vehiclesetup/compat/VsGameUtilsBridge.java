package com.erika.vsanalogwarfare.vehiclesetup.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Server-safe access to VSGameUtilsKt without enumerating its client-only overloads. */
public final class VsGameUtilsBridge {
    private static final String UTILS = "org.valkyrienskies.mod.common.VSGameUtilsKt";
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.publicLookup();
    private static final MethodHandle SHIP_OBJECT_MANAGING_POS = find(
            "getShipObjectManagingPos", "org.valkyrienskies.core.api.ships.LoadedShip",
            Level.class, Vec3i.class);
    private static final MethodHandle SHIP_MANAGING_POS = find(
            "getShipManagingPos", "org.valkyrienskies.core.api.ships.Ship",
            Level.class, BlockPos.class);
    private static final MethodHandle ALL_SHIPS = find(
            "getAllShips", "org.valkyrienskies.core.api.ships.QueryableShipData",
            Level.class);
    private static final MethodHandle SHIP_OBJECT_WORLD = find(
            "getShipObjectWorld", "org.valkyrienskies.core.apigame.world.ShipWorldCore",
            Level.class);
    private static final MethodHandle Y_RANGE = find(
            "getYRange", "org.valkyrienskies.core.api.world.LevelYRange",
            Level.class);
    private static final MethodHandle DIMENSION_ID = find(
            "getDimensionId", "java.lang.String", Level.class);

    private VsGameUtilsBridge() { }

    @Nullable public static Object shipObjectManagingPos(Level level, BlockPos pos) {
        return invoke(SHIP_OBJECT_MANAGING_POS, level, pos);
    }

    @Nullable public static Object shipManagingPos(Level level, BlockPos pos) {
        return invoke(SHIP_MANAGING_POS, level, pos);
    }

    public static List<Object> allShips(Level level) {
        Object result = invoke(ALL_SHIPS, level);
        if (!(result instanceof Iterable<?> iterable)) return Collections.emptyList();
        List<Object> ships = new ArrayList<>();
        for (Object ship : iterable) ships.add(ship);
        return ships;
    }

    @Nullable public static Object shipObjectWorld(Level level) {
        return invoke(SHIP_OBJECT_WORLD, level);
    }

    @Nullable public static Object yRange(Level level) {
        return invoke(Y_RANGE, level);
    }

    @Nullable public static String dimensionId(Level level) {
        Object result = invoke(DIMENSION_ID, level);
        return result instanceof String value ? value : null;
    }

    @Nullable private static MethodHandle find(String name, String returnType, Class<?>... parameters) {
        try {
            Class<?> utils = Class.forName(UTILS);
            Class<?> result = returnType == null ? void.class : Class.forName(returnType);
            return LOOKUP.findStatic(utils, name, MethodType.methodType(result, parameters));
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    @Nullable private static Object invoke(@Nullable MethodHandle method, Object... arguments) {
        if (method == null) return null;
        try {
            return method.invokeWithArguments(arguments);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
