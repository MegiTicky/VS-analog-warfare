package com.erika.vsanalogwarfare.mouseaim;

import com.erika.vsanalogwarfare.VSAnalogWarfare;
import com.erika.vsanalogwarfare.config.CommonConfig;
import com.erika.vsanalogwarfare.scope.compat.CbcCompat;
import com.erika.vsanalogwarfare.scope.compat.VsCompat;
import com.erika.vsanalogwarfare.stabilizer.StabilizerMath;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4dc;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * Yaw servo for turret mode. The aim direction is the setpoint, the bore
 * direction (mount angle carried through the turret ship's transform) is the
 * measurement, and the output is a Create RPM command handed to the block's
 * rotation-output face. The player wires that output into a Clockwork physics
 * bearing, whose internal controller turns the commanded RPM into torque on
 * the turret ship — so this class is a velocity servo, not a torque servo.
 *
 * <p>All angles are computed in <b>world space</b> (Minecraft azimuth,
 * {@code atan2(-x, z)}, degrees, wrapped to ±180). The setpoint arrives from
 * the client as a world-space direction (the free-look angles are seeded in
 * world frame), and the bore is converted to world with the turret ship's tick
 * transform.
 *
 * <p><b>Response shape:</b> single-mode. The command is the tuned PID
 * ({@code kp * error + kd * d(error)/dt + ff * sweepRate}, gains never
 * scaled) clamped to a cap of the input shaft speed up to the configured
 * ceiling — a slow crank slows the whole approach, a fast one allows the
 * full validated response, and the command always tapers with the error.
 * The derivative term damps the settle, the feed-forward term keeps tracking
 * a sweeping aim, and the slew limiter keeps the physics bearing from being
 * shocked. The gains come from the block's {@link TurretTuning} — the global
 * config template until a calibration stores per-block values.
 *
 * <p><b>Sign:</b> a Clockwork physics bearing facing up applies omega along
 * its facing normal, so positive RPM decreases the Minecraft azimuth
 * (counter-clockwise seen from above). The error term is therefore negated
 * before the strength scaling; {@link CommonConfig#turretYawInvert()} flips it
 * in case a bearing is placed or behaves differently.
 */
final class TurretYawController {
    private static final float DERIVATIVE_SMTH = 0.5F;
    private static final float FEED_FORWARD_SMTH = 0.3F;
    private static final float ZERO_SPEED_EPSILON_RPM = 0.01F;
    private static final float HOLD_FEED_RATE_THRESHOLD = 0.05F;
    private static final int DEBUG_PERIOD_TICKS = 4;

    private double prevErr;
    private double prevSetpointYaw;
    private double derivativeLpf;
    private double setpointRateLpf;
    private boolean hasHistory;
    private float lastOutputRpm;
    private int debugTimer;

    /**
     * World-frame azimuth of the cannon bore, or {@code null} when it cannot
     * be resolved this tick (no ship, no cannon angle, bore vertical). Shared
     * with {@link TurretAutotuner}, which drives the same measurement.
     */
    @Nullable
    static Double measuredBoreYawDeg(Level level, BlockPos mountPos) {
        Object turretShip = VsCompat.findShip(level, mountPos);
        Direction initialOrientation = CbcCompat.getInitialOrientationFromCannon(level, mountPos);
        if (turretShip == null || initialOrientation == null) {
            return null;
        }
        Optional<Vec3> boreLocal = CbcCompat.getAimDirection(level, mountPos, initialOrientation, 1.0F, false);
        Matrix4dc turretToWorld = StabilizerMath.getTickShipToWorld(turretShip);
        if (boreLocal.isEmpty() || turretToWorld == null) {
            return null;
        }
        Vec3 boreWorld = StabilizerMath.transformDirection(turretToWorld, boreLocal.get());
        if (boreWorld.horizontalDistanceSqr() < 1.0e-6) {
            // Bore is straight up or down; azimuth is undefined.
            return null;
        }
        return azimuthDeg(boreWorld);
    }

    /**
     * Computes this tick's output command.
     *
     * @param targetDirection the aim direction from the client packet (world frame)
     * @return the commanded RPM, slew-limited; the previous output when the
     *         measurement cannot be resolved this tick
     */
    float computeTargetRpm(MouseAimBlockEntity controller, BlockPos mountPos, Vec3 targetDirection) {
        Level level = controller.getLevel();
        if (level == null) {
            return lastOutputRpm;
        }
        Double measured = measuredBoreYawDeg(level, mountPos);
        if (measured == null) {
            return lastOutputRpm;
        }

        TurretTuning tuning = controller.getEffectiveTuning();
        double setpointYaw = azimuthDeg(targetDirection);
        // The horizontal angle between two directions is the difference of
        // their azimuths (identical to the vector form this replaces).
        double measuredYaw = measured;
        double err = wrapDegrees(setpointYaw - measuredYaw);

        double derivative = hasHistory ? wrapDegrees(err - prevErr) : 0.0D;
        double setpointRate = hasHistory ? wrapDegrees(setpointYaw - prevSetpointYaw) : 0.0D;
        prevSetpointYaw = setpointYaw;
        derivativeLpf += (derivative - derivativeLpf) * DERIVATIVE_SMTH;
        setpointRateLpf += (setpointRate - setpointRateLpf) * FEED_FORWARD_SMTH;
        prevErr = err;
        hasHistory = true;

        double core = tuning.kp() * err
                + tuning.kd() * derivativeLpf
                + tuning.feedForward() * setpointRateLpf;
        if (Math.abs(err) < CommonConfig.turretDeadbandDeg()
                && Math.abs(setpointRateLpf) < HOLD_FEED_RATE_THRESHOLD) {
            core = 0.0D;
        }

        // Single-mode response: the tuned PID always commands the output and
        // its damping acts over the whole range; the cap is the input shaft
        // speed (up to the configured ceiling), so a slow crank slows the
        // whole approach and the command always tapers with the error — no
        // relay, no speed-induced instability.
        float cap = (float) Math.min(CommonConfig.turretMaxOutputRpm(), Math.abs(controller.getSpeed()));
        float rpmSign = CommonConfig.turretYawInvert() ? 1.0F : -1.0F;
        float raw = (float) Mth.clamp(rpmSign * core, -cap, cap);
        float slew = (float) tuning.slewPerTick();
        lastOutputRpm = (float) Mth.clamp(raw, lastOutputRpm - slew, lastOutputRpm + slew);

        debugTick(controller, err, setpointYaw, measuredYaw, raw, lastOutputRpm, cap);
        return lastOutputRpm;
    }

    /**
     * Steps the output toward zero at the given slew rate; called while no
     * aim target is active so the turret coasts to a stop instead of freezing.
     */
    float idle(float slewPerTick) {
        if (Math.abs(lastOutputRpm) <= ZERO_SPEED_EPSILON_RPM) {
            lastOutputRpm = 0.0F;
            return 0.0F;
        }
        lastOutputRpm -= Math.signum(lastOutputRpm) * Math.min(Math.abs(lastOutputRpm), slewPerTick);
        return lastOutputRpm;
    }

    /**
     * Adopts a command produced outside the control law (the auto-calibration
     * step test) so the servo resumes by slewing from where the turret
     * actually is instead of jumping from a stale internal state.
     */
    void syncOutput(float externalRpm) {
        lastOutputRpm = externalRpm;
    }

    /** Clears the loop history (called when the target is dropped entirely). */
    void reset() {
        prevErr = 0.0D;
        prevSetpointYaw = 0.0D;
        derivativeLpf = 0.0D;
        setpointRateLpf = 0.0D;
        hasHistory = false;
    }

    float lastOutputRpm() {
        return lastOutputRpm;
    }

    private static double azimuthDeg(Vec3 direction) {
        return wrapDegrees(Math.toDegrees(Math.atan2(-direction.x, direction.z)));
    }

    private static double wrapDegrees(double value) {
        value %= 360.0D;
        if (value >= 180.0D) value -= 360.0D;
        if (value < -180.0D) value += 360.0D;
        return value;
    }

    private void debugTick(MouseAimBlockEntity controller, double err, double setpointYaw,
                           double measuredYaw, float rawRpm, float slewedRpm, float cap) {
        if (!CommonConfig.turretDebug()) {
            return;
        }
        if (debugTimer++ < DEBUG_PERIOD_TICKS) {
            return;
        }
        debugTimer = 0;
        VSAnalogWarfare.LOGGER.info("[VSAW_TURRET] {} set={} meas={} err={} raw={} rpm={} cap={} invert={}",
                controller.getBlockPos(), String.format("%.2f", setpointYaw),
                String.format("%.2f", measuredYaw), String.format("%.2f", err),
                String.format("%.2f", rawRpm), String.format("%.2f", slewedRpm),
                String.format("%.2f", cap), CommonConfig.turretYawInvert());
    }
}
