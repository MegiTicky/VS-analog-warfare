package com.erika.vsanalogwarfare.mouseaim;

import com.erika.vsanalogwarfare.config.CommonConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

import javax.annotation.Nullable;

/**
 * Yaw-servo tuning for one mouse aim block: the values the controller
 * consumes each tick. A block that has never been calibrated runs the global
 * config template; a step-test calibration stores per-block values in NBT
 * (which also travels with schematic paste, so a tuned tank design keeps its
 * tuning).
 *
 * <p>The Clockwork physics bearing reaches the commanded rate exactly at
 * steady state (the calibration's measured rate always equals RPM x 0.3
 * deg/tick), but real turrets spin up over tens of ticks — the response is
 * effectively moment-dominated, and how moment-dominated varies with what the
 * assembly drags. Calibration measures that spin-up against the configured
 * reference lag ({@code calibrationReferenceLagTicks}, the turret the
 * template was hand-tuned on) and only ever de-rates {@code kp}: a softer
 * position command restores the stability margin the sluggish actuator eats,
 * while the unchanged kd keeps damping dominant. The factor is floored at
 * 0.5, so calibration can slow a turret's response but never cripple it.
 */
public record TurretTuning(double kp, double kd, double feedForward, double slewPerTick) {

    public TurretTuning {
        kp = Mth.clamp(kp, 0.0D, 10.0D);
        kd = Mth.clamp(kd, 0.0D, 10.0D);
        feedForward = Mth.clamp(feedForward, 0.0D, 10.0D);
        slewPerTick = Mth.clamp(slewPerTick, 0.0D, 64.0D);
    }

    /** The global config template. */
    public static TurretTuning fromConfig() {
        return new TurretTuning(CommonConfig.turretKp(), CommonConfig.turretKd(),
                CommonConfig.turretFeedForward(), CommonConfig.turretOutputSlewPerTick());
    }

    /**
     * De-rates the template for a measured spin-up lag ratio: {@code factor}
     * is reference lag over measured lag. Clamped to [0.5, 1] — never above
     * the template (measurement noise must not make the servo more
     * aggressive) and never below half of it (a de-rate must soften the
     * response, not cripple it). Only kp scales — see the class comment.
     */
    public static TurretTuning scaledBy(double responseFactor) {
        double f = Mth.clamp(responseFactor, 0.5D, 1.0D);
        TurretTuning template = fromConfig();
        return new TurretTuning(template.kp * f, template.kd, template.feedForward, template.slewPerTick);
    }

    public CompoundTag write() {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("Kp", kp);
        tag.putDouble("Kd", kd);
        tag.putDouble("Ff", feedForward);
        tag.putDouble("Slew", slewPerTick);
        return tag;
    }

    @Nullable
    public static TurretTuning read(@Nullable CompoundTag tag) {
        if (tag == null || !tag.contains("Kp") || !tag.contains("Kd")
                || !tag.contains("Ff") || !tag.contains("Slew")) {
            return null;
        }
        return new TurretTuning(tag.getDouble("Kp"), tag.getDouble("Kd"),
                tag.getDouble("Ff"), tag.getDouble("Slew"));
    }
}
