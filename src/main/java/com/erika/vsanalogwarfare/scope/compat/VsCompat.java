package com.erika.vsanalogwarfare.scope.compat;

import com.mojang.logging.LogUtils;
import com.erika.vsanalogwarfare.vehiclesetup.compat.VsGameUtilsBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.joml.Vector3d;
import org.joml.primitives.AABBdc;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class VsCompat {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    private static Class<?> vsGameUtilsClass;
    private static Method getShipMountedToMethod;
    private static Method getLoadedShipsMethod;
    
    private static boolean initialized = false;
    private static boolean isClientSide = false;
    
    private static long lastShipDirectionLogMs = 0;

    private VsCompat() {
    }

    static {
        initialize();
    }
    
    private static void initialize() {
        if (initialized) return;
        initialized = true;

        try {
            vsGameUtilsClass = Class.forName("org.valkyrienskies.mod.common.VSGameUtilsKt");
        } catch (ClassNotFoundException e) {
            LOGGER.warn("[VSAW] VSGameUtilsKt not found, VS integration disabled");
        }
        
        isClientSide = safeCheckClientSide();

        if (isClientSide && vsGameUtilsClass != null) {
            getShipMountedToMethod = tryGetMethod("getShipMountedTo", net.minecraft.world.entity.Entity.class);
        }
        
        LOGGER.debug("[VSAW] VS compat initialized");
    }
    
    private static boolean safeCheckClientSide() {
        return FMLEnvironment.dist == Dist.CLIENT;
    }
    
    private static Method tryGetMethod(String name, Class<?>... paramTypes) {
        try {
            Method m = vsGameUtilsClass.getDeclaredMethod(name, paramTypes);
            m.setAccessible(true);
            LOGGER.debug("[VSAW] Found method {}({})", name, java.util.Arrays.toString(paramTypes));
            return m;
        } catch (NoSuchMethodException e) {
            LOGGER.debug("[VSAW] Method {}({}) not found in VS version", name, java.util.Arrays.toString(paramTypes));
            return null;
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("invalid dist")) {
                LOGGER.debug("[VSAW] Method {} has client-only parameters, skipping", name);
                return null;
            }
            LOGGER.warn("[VSAW] Unexpected error getting method {}: {}", name, e.getMessage());
            return null;
        } catch (Exception e) {
            LOGGER.warn("[VSAW] Error getting method {}: {}", name, e.getClass().getSimpleName());
            return null;
        }
    }

    public static boolean isPlayerMountedToShip() {
        if (!isClientSide) return false;
        if (getShipMountedToMethod == null) return false;
        return DistExecutor.unsafeCallWhenOn(Dist.CLIENT, 
            () -> () -> VsCompatClient.isPlayerMountedToShip(getShipMountedToMethod));
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
        return shipToWorldPosition(ship, localPosition);
    }

    public static Vec3 shipToWorldPosition(Object ship, Vec3 localPosition) {
        Vector3d transformed = invokeMatrixTransform(ship, localPosition, true);
        return transformed == null ? localPosition : new Vec3(transformed.x, transformed.y, transformed.z);
    }

    public static Vec3 shipToWorldDirection(Level level, BlockPos anchorPos, Vec3 localDirection) {
        if (isPlayerMountedToShip()) {
            return localDirection.normalize();
        }
        Object ship = findShip(level, anchorPos);
        if (ship == null) {
            return localDirection.normalize();
        }
        Vector3d transformed = invokeMatrixTransform(ship, localDirection, false);
        if (transformed == null) {
            return localDirection.normalize();
        }
        Vec3 result = new Vec3(transformed.x, transformed.y, transformed.z).normalize();
        
        long now = System.currentTimeMillis();
        if (now - lastShipDirectionLogMs >= 1000L) {
            lastShipDirectionLogMs = now;
            LOGGER.debug("[VSAW_SCOPE] shipToWorldDirection: local={} -> world={}",
                String.format("%.2f,%.2f,%.2f", localDirection.x, localDirection.y, localDirection.z),
                String.format("%.2f,%.2f,%.2f", result.x, result.y, result.z));
        }
        
        return result;
    }

    public static Vec3 shipToWorldDirectionForRaycast(Level level, BlockPos anchorPos, Vec3 localDirection) {
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

    public static Object findShip(Level level, BlockPos pos) {
        try {
            Object ship = VsGameUtilsBridge.shipManagingPos(level, pos);
            long now = System.currentTimeMillis();
            if (now - lastShipDirectionLogMs >= 1000L) {
                lastShipDirectionLogMs = now;
                LOGGER.debug("[VSAW_SCOPE] findShip(pos={}): {}", pos, ship != null ? ship.getClass().getSimpleName() : "null");
            }
            return ship;
        } catch (RuntimeException | LinkageError e) {
            long now = System.currentTimeMillis();
            if (now - lastShipDirectionLogMs >= 1000L) {
                lastShipDirectionLogMs = now;
                LOGGER.debug("[VSAW_SCOPE] findShip(pos={}): exception {}", pos, e.getClass().getSimpleName());
            }
            return null;
        }
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

    public static List<Object> getAllShips(Level level) {
        List<Object> ships = new ArrayList<>(VsGameUtilsBridge.allShips(level));
        if (ships.isEmpty()) {
            try {
                Object shipWorld = VsGameUtilsBridge.shipObjectWorld(level);
                if (shipWorld != null) {
                    if (getLoadedShipsMethod == null) {
                        getLoadedShipsMethod = shipWorld.getClass().getMethod("getLoadedShips");
                    }
                    Object loadedShips = getLoadedShipsMethod.invoke(shipWorld);
                    if (loadedShips instanceof Iterable<?> iterable) {
                        for (Object ship : iterable) {
                            ships.add(ship);
                        }
                    }
                }
            } catch (ReflectiveOperationException | LinkageError e) {
                LOGGER.debug("[VSAW_SCOPE] getAllShips fallback: exception {}", e.getClass().getSimpleName());
            }
        }
        
        return ships;
    }

    public static Vec3 worldToShipDirection(Object ship, Vec3 worldDirection) {
        Vector3d transformed = invokeInverseMatrixTransform(ship, worldDirection, false);
        if (transformed == null) {
            return worldDirection.normalize();
        }
        return new Vec3(transformed.x, transformed.y, transformed.z).normalize();
    }

    public static Vec3 shipToWorldDirection(Object ship, Vec3 localDirection) {
        Vector3d transformed = invokeMatrixTransform(ship, localDirection, false);
        if (transformed == null) {
            return localDirection.normalize();
        }
        return new Vec3(transformed.x, transformed.y, transformed.z).normalize();
    }

    public static org.joml.Vector3dc getShipPositionInWorld(Object ship) {
        try {
            Object transform = ship.getClass().getMethod("getTransform").invoke(ship);
            if (transform != null) {
                return (org.joml.Vector3dc) transform.getClass().getMethod("getPositionInWorld").invoke(transform);
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
        return null;
    }

    public static long getShipId(Object ship) {
        try {
            Object id = ship.getClass().getMethod("getId").invoke(ship);
            if (id instanceof Number number) {
                return number.longValue();
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return -1;
    }

    public static AABBdc getShipAABB(Object ship) {
        try {
            Object aabb = ship.getClass().getMethod("getShipAABB").invoke(ship);
            if (aabb == null) return null;

            double minX = getAabbValue(aabb, "minX");
            double minY = getAabbValue(aabb, "minY");
            double minZ = getAabbValue(aabb, "minZ");
            double maxX = getAabbValue(aabb, "maxX");
            double maxY = getAabbValue(aabb, "maxY");
            double maxZ = getAabbValue(aabb, "maxZ");

            return new org.joml.primitives.AABBd(minX, minY, minZ, maxX, maxY, maxZ);
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
        return null;
    }

    private static double getAabbValue(Object aabb, String methodName) {
        try {
            Method m = aabb.getClass().getMethod(methodName);
            Object result = m.invoke(aabb);
            if (result instanceof Number number) {
                return number.doubleValue();
            }
            return 0.0;
        } catch (ReflectiveOperationException | LinkageError e) {
            return 0.0;
        }
    }

    public static Object getChunkClaim(Object ship) {
        try {
            return ship.getClass().getMethod("getChunkClaim").invoke(ship);
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
        return null;
    }

    public static Iterable<int[]> getChunkClaimChunks(Object chunkClaim) {
        try {
            Method iteratorMethod = chunkClaim.getClass().getMethod("iterator");
            Object iterator = iteratorMethod.invoke(chunkClaim);
            if (iterator instanceof Iterable) {
                @SuppressWarnings("unchecked")
                Iterable<int[]> iterable = (Iterable<int[]>) iterator;
                return iterable;
            }
            java.util.List<int[]> chunks = new java.util.ArrayList<>();
            while (iterator instanceof java.util.Iterator<?> it && it.hasNext()) {
                Object next = it.next();
                if (next instanceof int[] arr) {
                    chunks.add(arr);
                }
            }
            return chunks;
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
        return java.util.Collections.emptyList();
    }

    public static int[] getChunkClaimCenter(Object chunkClaim, Level level) {
        try {
            org.joml.Vector3i center = new org.joml.Vector3i();
            Object yRange = VsGameUtilsBridge.yRange(level);
            if (yRange == null) return null;
            Method getCenterMethod = chunkClaim.getClass().getMethod("getCenterBlockCoordinates", Object.class, org.joml.Vector3i.class);
            getCenterMethod.invoke(chunkClaim, yRange, center);
            return new int[] { center.x, center.y, center.z };
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
        return null;
    }
}
