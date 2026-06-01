package com.erika.vsanalogwarfare.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class ClientConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.DoubleValue SCOPE_ZOOM_SENSITIVITY_MULTIPLIER;
    public static final ForgeConfigSpec.BooleanValue DISABLE_PLAYER_BLOCK_INTERACTION_WHILE_SCOPED;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("scope");
        SCOPE_ZOOM_SENSITIVITY_MULTIPLIER = builder
                .comment("Additional multiplier applied after scope zoom/FOV mouse sensitivity scaling.")
                .defineInRange("scopeZoomSensitivityMultiplier", 0.65D, 0.05D, 2.0D);
        DISABLE_PLAYER_BLOCK_INTERACTION_WHILE_SCOPED = builder
                .comment("When true, left/right click block interactions are disabled while the player is using a scope.")
                .define("disablePlayerBlockInteractionWhileScoped", true);
        builder.pop();
        SPEC = builder.build();
    }

    private ClientConfig() {
    }

    public static double scopeZoomSensitivityMultiplier() {
        return SCOPE_ZOOM_SENSITIVITY_MULTIPLIER.get();
    }

    public static boolean disablePlayerBlockInteractionWhileScoped() {
        return DISABLE_PLAYER_BLOCK_INTERACTION_WHILE_SCOPED.get();
    }
}
