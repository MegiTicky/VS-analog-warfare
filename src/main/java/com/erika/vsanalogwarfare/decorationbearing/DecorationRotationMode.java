package com.erika.vsanalogwarfare.decorationbearing;

import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.utility.Lang;

/**
 * Rotation modes for the decoration bearing, selectable through Create's
 * {@code ScrollOptionBehaviour} wrench value-settings UI.
 * <p>
 * {@code ScrollOptionBehaviour<E>} is declared
 * {@code E extends Enum<E> & INamedIconOptions}, so the enum must provide an
 * {@link AllIcons} icon and a translation key — mirrors Create's own
 * {@code IControlContraption.RotationMode}.
 */
public enum DecorationRotationMode implements INamedIconOptions {
    YAW_ONLY(AllIcons.I_TOOL_ROTATE),
    PITCH_ONLY(AllIcons.I_TOOL_MOVE_Y),
    YAW_AND_PITCH(AllIcons.I_FOLLOW_DIAGONAL);

    private final String translationKey;
    private final AllIcons icon;

    DecorationRotationMode(AllIcons icon) {
        this.icon = icon;
        this.translationKey = "vsanalogwarfare.decoration_bearing.rotation_mode." + Lang.asId(name());
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
