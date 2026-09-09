package com.erika.vsanalogwarfare.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class ClientConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.DoubleValue SCOPE_ZOOM_SENSITIVITY_MULTIPLIER;
    public static final ForgeConfigSpec.BooleanValue DISABLE_PLAYER_BLOCK_INTERACTION_WHILE_SCOPED;
    public static final ForgeConfigSpec.IntValue ZEROING_STEP;
    public static final ForgeConfigSpec.BooleanValue IGNORE_TALLYHO_ENTITY_PLACEMENT;
    public static final ForgeConfigSpec.BooleanValue SCOPE_MOUNTED_ROTATION_COMPENSATION;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("scope");
        SCOPE_ZOOM_SENSITIVITY_MULTIPLIER = builder
                .comment("Additional multiplier applied after scope zoom/FOV mouse sensitivity scaling.")
                .defineInRange("scopeZoomSensitivityMultiplier", 0.65D, 0.05D, 2.0D);
        DISABLE_PLAYER_BLOCK_INTERACTION_WHILE_SCOPED = builder
                .comment("When true, left/right click block interactions are disabled while the player is using a scope.")
                .define("disablePlayerBlockInteractionWhileScoped", true);
        ZEROING_STEP = builder
                .comment("Distance increment (in meters) when adjusting sight zero with scroll wheel while holding the zeroing key.")
                .defineInRange("zeroingStep", 50, 10, 500);
        SCOPE_MOUNTED_ROTATION_COMPENSATION = builder
                .comment("When true and the player is seated on a ship, the scope camera pre-divides its world-frame\n"
                        + "orientation by the ship's render rotation so Valkyrien Skies' mounted-camera transform\n"
                        + "cancels exactly. Keeps free-look and the scope locked to world-space angles on ships.")
                .define("scopeMountedRotationCompensation", true);
        builder.pop();
        builder.push("actionToIgnore");
        IGNORE_TALLYHO_ENTITY_PLACEMENT = builder
                .comment("When true, generic Tallyho entity placement is ignored by Vehicle Setup. Tallyho entities can still be recorded with the Analog Screwdriver.")
                .define("ignoreTallyhoEntityPlacement", false);
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

    public static int zeroingStep() {
        return ZEROING_STEP.get();
    }

    public static boolean ignoreTallyhoEntityPlacement() {
        return IGNORE_TALLYHO_ENTITY_PLACEMENT.get();
    }

    public static boolean scopeMountedRotationCompensation() {
        return SCOPE_MOUNTED_ROTATION_COMPENSATION.get();
    }
}
