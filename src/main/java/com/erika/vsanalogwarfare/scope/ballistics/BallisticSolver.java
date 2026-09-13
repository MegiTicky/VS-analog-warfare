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
        return solvePitch(profile, targetDistance, maxPitchDeg, false, 45.0);
    }

    // High arc = the steeper of the two solutions for the same distance (artillery / mortar fire).
    // apexPitchDeg = pitch of the flattest-arc maximum range (the branch point); the high arc always
    // sits above it. Pass the cached apex pitch when available.
    public static ReticleMark solvePitch(BallisticProfile profile, int targetDistance, double maxPitchDeg, boolean highArc, double apexPitchDeg) {
        double bestPitch = Double.NaN;
        double bestError = Double.POSITIVE_INFINITY;
        if (highArc) {
            // Scan only the region ABOVE the apex pitch, top-down. Selecting the highest
            // minimal-error sample across the whole sweep fails: both arcs' sampled errors converge
            // near zero and the low arc is sampled first, so the comparison never switches to the
            // steep solution and the gun would pitch down instead of up.
            double floor = Math.max(0.0, apexPitchDeg - 1.0);
            for (double pitch = maxPitchDeg; pitch >= floor; pitch -= 0.5) {
                double err = rangeError(profile, targetDistance, pitch);
                if (err < bestError) {
                    bestError = err;
                    bestPitch = pitch;
                }
            }
        } else {
            // Coarse scan keeps the lowest valid pitch (strict <) - the low arc.
            for (double pitch = 0.0; pitch <= maxPitchDeg; pitch += 0.5) {
                double err = rangeError(profile, targetDistance, pitch);
                if (err < bestError) {
                    bestError = err;
                    bestPitch = pitch;
                }
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

    public record ApexSolution(double range, double pitchDegrees) {}

    // Apex = the flattest-trajectory maximum range; the zero wheel reverses direction when it crosses this.
    public static ApexSolution maxRangeApex(BallisticProfile profile) {
        if (profile == null || !profile.valid()) return null;
        double bestRange = -1.0;
        double bestPitch = 45.0;
        for (double pitch = 0.0; pitch <= 90.0; pitch += 0.5) {
            double range = impactRange(profile, pitch);
            if (range > bestRange) {
                bestRange = range;
                bestPitch = pitch;
            }
        }
        if (bestRange <= 0.0) return null;
        return new ApexSolution(bestRange, bestPitch);
    }

    public static double impactRange(BallisticProfile profile, double pitchDeg) {
        double pitch = Math.toRadians(pitchDeg);
        Vec3 pos = Vec3.ZERO;
        Vec3 velocity = new Vec3(Math.cos(pitch) * profile.muzzleSpeed(), Math.sin(pitch) * profile.muzzleSpeed(), 0.0);
        int maxTicks = profile.lifetimeTicks() > 0 ? Math.min(profile.lifetimeTicks(), 2000) : 2000;
        Vec3 last = pos;
        for (int tick = 0; tick < maxTicks; tick++) {
            last = pos;
            pos = pos.add(velocity);
            if (pos.y < 0.0 && last.y >= 0.0) {
                double dy = pos.y - last.y;
                double t = Math.abs(dy) < 1.0e-8 ? 0.0 : (0.0 - last.y) / dy;
                return last.x + (pos.x - last.x) * t;
            }
            velocity = applyForces(profile, velocity);
        }
        return pos.x;
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
