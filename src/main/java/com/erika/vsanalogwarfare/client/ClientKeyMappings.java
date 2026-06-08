package com.erika.vsanalogwarfare.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public final class ClientKeyMappings {
    public static final KeyMapping SCOPE_ZOOM = new KeyMapping(
            "key.vs_analog_warfare.scope_zoom",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_C,
            "key.categories.vs_analog_warfare"
    );

    public static final KeyMapping SCOPE_FREE_LOOK = new KeyMapping(
            "key.vs_analog_warfare.scope_freelook",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_X,
            "key.categories.vs_analog_warfare"
    );

    public static final KeyMapping SCOPE_RANGEFINDER = new KeyMapping(
            "key.vs_analog_warfare.scope_rangefinder",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            "key.categories.vs_analog_warfare"
    );

    private ClientKeyMappings() {
    }
}
