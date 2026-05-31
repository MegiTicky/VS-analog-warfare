package com.lauya.vsanalogwarfare.scope.rig;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public record CameraPose(Vec3 position, float yaw, float pitch, float qx, float qy, float qz, float qw) {
    public static CameraPose looking(Vec3 position, Vec3 direction) {
        return looking(position, direction, new Vec3(0.0, 1.0, 0.0));
    }

    public static CameraPose looking(Vec3 position, Vec3 direction, Vec3 upHint) {
        Vec3 d = direction.normalize();
        Vec3 up = safeNormalize(upHint, new Vec3(0.0, 1.0, 0.0));
        if (Math.abs(d.dot(up)) > 0.999) {
            up = Math.abs(d.dot(new Vec3(0.0, 1.0, 0.0))) > 0.999
                    ? new Vec3(1.0, 0.0, 0.0)
                    : new Vec3(0.0, 1.0, 0.0);
        }

        // Minecraft's Camera quaternion maps local +Z to look, +Y to up, and
        // +X to the camera's left vector. Do not use Quaternionf#lookAlong here:
        // JOML's lookAlong is a view/-Z convention, and it only happened to line
        // up at some world-axis headings. Building the basis explicitly keeps the
        // quaternion's forward vector identical to the yaw/pitch direction at all
        // ship headings, so roll extraction cannot pick up a yaw-dependent error.
        Vec3 left = up.cross(d).normalize();
        Vec3 correctedUp = d.cross(left).normalize();
        Matrix3f basis = new Matrix3f()
                .setColumn(0, new Vector3f((float) left.x, (float) left.y, (float) left.z))
                .setColumn(1, new Vector3f((float) correctedUp.x, (float) correctedUp.y, (float) correctedUp.z))
                .setColumn(2, new Vector3f((float) d.x, (float) d.y, (float) d.z));
        Quaternionf rotation = new Quaternionf().setFromNormalized(basis).normalize();

        double horizontal = Math.sqrt(d.x * d.x + d.z * d.z);
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, horizontal));
        float yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0);
        return new CameraPose(position, yaw, pitch, rotation.x, rotation.y, rotation.z, rotation.w);
    }

    public Vec3 direction() {
        double yawRad = Math.toRadians(this.yaw + 90.0f);
        double pitchRad = Math.toRadians(this.pitch);
        double horizontal = Math.cos(pitchRad);
        return new Vec3(Math.cos(yawRad) * horizontal, -Math.sin(pitchRad), Math.sin(yawRad) * horizontal).normalize();
    }

    public Vec3 up() {
        Vector3f v = new Vector3f(0.0f, 1.0f, 0.0f).rotate(new Quaternionf(this.qx, this.qy, this.qz, this.qw).normalize());
        return new Vec3(v.x(), v.y(), v.z()).normalize();
    }

    public Vec3 left() {
        Vector3f v = new Vector3f(1.0f, 0.0f, 0.0f).rotate(new Quaternionf(this.qx, this.qy, this.qz, this.qw).normalize());
        return new Vec3(v.x(), v.y(), v.z()).normalize();
    }

    private static Vec3 safeNormalize(Vec3 vec, Vec3 fallback) {
        return vec.lengthSqr() < 1.0e-8 ? fallback : vec.normalize();
    }
}
