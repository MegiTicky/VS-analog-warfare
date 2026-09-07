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
 * {@code atan2(-x, z)}, degrees, wrapped to ±180). Working in world space
 * sidesteps VS2's ship-local axis conventions entirely: a setpoint captured in
 * the scope's ship frame is converted to world with that ship's tick
 * transform, and the bore is converted to world with the turret ship's.
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
    private static final int DEBUG_PERIOD_TICKS = 20;

    private double prevErr;
    private double prevSetpointYaw;
    private double derivativeLpf;
    private double setpointRateLpf;
    private boolean hasHistory;
    private float lastOutputRpm;
    private int debugTimer;

    /**
     * Computes this tick's output command.
     *
     * @param targetDirection the aim direction from the client packet
     * @param shipRelative    true when the client captured it in the scope
     *                        ship's frame (player was mounted)
     * @return the commanded RPM, slew-limited; the previous output when the
     *         setpoint or measurement cannot be resolved this tick
     */
    float computeTargetRpm(MouseAimBlockEntity controller, @Nullable BlockPos scopePos, BlockPos mountPos,
                           Vec3 targetDirection, boolean shipRelative) {
        Level level = controller.getLevel();
        if (level == null) {
            return lastOutputRpm;
        }

        Vec3 setpointWorld = targetDirection;
        if (shipRelative) {
            if (scopePos == null) {
                return lastOutputRpm;
            }
            Object scopeShip = VsCompat.findShip(level, scopePos);
            if (scopeShip == null) {
                return lastOutputRpm;
            }
            setpointWorld = VsCompat.shipToWorldDirection(scopeShip, targetDirection);
        }

        Object turretShip = VsCompat.findShip(level, mountPos);
        Direction initialOrientation = CbcCompat.getInitialOrientationFromCannon(level, mountPos);
        if (turretShip == null || initialOrientation == null) {
            return lastOutputRpm;
        }
        Optional<Vec3> boreLocal = CbcCompat.getAimDirection(level, mountPos, initialOrientation, 1.0F, false);
        Matrix4dc turretToWorld = StabilizerMath.getTickShipToWorld(turretShip);
        if (boreLocal.isEmpty() || turretToWorld == null) {
            return lastOutputRpm;
        }
        Vec3 boreWorld = StabilizerMath.transformDirection(turretToWorld, boreLocal.get());
        if (boreWorld.horizontalDistanceSqr() < 1.0e-6) {
            // Bore is straight up or down; azimuth is undefined, hold.
            return lastOutputRpm;
        }

        double setpointYaw = azimuthDeg(setpointWorld);
        double measuredYaw = azimuthDeg(boreWorld);
        double err = wrapDegrees(setpointYaw - measuredYaw);

        double derivative = hasHistory ? wrapDegrees(err - prevErr) : 0.0D;
        double setpointRate = 0.0D;
        if (hasHistory) {
            // Setpoint rate is differenced in the same world frame as the stored yaw.
            setpointRate = wrapDegrees(setpointYaw - prevSetpointYaw);
        }
        prevSetpointYaw = setpointYaw;
        derivativeLpf += (derivative - derivativeLpf) * DERIVATIVE_SMTH;
        setpointRateLpf += (setpointRate - setpointRateLpf) * FEED_FORWARD_SMTH;
        prevErr = err;
        hasHistory = true;

        double core = CommonConfig.turretKp() * err
                + CommonConfig.turretKd() * derivativeLpf
                + CommonConfig.turretFeedForward() * setpointRateLpf;
        if (Math.abs(err) < CommonConfig.turretDeadbandDeg()
                && Math.abs(setpointRateLpf) < HOLD_FEED_RATE_THRESHOLD) {
            core = 0.0D;
        }

        TurretStrength strength = controller.getTurretStrength();
        float sign = CommonConfig.turretYawInvert() ? 1.0F : -1.0F;
        float raw = (float) Mth.clamp(sign * core * strength.gainMultiplier(),
                -strength.maxRpm(), strength.maxRpm());
        float slew = (float) CommonConfig.turretOutputSlewPerTick();
        lastOutputRpm = (float) Mth.clamp(raw, lastOutputRpm - slew, lastOutputRpm + slew);

        debugTick(controller, err, setpointYaw, measuredYaw, lastOutputRpm);
        return lastOutputRpm;
    }

    /**
     * Steps the output toward zero at the slew rate; called while no aim
     * target is active so the turret coasts to a stop instead of freezing.
     */
    float idle() {
        if (Math.abs(lastOutputRpm) <= ZERO_SPEED_EPSILON_RPM) {
            lastOutputRpm = 0.0F;
            return 0.0F;
        }
        float slew = (float) CommonConfig.turretOutputSlewPerTick();
        lastOutputRpm -= Math.signum(lastOutputRpm) * Math.min(Math.abs(lastOutputRpm), slew);
        return lastOutputRpm;
    }

    /** Clears the loop history (called when the target is dropped entirely). */
    void reset() {
        prevErr = 0.0D;
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

    private void debugTick(MouseAimBlockEntity controller, double err, double setpointYaw, double measuredYaw, float rpm) {
        if (!CommonConfig.turretDebug()) {
            return;
        }
        if (debugTimer++ < DEBUG_PERIOD_TICKS) {
            return;
        }
        debugTimer = 0;
        VSAnalogWarfare.LOGGER.info("[VSAW_TURRET] {} set={} meas={} err={} rpm={}",
                controller.getBlockPos(), String.format("%.2f", setpointYaw),
                String.format("%.2f", measuredYaw), String.format("%.2f", err),
                String.format("%.2f", rpm));
    }
}
