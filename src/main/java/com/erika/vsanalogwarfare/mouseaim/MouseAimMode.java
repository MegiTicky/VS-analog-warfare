package com.erika.vsanalogwarfare.mouseaim;

import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.utility.Lang;

/**
 * Aiming modes for the mouse aim block, selectable through Create's
 * {@code ScrollOptionBehaviour} value-settings UI (hold right click).
 *
 * <ul>
 *   <li>{@link #CANNON} — the classic behaviour: mouse aim slews both the yaw
 *       and the pitch of the adjacent CBC cannon mount.</li>
 *   <li>{@link #TURRET} — mouse aim slews only the mount pitch; yaw becomes a
 *       PID-controlled Create rotation output on the block's arrow face that
 *       the player pipes into a Clockwork physics bearing to rotate the whole
 *       turret structure.</li>
 * </ul>
 */
public enum MouseAimMode implements INamedIconOptions {
    CANNON(AllIcons.I_FOLLOW_DIAGONAL),
    TURRET(AllIcons.I_TOOL_ROTATE);

    private final String translationKey;
    private final AllIcons icon;

    MouseAimMode(AllIcons icon) {
        this.icon = icon;
        this.translationKey = "vs_analog_warfare.mouse_aim.mode." + Lang.asId(name());
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
