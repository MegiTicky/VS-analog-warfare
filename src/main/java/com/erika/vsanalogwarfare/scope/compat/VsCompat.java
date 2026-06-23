package com.erika.vsanalogwarfare.scope.compat;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
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
    private static Method getShipManagingPos;
    private static Method getShipObjectManagingPosClient;
    private static Method getShipMountedToMethod;
    private static Method getAllShipsMethod;
    private static Method getShipObjectWorldMethod;
    private static Method getLoadedShipsMethod;
    private static boolean getShipMountedToInitialized = false;
    private static boolean getAllShipsInitialized = false;
    private static long lastShipDirectionLogMs = 0;

    private VsCompat() {
    }

    public static boolean isPlayerMountedToShip() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return false;
        }
        if (player.getVehicle() == null) {
            return false;
        }
        try {
            if (vsGameUtilsClass == null) {
                vsGameUtilsClass = Class.forName("org.valkyrienskies.mod.common.VSGameUtilsKt");
            }
            if (!getShipMountedToInitialized) {
                getShipMountedToInitialized = true;
                try {
                    getShipMountedToMethod = vsGameUtilsClass.getMethod("getShipMountedTo", net.minecraft.world.entity.Entity.class);
                } catch (NoSuchMethodException ignored) {
                }
            }
            if (getShipMountedToMethod == null) {
                return false;
            }
            Object result = getShipMountedToMethod.invoke(null, player);
            return result != null;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
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
            LOGGER.info("[VSAW_SCOPE] shipToWorldDirection: local={} -> world={}", 
                String.format("%.2f,%.2f,%.2f", localDirection.x, localDirection.y, localDirection.z),
                String.format("%.2f,%.2f,%.2f", result.x, result.y, result.z));
        }
        
        return result;
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
            if (vsGameUtilsClass == null) {
                vsGameUtilsClass = Class.forName("org.valkyrienskies.mod.common.VSGameUtilsKt");
            }

            // On the client, prefer the ClientShip return type. It exposes getRenderTransform(),
            // which is what VS uses for camera/render interpolation (partial ticks).
            if (level.isClientSide()) {
                try {
                    if (getShipObjectManagingPosClient == null) {
                        getShipObjectManagingPosClient = vsGameUtilsClass.getMethod(
                                "getShipObjectManagingPos",
                                net.minecraft.client.multiplayer.ClientLevel.class,
                                BlockPos.class
                        );
                    }
                    Object ship = getShipObjectManagingPosClient.invoke(null, level, pos);
                    long now = System.currentTimeMillis();
                    if (now - lastShipDirectionLogMs >= 1000L) {
                        LOGGER.info("[VSAW_SCOPE] findShip(pos={}): {}", pos, ship != null ? ship.getClass().getSimpleName() : "null");
                    }
                    return ship;
                } catch (ReflectiveOperationException | LinkageError ignored) {
                    // Fall back to the generic ship lookup below.
                }
            }

            if (getShipManagingPos == null) {
                getShipManagingPos = vsGameUtilsClass.getMethod("getShipManagingPos", Level.class, BlockPos.class);
            }
            Object ship = getShipManagingPos.invoke(null, level, pos);
            long now = System.currentTimeMillis();
            if (now - lastShipDirectionLogMs >= 1000L) {
                LOGGER.info("[VSAW_SCOPE] findShip(pos={}): {}", pos, ship != null ? ship.getClass().getSimpleName() : "null");
            }
            return ship;
        } catch (ReflectiveOperationException | LinkageError e) {
            long now = System.currentTimeMillis();
            if (now - lastShipDirectionLogMs >= 1000L) {
                LOGGER.info("[VSAW_SCOPE] findShip(pos={}): exception {}", pos, e.getClass().getSimpleName());
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
        List<Object> ships = new ArrayList<>();
        try {
            if (vsGameUtilsClass == null) {
                vsGameUtilsClass = Class.forName("org.valkyrienskies.mod.common.VSGameUtilsKt");
            }
            
            if (!getAllShipsInitialized) {
                getAllShipsInitialized = true;
                try {
                    getAllShipsMethod = vsGameUtilsClass.getMethod("getAllShips", Level.class);
                } catch (NoSuchMethodException ignored) {
                }
                try {
                    getShipObjectWorldMethod = vsGameUtilsClass.getMethod("getShipObjectWorld", Level.class);
                } catch (NoSuchMethodException ignored) {
                }
            }
            
            if (getAllShipsMethod != null) {
                Object allShips = getAllShipsMethod.invoke(null, level);
                if (allShips instanceof Iterable<?> iterable) {
                    for (Object ship : iterable) {
                        ships.add(ship);
                    }
                }
            } else if (getShipObjectWorldMethod != null && getLoadedShipsMethod != null) {
                Object shipWorld = getShipObjectWorldMethod.invoke(null, level);
                if (shipWorld != null && getLoadedShipsMethod == null) {
                    getLoadedShipsMethod = shipWorld.getClass().getMethod("getLoadedShips");
                }
                if (shipWorld != null && getLoadedShipsMethod != null) {
                    Object loadedShips = getLoadedShipsMethod.invoke(shipWorld);
                    if (loadedShips instanceof Iterable<?> iterable) {
                        for (Object ship : iterable) {
                            ships.add(ship);
                        }
                    }
                }
            }
        } catch (ReflectiveOperationException | LinkageError e) {
            LOGGER.info("[VSAW_SCOPE] getAllShips: exception {}", e.getClass().getSimpleName());
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
            Method getYRange = vsGameUtilsClass.getMethod("getYRange", Level.class);
            Object yRange = getYRange.invoke(null, level);
            Method getCenterMethod = chunkClaim.getClass().getMethod("getCenterBlockCoordinates", Object.class, org.joml.Vector3i.class);
            getCenterMethod.invoke(chunkClaim, yRange, center);
            return new int[] { center.x, center.y, center.z };
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
        return null;
    }
}
