package com.erika.vsanalogwarfare.mouseaim;

import com.erika.vsanalogwarfare.VSAnalogWarfare;
import com.erika.vsanalogwarfare.config.CommonConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Step-response auto-calibration for the turret yaw servo.
 *
 * <p><b>Method.</b> The servo commands a small constant RPM in one direction
 * until the measured bore rate settles, then the opposite direction until it
 * settles back. The Clockwork physics bearing is a velocity servo, so the
 * steady rate always reaches the commanded value — what a heavier or dragged
 * turret changes is the <b>lag</b>. For each segment the lag is integrated
 * directly: for a first-order response, {@code Σ (1 − rate/steadyRate)} in
 * ticks equals the time constant, and using every sample instead of a single
 * crossing makes the estimate noise-robust. Aiming in both directions and
 * averaging the two integrals cancels any constant ship rotation during the
 * test.
 *
 * <p>The lag ratio against the configured reference turret
 * ({@code calibrationReferenceLagTicks} — the lag the template gains were
 * hand-tuned on) de-rates the template's proportional gain
 * ({@link TurretTuning#scaledBy}); kd and feed-forward are unchanged, and the
 * factor is floored at 0.5 so the result softens the response but never
 * cripples it. Results are stored on the block.
 */
final class TurretAutotuner {
    private enum Phase { IDLE, OUT, BACK }

    /** Test amplitude in RPM — small enough that even a moment-dominated turret stays inside the travel budget. */
    private static final double TEST_RPM = 2.0D;
    private static final int MIN_SEGMENT_TICKS = 10;
    private static final int MAX_SEGMENT_TICKS = 100;
    /** Consecutive ticks of near-constant rate that end a segment early. */
    private static final int SETTLED_TICKS_NEEDED = 4;
    private static final double SETTLE_ABS_TOLERANCE = 0.02D;
    private static final double SETTLE_REL_TOLERANCE = 0.02D;
    /** Degrees a single segment may sweep before it is cut short (keeps the whole test compact). */
    private static final double SEGMENT_TRAVEL_LIMIT_DEG = 45.0D;
    private static final double MAX_EXCURSION_DEG = 150.0D;
    /** Below this steady rate (deg/tick) the turret is considered not moving. */
    private static final double MIN_STEADY_RATE = 0.02D;
    /** Below this lag ratio the result message suggests a physical cause. */
    private static final double SLOW_HINT_FACTOR = 0.5D;

    private Phase phase = Phase.IDLE;
    private BlockPos mountPos;
    private double testRpm = TEST_RPM;
    @Nullable
    private ServerPlayer player;

    @Nullable
    private Double prevYaw;
    private double excursionDeg;
    private double segmentTravelDeg;
    private final List<Double> rates = new ArrayList<>();
    private double lastRate;
    private int stableTicks;
    private boolean moved;
    private int phaseTicks;

    private double lagOutTicks;
    private double lagBackTicks;
    private double steadyOut;
    private double steadyBack;
    private String profileOut = "";
    private String profileBack = "";

    boolean active() {
        return phase != Phase.IDLE;
    }

    /**
     * Begins a calibration run against {@code mountPos}. Returns a lang key
     * describing the failure, or {@code null} when the run started; the
     * outcome itself is reported to {@code player} when the test finishes.
     */
    @Nullable
    String start(MouseAimBlockEntity controller, BlockPos mountPos, ServerPlayer player) {
        if (active()) {
            return "vs_analog_warfare.mouse_aim.calibrate.busy";
        }
        float cap = (float) Math.min(CommonConfig.turretMaxOutputRpm(), Math.abs(controller.getSpeed()));
        if (cap < 1.0F) {
            return "vs_analog_warfare.mouse_aim.calibrate.unpowered";
        }
        this.mountPos = mountPos;
        this.player = player;
        testRpm = Math.min(TEST_RPM, cap);
        excursionDeg = 0.0D;
        prevYaw = null;
        beginPhase(Phase.OUT);
        VSAnalogWarfare.LOGGER.info("[VSAW_TURRET_CAL] {} started: cap={} rpm, cmd={} rpm",
                controller.getBlockPos(), String.format("%.1f", cap), String.format("%.1f", testRpm));
        message(player, "vs_analog_warfare.mouse_aim.calibrate.started");
        return null;
    }

    /** Stops without storing anything (mode switch, reset, screen-driven cancel). */
    void cancel() {
        phase = Phase.IDLE;
        player = null;
        prevYaw = null;
        rates.clear();
    }

    /**
     * Runs one tick of the test.
     *
     * @return the RPM to command this tick; when the run just finished or
     *         aborted, the block should hand this value to the servo as its
     *         resumed output
     */
    float tick(MouseAimBlockEntity controller) {
        Level level = controller.getLevel();
        if (level == null || phase == Phase.IDLE) {
            return 0.0F;
        }
        Double yaw = TurretYawController.measuredBoreYawDeg(level, mountPos);
        if (yaw == null) {
            return abort(controller, "vs_analog_warfare.mouse_aim.calibrate.no_bore");
        }
        if (prevYaw != null) {
            double step = wrapDegrees(yaw - prevYaw);
            excursionDeg += Math.abs(step);
            segmentTravelDeg += Math.abs(step);
            recordRate(step);
            if (excursionDeg > MAX_EXCURSION_DEG) {
                return abort(controller, "vs_analog_warfare.mouse_aim.calibrate.runaway");
            }
        }
        prevYaw = yaw;
        phaseTicks++;

        boolean settled = moved && stableTicks >= SETTLED_TICKS_NEEDED;
        boolean segmentOver = phaseTicks >= MAX_SEGMENT_TICKS
                || (phaseTicks >= MIN_SEGMENT_TICKS
                    && (settled || segmentTravelDeg >= SEGMENT_TRAVEL_LIMIT_DEG));
        if (segmentOver) {
            double lag = analyzeSegment();
            if (Double.isNaN(lag)) {
                return abort(controller, "vs_analog_warfare.mouse_aim.calibrate.stuck");
            }
            if (phase == Phase.OUT) {
                lagOutTicks = lag;
                beginPhase(Phase.BACK);
            } else {
                lagBackTicks = lag;
                return complete(controller);
            }
        }
        return phase == Phase.OUT ? (float) testRpm : (float) -testRpm;
    }

    private void recordRate(double rate) {
        // Settling may only count once the bore has reached half the commanded
        // rate — the rest period before the rise accumulates stable ticks and
        // would otherwise end the segment while the rate is still climbing.
        if (!moved && Math.abs(rate) >= testRpm * 0.15D) {
            moved = true;
        }
        double tolerance = SETTLE_ABS_TOLERANCE + SETTLE_REL_TOLERANCE * Math.abs(rate);
        if (Math.abs(rate - lastRate) <= tolerance) {
            stableTicks++;
        } else {
            stableTicks = 0;
        }
        lastRate = rate;
        rates.add(rate);
    }

    private void beginPhase(Phase next) {
        phase = next;
        rates.clear();
        stableTicks = 0;
        moved = false;
        phaseTicks = 0;
        segmentTravelDeg = 0.0D;
    }

    /**
     * Lag of the segment that just ended, in ticks. The steady rate is the
     * largest |rate| in the tail half — the rise is monotone, so the max is
     * the converged value and immune to the segment ending early. The lag is
     * then the first-order identity {@code Σ (1 − rate/steady)} over every
     * sample, each term clamped at zero so a slightly-low steady estimate
     * cannot drive tail terms negative. The return segment swings from +R to
     * −R, so its normalized rate is {@code 2e^(−t/τ)} and its raw sum is
     * exactly twice the lag — halved before returning. {@code NaN} when the
     * turret did not move.
     */
    private double analyzeSegment() {
        int n = rates.size();
        StringBuilder profile = new StringBuilder();
        for (int i = 0; i < n; i += 5) {
            if (profile.length() > 0) {
                profile.append(',');
            }
            profile.append(String.format("%.2f", rates.get(i)));
        }
        if (phase == Phase.OUT) {
            profileOut = profile.toString();
        } else {
            profileBack = profile.toString();
        }
        if (n < 2) {
            return Double.NaN;
        }
        int from = Math.max(1, n / 2);
        double steady = 0.0D;
        for (int i = from; i < n; i++) {
            steady = Math.max(steady, Math.abs(rates.get(i)));
        }
        if (phase == Phase.OUT) {
            steadyOut = steady;
        } else {
            steadyBack = steady;
        }
        if (steady < MIN_STEADY_RATE) {
            return Double.NaN;
        }
        double sign = phase == Phase.OUT ? 1.0D : -1.0D;
        double lag = 0.0D;
        for (int i = 0; i < n; i++) {
            lag += Math.max(0.0D, 1.0D - rates.get(i) / (steady * sign));
        }
        if (phase == Phase.BACK) {
            lag *= 0.5D;
        }
        return Math.max(0.5D, lag);
    }

    private float complete(MouseAimBlockEntity controller) {
        double lagTicks = Math.max(0.5D, (lagOutTicks + lagBackTicks) / 2.0D);
        // 100% while within 25% of the reference turret's lag (measurement
        // noise headroom), de-rate below that, floored at half.
        double factor = 1.25D * CommonConfig.turretCalibrationReferenceLagTicks() / lagTicks;
        TurretTuning tuned = TurretTuning.scaledBy(factor);
        controller.storeTuning(tuned);
        double appliedFactor = tuned.kp() / Math.max(1.0E-6D, TurretTuning.fromConfig().kp());
        // Always logged: one line per calibration, the primary diagnostic.
        VSAnalogWarfare.LOGGER.info(
                "[VSAW_TURRET_CAL] {} cmd={} rateOut={}/t rateBack={}/t lagOut={} lagBack={} lag={} f={} -> kp={} kd={} ff={} outRates=[{}] backRates=[{}]",
                controller.getBlockPos(), String.format("%.1f", testRpm),
                String.format("%.3f", steadyOut), String.format("%.3f", steadyBack),
                String.format("%.1f", lagOutTicks), String.format("%.1f", lagBackTicks),
                String.format("%.1f", lagTicks), String.format("%.2f->%.2f", factor, appliedFactor),
                String.format("%.3f", tuned.kp()), String.format("%.3f", tuned.kd()),
                String.format("%.3f", tuned.feedForward()), profileOut, profileBack);
        if (player != null) {
            message(player, "vs_analog_warfare.mouse_aim.calibrate.done",
                    String.format("%.0f", appliedFactor * 100.0D), String.format("%.1f", lagTicks));
            if (factor < SLOW_HINT_FACTOR) {
                player.sendSystemMessage(Component.translatable(
                        "vs_analog_warfare.mouse_aim.calibrate.slow_hint"));
            }
        }
        finish();
        return 0.0F;
    }

    private float abort(MouseAimBlockEntity controller, String key) {
        VSAnalogWarfare.LOGGER.info("[VSAW_TURRET_CAL] {} aborted: {}",
                controller.getBlockPos(), key);
        if (player != null) {
            message(player, key);
        }
        finish();
        return 0.0F;
    }

    private void finish() {
        phase = Phase.IDLE;
        player = null;
        prevYaw = null;
        rates.clear();
    }

    private static void message(ServerPlayer player, String key, Object... args) {
        player.displayClientMessage(Component.translatable(key, args), true);
    }

    private static double wrapDegrees(double value) {
        value %= 360.0D;
        if (value >= 180.0D) value -= 360.0D;
        if (value < -180.0D) value += 360.0D;
        return value;
    }
}
