package com.erika.vsanalogwarfare.scope.ballistics;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class BallisticSolver {
    public static final int DEFAULT_INTERVAL = 100;
    public static final int DEFAULT_MAX_RANGE = 2000;
    public static final double DEFAULT_MAX_PITCH_DEG = 65.0;

    private BallisticSolver() {}

    public static List<ReticleMark> generateMarks(BallisticProfile profile, int interval, int maxRange) {
        List<ReticleMark> marks = new ArrayList<>();
        if (profile == null || !profile.valid()) return marks;
        for (int distance = interval; distance <= maxRange; distance += interval) {
            ReticleMark mark = solvePitch(profile, distance, DEFAULT_MAX_PITCH_DEG);
            if (mark != null) marks.add(mark);
        }
        return marks;
    }

    public static ReticleMark solvePitch(BallisticProfile profile, int targetDistance, double maxPitchDeg) {
        double bestPitch = Double.NaN;
        double bestError = Double.POSITIVE_INFINITY;
        // Coarse + fine scan is stable for both low and high arcs; reticle uses the lowest valid arc.
        for (double pitch = 0.0; pitch <= maxPitchDeg; pitch += 0.5) {
            double err = rangeError(profile, targetDistance, pitch);
            if (err < bestError) {
                bestError = err;
                bestPitch = pitch;
            }
        }
        double start = Math.max(0.0, bestPitch - 0.35);
        double end = Math.min(maxPitchDeg, bestPitch + 0.35);
        for (double pitch = start; pitch <= end; pitch += 0.1) {
            double err = rangeError(profile, targetDistance, pitch);
            if (err < bestError) {
                bestError = err;
                bestPitch = pitch;
            }
        }
        if (!Double.isFinite(bestPitch) || bestError > Math.max(25.0, targetDistance * 0.20)) return null;
        return new ReticleMark(targetDistance, bestPitch, bestError);
    }

    private static double rangeError(BallisticProfile profile, double targetDistance, double pitchDeg) {
        double yAtTarget = yAtHorizontalDistance(profile, targetDistance, pitchDeg);
        if (!Double.isFinite(yAtTarget)) return Double.POSITIVE_INFINITY;
        return Math.abs(yAtTarget);
    }

    private static double yAtHorizontalDistance(BallisticProfile profile, double targetDistance, double pitchDeg) {
        double pitch = Math.toRadians(pitchDeg);
        Vec3 pos = Vec3.ZERO;
        Vec3 velocity = new Vec3(Math.cos(pitch) * profile.muzzleSpeed(), Math.sin(pitch) * profile.muzzleSpeed(), 0.0);
        int maxTicks = profile.lifetimeTicks() > 0 ? Math.min(profile.lifetimeTicks(), 2000) : 2000;
        Vec3 last = pos;
        for (int tick = 0; tick < maxTicks; tick++) {
            last = pos;
            pos = pos.add(velocity);
            if (pos.x >= targetDistance) {
                double dx = pos.x - last.x;
                double t = Math.abs(dx) < 1.0e-8 ? 0.0 : (targetDistance - last.x) / dx;
                return last.y + (pos.y - last.y) * t;
            }
            velocity = applyForces(profile, velocity);
            if (pos.y < -512.0 && pos.x < targetDistance) return Double.NaN;
        }
        return Double.NaN;
    }

    private static Vec3 applyForces(BallisticProfile profile, Vec3 velocity) {
        double speed = velocity.length();
        if (speed > 1.0e-8) {
            double dragForce = profile.drag() * speed;
            if (profile.quadraticDrag()) dragForce *= speed;
            dragForce = Math.min(dragForce, speed);
            velocity = velocity.add(velocity.normalize().scale(-dragForce));
        }
        return velocity.add(0.0, profile.gravity(), 0.0);
    }

    //for ship rangefinder
    public static double intersectRayAABB(Vec3 start, Vec3 dir, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        double tmin = (minX - start.x) / dir.x;
        double tmax = (maxX - start.x) / dir.x;
        if (tmin > tmax) { double temp = tmin; tmin = tmax; tmax = temp; }

        double tymin = (minY - start.y) / dir.y;
        double tymax = (maxY - start.y) / dir.y;
        if (tymin > tymax) { double temp = tymin; tymin = tymax; tymax = temp; }

        if ((tmin > tymax) || (tymin > tmax)) return -1.0;

        if (tymin > tmin) tmin = tymin;
        if (tymax < tmax) tmax = tymax;

        double tzmin = (minZ - start.z) / dir.z;
        double tzmax = (maxZ - start.z) / dir.z;
        if (tzmin > tzmax) { double temp = tzmin; tzmin = tzmax; tzmax = temp; }

        if ((tmin > tzmax) || (tzmin > tmax)) return -1.0;

        if (tzmin > tmin) tmin = tzmin;
        if (tzmax < tmax) tmax = tzmax;

        // If tmax < 0, the box is behind the player
        if (tmax < 0) return -1.0;

        // If tmin < 0, the player is INSIDE the box
        return tmin < 0 ? tmax : tmin;
    }
}
