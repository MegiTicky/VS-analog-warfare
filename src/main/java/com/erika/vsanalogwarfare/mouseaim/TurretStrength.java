package com.erika.vsanalogwarfare.mouseaim;

import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.utility.Lang;

/**
 * Output aggressiveness of the turret-mode yaw controller, selectable through
 * Create's {@code ScrollOptionBehaviour} value-settings UI.
 *
 * <p>The RPM output is a velocity command: the Clockwork physics bearing
 * converts it into torque against the turret's inertia internally. Strength
 * therefore tunes the control loop, not the raw torque — it scales the PID
 * gains (making the turret chase the aim harder) together with the maximum
 * output RPM (limiting the commanded slew). Fine tuning of the individual
 * gains lives in the common config under {@code turretAim}.
 */
public enum TurretStrength implements INamedIconOptions {
    GENTLE(AllIcons.I_PRIORITY_VERY_LOW, 0.25D, 8.0F),
    FIRM(AllIcons.I_PRIORITY_LOW, 0.5D, 16.0F),
    AGGRESSIVE(AllIcons.I_PRIORITY_HIGH, 1.0D, 24.0F),
    BRUTAL(AllIcons.I_PRIORITY_VERY_HIGH, 2.0D, 32.0F);

    private final String translationKey;
    private final AllIcons icon;
    private final double gainMultiplier;
    private final float maxRpm;

    TurretStrength(AllIcons icon, double gainMultiplier, float maxRpm) {
        this.icon = icon;
        this.gainMultiplier = gainMultiplier;
        this.maxRpm = maxRpm;
        this.translationKey = "vs_analog_warfare.mouse_aim.strength." + Lang.asId(name());
    }

    /** Scales the PID controller output. */
    public double gainMultiplier() {
        return gainMultiplier;
    }

    /** Clamp on the commanded output, in Create RPM. */
    public float maxRpm() {
        return maxRpm;
    }

    @Override
    public AllIcons getIcon() {
        return icon;
    }

    @Override
    public String getTranslationKey() {
        return translationKey;
    }
}
