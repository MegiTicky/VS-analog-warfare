package com.erika.vsanalogwarfare.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class ClientConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.DoubleValue SCOPE_ZOOM_SENSITIVITY_MULTIPLIER;
    public static final ForgeConfigSpec.BooleanValue DISABLE_PLAYER_BLOCK_INTERACTION_WHILE_SCOPED;
    public static final ForgeConfigSpec.IntValue ZEROING_STEP;
    public static final ForgeConfigSpec.DoubleValue MAX_ZERO_PITCH_DEGREES;
    public static final ForgeConfigSpec.BooleanValue IGNORE_TALLYHO_ENTITY_PLACEMENT;
    public static final ForgeConfigSpec.BooleanValue SCOPE_MOUNTED_ROTATION_COMPENSATION;
    public static final ForgeConfigSpec.DoubleValue SCOPE_THIRD_PERSON_CAMERA_LIFT;
    public static final ForgeConfigSpec.DoubleValue FREE_LOOK_TETHER_DEGREES;
    public static final ForgeConfigSpec.BooleanValue VIRTUALIZE_PLAYER_LOOK_WHILE_SCOPED;

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
        MAX_ZERO_PITCH_DEGREES = builder
                .comment("Highest elevation (degrees) the high-angle (artillery) zero branch may command. Zeroing past\n"
                        + "the cannon's maximum range flips to the high arc and keeps elevating up to this limit.\n"
                        + "Match this to your CBC cannon mount's elevation limit, otherwise solutions above the\n"
                        + "physical limit will not converge.")
                .defineInRange("maxZeroPitchDegrees", 89.0D, 45.0D, 89.0D);
        SCOPE_MOUNTED_ROTATION_COMPENSATION = builder
                .comment("When true and the player is seated on a ship, the scope camera pre-divides its world-frame\n"
                        + "orientation by the ship's render rotation so Valkyrien Skies' mounted-camera transform\n"
                        + "cancels exactly. Keeps free-look and the scope locked to world-space angles on ships.")
                .define("scopeMountedRotationCompensation", true);
        SCOPE_THIRD_PERSON_CAMERA_LIFT = builder
                .comment("World-Y lift (blocks) applied to the third-person camera while a scope session is\n"
                        + "toggled to third-person view, so the vehicle hull does not block the view.")
                .defineInRange("scopeThirdPersonCameraLift", 2.0D, 0.0D, 16.0D);
        FREE_LOOK_TETHER_DEGREES = builder
                .comment("War Thunder style mouse-aim tether: how far (degrees) the scope free-look camera may\n"
                        + "stray from the crosshair before it hits the boundary. Mouse input past the edge is\n"
                        + "absorbed (the cursor pins and slides along it), and hull rotation that carries the\n"
                        + "crosshair further away drags the camera along so the turret is never left behind.\n"
                        + "180 or more disables the tether.")
                .defineInRange("freeLookTetherDegrees", 15.0D, 5.0D, 180.0D);
        VIRTUALIZE_PLAYER_LOOK_WHILE_SCOPED = builder
                .comment("While a scope session is active, the local player's eye position and look vector report the\n"
                        + "scope view's world-frame ray. Mods that aim from the player entity (Ping Wheel, Steve's Army\n"
                        + "pings, crosshair targeting) then aim at what the reticle points at instead of the frozen\n"
                        + "first-person head. Disable if another mod misbehaves under the virtual look.")
                .define("virtualizePlayerLookWhileScoped", true);
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

    public static double maxZeroPitchDegrees() {
        return MAX_ZERO_PITCH_DEGREES.get();
    }

    public static boolean ignoreTallyhoEntityPlacement() {
        return IGNORE_TALLYHO_ENTITY_PLACEMENT.get();
    }

    public static boolean scopeMountedRotationCompensation() {
        return SCOPE_MOUNTED_ROTATION_COMPENSATION.get();
    }

    public static double scopeThirdPersonCameraLift() {
        return SCOPE_THIRD_PERSON_CAMERA_LIFT.get();
    }

    public static double freeLookTetherDegrees() {
        return FREE_LOOK_TETHER_DEGREES.get();
    }

    public static boolean virtualizePlayerLookWhileScoped() {
        return VIRTUALIZE_PLAYER_LOOK_WHILE_SCOPED.get();
    }
}
