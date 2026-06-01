package com.erika.vsanalogwarfare.scope.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.util.Optional;

public final class CbcCompat {
    private static final String CANNON_MOUNT = "rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity";
    private static final String FIXED_CANNON_MOUNT = "rbasamoyai.createbigcannons.cannon_control.fixed_cannon_mount.FixedCannonMountBlockEntity";

    private CbcCompat() {
    }

    public static Optional<BlockPos> findNearestMount(Level level, BlockPos origin, int radius) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    if (!level.isLoaded(cursor)) {
                        continue;
                    }
                    if (isCannonMount(level.getBlockEntity(cursor))) {
                        double dist = cursor.distSqr(origin);
                        if (dist < bestDist) {
                            best = cursor.immutable();
                            bestDist = dist;
                        }
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    public static boolean isCannonMount(BlockEntity be) {
        if (be == null) {
            return false;
        }
        String name = be.getClass().getName();
        if (name.equals(CANNON_MOUNT) || name.equals(FIXED_CANNON_MOUNT)) {
            return true;
        }
        for (Class<?> c = be.getClass(); c != null; c = c.getSuperclass()) {
            String className = c.getName();
            if (className.equals(CANNON_MOUNT) || className.equals(FIXED_CANNON_MOUNT)) {
                return true;
            }
        }
        return false;
    }

    public static Optional<Vec3> getAimDirection(Level level, BlockPos mountPos, Direction fallbackFacing, float partialTicks) {
        BlockEntity be = level.getBlockEntity(mountPos);
        if (!isCannonMount(be)) {
            return Optional.of(Vec3.atLowerCornerOf(fallbackFacing.getNormal()).normalize());
        }

        Vec3 byContraption = tryDirectionFromContraption(be, partialTicks).orElse(null);
        if (byContraption != null) {
            return Optional.of(VsCompat.shipToWorldDirection(level, mountPos, byContraption));
        }

        Vec3 byMount = tryDirectionFromMountOffsets(be, partialTicks).orElse(Vec3.atLowerCornerOf(fallbackFacing.getNormal()).normalize());
        return Optional.of(VsCompat.shipToWorldDirection(level, mountPos, byMount));
    }

    public static Optional<Vec3> getAimUpDirection(Level level, BlockPos mountPos, Direction fallbackFacing, Direction scopeUp, float partialTicks) {
        BlockEntity be = level.getBlockEntity(mountPos);
        Vec3 fallbackUp = Vec3.atLowerCornerOf(scopeUp.getNormal()).normalize();
        if (!isCannonMount(be)) {
            return Optional.of(VsCompat.shipToWorldDirection(level, mountPos, fallbackUp));
        }

        Vec3 byContraption = tryUpFromContraption(be, fallbackUp, partialTicks).orElse(null);
        if (byContraption != null) {
            return Optional.of(VsCompat.shipToWorldDirection(level, mountPos, byContraption));
        }

        Vec3 forward = tryDirectionFromMountOffsets(be, partialTicks).orElse(Vec3.atLowerCornerOf(fallbackFacing.getNormal()).normalize());
        Vec3 projectedUp = projectUp(fallbackUp, forward);
        return Optional.of(VsCompat.shipToWorldDirection(level, mountPos, projectedUp));
    }

    private static Optional<Vec3> tryDirectionFromContraption(Object mount, float partialTicks) {
        try {
            Object poce = callNoArg(mount, "getContraption");
            if (poce == null) {
                return Optional.empty();
            }
            Object initial = callNoArg(poce, "getInitialOrientation");
            if (!(initial instanceof Direction direction)) {
                return Optional.empty();
            }
            Vec3 base = Vec3.atLowerCornerOf(direction.getNormal());
            Method applyRotation = poce.getClass().getMethod("applyRotation", Vec3.class, float.class);
            Object rotated = applyRotation.invoke(poce, base, partialTicks);
            return rotated instanceof Vec3 vec ? Optional.of(vec.normalize()) : Optional.empty();
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return Optional.empty();
        }
    }

    private static Optional<Vec3> tryUpFromContraption(Object mount, Vec3 fallbackUp, float partialTicks) {
        try {
            Object poce = callNoArg(mount, "getContraption");
            if (poce == null) {
                return Optional.empty();
            }
            Object initial = callNoArg(poce, "getInitialOrientation");
            Vec3 localUp = fallbackUp;
            if (initial instanceof Direction direction && Math.abs(Vec3.atLowerCornerOf(direction.getNormal()).normalize().dot(localUp)) > 0.98) {
                localUp = new Vec3(0.0, 0.0, 1.0);
            }
            Method applyRotation = poce.getClass().getMethod("applyRotation", Vec3.class, float.class);
            Object rotated = applyRotation.invoke(poce, localUp, partialTicks);
            return rotated instanceof Vec3 vec ? Optional.of(vec.normalize()) : Optional.empty();
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return Optional.empty();
        }
    }

    private static Optional<Vec3> tryDirectionFromMountOffsets(Object mount, float partialTicks) {
        try {
            Direction baseDir = Direction.NORTH;
            Object direction = callNoArg(mount, "getContraptionDirection");
            if (direction instanceof Direction d) {
                baseDir = d;
            }
            float yaw = callFloat(mount, "getYawOffset", partialTicks);
            float pitch = callFloat(mount, "getPitchOffset", partialTicks);
            return Optional.of(directionFromYawPitch(baseDir.toYRot() + yaw, pitch));
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return Optional.empty();
        }
    }

    private static Object callNoArg(Object target, String method) throws ReflectiveOperationException {
        Method m = target.getClass().getMethod(method);
        return m.invoke(target);
    }

    private static float callFloat(Object target, String method, float partialTicks) throws ReflectiveOperationException {
        Method m = target.getClass().getMethod(method, float.class);
        Object value = m.invoke(target, partialTicks);
        return value instanceof Number n ? n.floatValue() : 0.0f;
    }

    public static Vec3 directionFromYawPitch(float yawDeg, float pitchDeg) {
        double yaw = Math.toRadians(yawDeg);
        double pitch = Math.toRadians(pitchDeg);
        double x = -Math.sin(yaw) * Math.cos(pitch);
        // CBC mount offsets are not Minecraft camera XRot values here: positive
        // pitch is elevation, so positive pitch must produce positive Y.
        double y = Math.sin(pitch);
        double z = Math.cos(yaw) * Math.cos(pitch);
        return new Vec3(x, y, z).normalize();
    }

    private static Vec3 projectUp(Vec3 up, Vec3 forward) {
        Vec3 f = forward.normalize();
        Vec3 projected = up.subtract(f.scale(up.dot(f)));
        if (projected.lengthSqr() < 1.0e-8) {
            projected = Math.abs(f.dot(new Vec3(0.0, 1.0, 0.0))) > 0.98
                    ? new Vec3(0.0, 0.0, 1.0)
                    : new Vec3(0.0, 1.0, 0.0);
            projected = projected.subtract(f.scale(projected.dot(f)));
        }
        return projected.normalize();
    }
}
