package com.erika.vsanalogwarfare.stabilizer;

import com.erika.vsanalogwarfare.scope.compat.VsCompat;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4dc;
import org.joml.Vector3d;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pure math helpers for the gyro stabilizer control loop.
 *
 * All angular values are in degrees; rotation rates are degrees per game tick.
 * Ship transforms are read from the <b>tick</b> transform (never the render
 * transform) so that server and client run identical math.
 */
public final class StabilizerMath {

    /**
     * Reflection cache keyed by the receiver's concrete class. The integrated
     * server and the client use different ship classes (ServerShip vs
     * ClientShip) in the same JVM, so a single cached Method would be invoked
     * on a foreign class and crash ("object is not an instance of declaring
     * class").
     */
    private static final Map<Class<?>, Method> METHOD_CACHE = new ConcurrentHashMap<>();

    private StabilizerMath() {
    }

    @Nullable
    private static Method methodFor(Class<?> owner, String name) {
        return METHOD_CACHE.computeIfAbsent(owner, cls -> {
            try {
                return cls.getMethod(name);
            } catch (NoSuchMethodException e) {
                return null;
            }
        });
    }

    /** Ship-to-world rotation of the current <b>tick</b> transform, or null when unavailable. */
    @Nullable
    public static Matrix4dc getTickShipToWorld(Object ship) {
        Method direct = methodFor(ship.getClass(), "getShipToWorld");
        if (direct != null) {
            try {
                Object matrix = direct.invoke(ship);
                if (matrix instanceof Matrix4dc m) {
                    return m;
                }
            } catch (ReflectiveOperationException | LinkageError ignored) {
            }
        }
        try {
            Method getTransform = methodFor(ship.getClass(), "getTransform");
            if (getTransform == null) {
                return null;
            }
            Object transform = getTransform.invoke(ship);
            if (transform == null) {
                return null;
            }
            Method m = methodFor(transform.getClass(), "getShipToWorld");
            if (m == null) {
                return null;
            }
            Object matrix = m.invoke(transform);
            return matrix instanceof Matrix4dc m4 ? m4 : null;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    /** Ship-to-world rotation of the previous game tick transform, or null when unavailable. */
    @Nullable
    public static Matrix4dc getPrevTickShipToWorld(Object ship) {
        try {
            Method getPrev = methodFor(ship.getClass(), "getPrevTickTransform");
            if (getPrev == null) {
                return null;
            }
            Object transform = getPrev.invoke(ship);
            if (transform == null) {
                return null;
            }
            Method m = methodFor(transform.getClass(), "getShipToWorld");
            if (m == null) {
                return null;
            }
            Object matrix = m.invoke(transform);
            return matrix instanceof Matrix4dc m4 ? m4 : null;
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }

    /**
     * Ship angular velocity as a world-space vector in radians per game tick,
     * or null when the client has not synced it.
     */
    @Nullable
    public static Vector3d getShipOmegaPerTick(Object ship) {
        try {
            Method getOmega = methodFor(ship.getClass(), "getOmega");
            if (getOmega == null) {
                return null;
            }
            Object omega = getOmega.invoke(ship);
            if (omega instanceof org.joml.Vector3dc v) {
                return new Vector3d(v).mul(0.05);
            }
            return null;
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }

    public static Vec3 transformDirection(@Nullable Matrix4dc matrix, Vec3 direction) {
        if (matrix == null) {
            return direction.normalize();
        }
        Vector3d dest = new Vector3d();
        matrix.transformDirection(direction.x, direction.y, direction.z, dest);
        return new Vec3(dest.x, dest.y, dest.z).normalize();
    }

    /** World-space elevation of a direction, in degrees (positive = up). */
    public static double elevationDeg(Vec3 direction) {
        Vec3 d = direction.normalize();
        return Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, d.y))));
    }

    /**
     * The cannon's pitch rotation axis in ship-local space, following the CBC
     * render convention: an X-facing contraption rotates about Z, everything
     * else rotates about X.
     */
    public static Vec3 pitchAxisShipLocal(Direction initialOrientation) {
        return initialOrientation.getAxis() == Direction.Axis.X
                ? new Vec3(0.0, 0.0, 1.0)
                : new Vec3(1.0, 0.0, 0.0);
    }

    /**
     * CBC's direction sign: {@code newPitch = cannonPitch + pitchSpeed * sgn},
     * with {@code sgn} derived from the contraption's initial orientation.
     */
    public static float cbcPitchSign(Direction initialOrientation) {
        boolean positive = (initialOrientation.getAxisDirection() == Direction.AxisDirection.POSITIVE)
                == (initialOrientation.getAxis() == Direction.Axis.X);
        return positive ? 1.0f : -1.0f;
    }

    /**
     * d(world elevation) / d(contraption pitch angle) in deg/deg at the current
     * pose. Zero means pitching cannot change world elevation (gimbal lock).
     */
    public static double pitchToElevationJacobian(Matrix4dc shipRotation, Vec3 aimShip, Vec3 axisShip,
                                                  float cbcSign, double elevationRad) {
        Vec3 axisWorld = transformDirection(shipRotation, axisShip);
        Vec3 aimWorld = transformDirection(shipRotation, aimShip);
        Vec3 cross = axisWorld.cross(aimWorld);
        double cos = Math.max(0.05, Math.cos(elevationRad));
        return cbcSign * cross.y / cos;
    }

    /**
     * World elevation change (deg/tick) of the current aim direction caused by
     * ship rotation. Prefers the synced angular velocity and falls back to a
     * finite difference between the previous and current tick transforms.
     */
    public static double elevationRatePerTick(Object ship, Matrix4dc currentRotation,
                                              @Nullable Matrix4dc prevRotation, Vec3 aimShip, Vec3 aimWorld) {
        Vector3d omega = getShipOmegaPerTick(ship);
        if (omega != null) {
            // delev/dt = ((omega x d) . up) / cos(elev);  omega already per tick.
            double cos = Math.max(0.05, Math.cos(Math.toRadians(StabilizerMath.elevationDeg(aimWorld))));
            double crossY = omega.z * aimWorld.x - omega.x * aimWorld.z;
            return Math.toDegrees(crossY / cos);
        }
        if (prevRotation == null) {
            return 0.0;
        }
        Vec3 aimWorldPrev = transformDirection(prevRotation, aimShip);
        return elevationDeg(aimWorld) - elevationDeg(aimWorldPrev);
    }

    /** Convenience passthrough so callers do not need to import VsCompat here. */
    @Nullable
    public static Object shipManaging(Level level, net.minecraft.core.BlockPos pos) {
        return VsCompat.findShip(level, pos);
    }
}
