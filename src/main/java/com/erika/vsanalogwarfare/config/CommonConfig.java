package com.erika.vsanalogwarfare.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class CommonConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.DoubleValue MOUSE_AIM_MIN_SPEED;
    public static final ForgeConfigSpec.IntValue MOUSE_AIM_PACKET_INTERVAL_TICKS;
    public static final ForgeConfigSpec.IntValue MOUSE_AIM_TARGET_TIMEOUT_TICKS;
    public static final ForgeConfigSpec.DoubleValue MOUSE_AIM_RATE_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue MAX_RANGEFINDER_DISTANCE;
    public static final ForgeConfigSpec.BooleanValue DISABLE_CONTRAPTION_ENTITY_COLLISION;

    public static final ForgeConfigSpec.BooleanValue STABILIZER_ENABLED;
    public static final ForgeConfigSpec.DoubleValue STABILIZER_MAX_DEG_PER_TICK;
    public static final ForgeConfigSpec.DoubleValue STABILIZER_DEAD_ZONE_DEG;
    public static final ForgeConfigSpec.DoubleValue STABILIZER_LINK_RANGE;
    public static final ForgeConfigSpec.BooleanValue STABILIZER_DEBUG;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("mouseAim");
        MOUSE_AIM_MIN_SPEED = builder
                .comment("Minimum absolute Create RPM required for a mouse aim block to control an adjacent cannon mount.")
                .defineInRange("mouseAimMinSpeed", 16.0D, 0.0D, 4096.0D);
        MOUSE_AIM_PACKET_INTERVAL_TICKS = builder
                .comment("Client-to-server mouse aim target update interval while scoped free look is active.")
                .defineInRange("mouseAimPacketIntervalTicks", 2, 1, 20);
        MOUSE_AIM_TARGET_TIMEOUT_TICKS = builder
                .comment("Ticks before a mouse aim block forgets the last target if updates stop.")
                .defineInRange("mouseAimTargetTimeoutTicks", 6, 1, 100);
        MOUSE_AIM_RATE_MULTIPLIER = builder
                .comment("Multiplier applied to Create angular speed to get cannon chase rate in degrees per tick.")
                .defineInRange("mouseAimRateMultiplier", 0.125D, 0.0D, 10.0D);
        builder.pop();

        builder.push("scope");
        MAX_RANGEFINDER_DISTANCE = builder
                .comment("Maximum distance in blocks for the rangefinder to scan. Applies to terrain and ships.")
                .defineInRange("maxRangefinderDistance", 2000.0D, 10.0D, 10000.0D);
        builder.pop();

        builder.push("contraption");
        DISABLE_CONTRAPTION_ENTITY_COLLISION = builder
                .comment("When true, Create contraptions do not physically collide with entities.")
                .define("disableEntityCollision", false);
        builder.pop();

        builder.push("stabilizer");
        STABILIZER_ENABLED = builder
                .comment("Master switch for the gyro stabilizer block.")
                .define("enabled", true);
        STABILIZER_MAX_DEG_PER_TICK = builder
                .comment("Maximum compensating pitch speed the stabilizer may command, in degrees per game tick. "
                        + "Also the slew rate for large corrections.")
                .defineInRange("maxCompensationDegPerTick", 4.0D, 0.0D, 45.0D);
        STABILIZER_DEAD_ZONE_DEG = builder
                .comment("World-elevation errors smaller than this (degrees) are not corrected, preventing dither.")
                .defineInRange("deadZoneDeg", 0.02D, 0.0D, 5.0D);
        STABILIZER_LINK_RANGE = builder
                .comment("Maximum block distance between a stabilizer and its cannon mount.")
                .defineInRange("linkRange", 24.0D, 2.0D, 256.0D);
        STABILIZER_DEBUG = builder
                .comment("Log stabilizer servo state once per second per linked mount to the server log.")
                .define("debug", false);
        builder.pop();

        SPEC = builder.build();
    }

    private CommonConfig() {
    }

    public static double mouseAimMinSpeed() { return MOUSE_AIM_MIN_SPEED.get(); }
    public static int mouseAimPacketIntervalTicks() { return MOUSE_AIM_PACKET_INTERVAL_TICKS.get(); }
    public static int mouseAimTargetTimeoutTicks() { return MOUSE_AIM_TARGET_TIMEOUT_TICKS.get(); }
    public static double mouseAimRateMultiplier() { return MOUSE_AIM_RATE_MULTIPLIER.get(); }

    // Add the new getter
    public static double maxRangefinderDistance() { return MAX_RANGEFINDER_DISTANCE.get(); }
    public static boolean disableContraptionEntityCollision() { return DISABLE_CONTRAPTION_ENTITY_COLLISION.get(); }

    public static boolean stabilizerEnabled() { return STABILIZER_ENABLED.get(); }
    public static double stabilizerMaxDegPerTick() { return STABILIZER_MAX_DEG_PER_TICK.get(); }
    public static double stabilizerDeadZoneDeg() { return STABILIZER_DEAD_ZONE_DEG.get(); }
    public static double stabilizerLinkRange() { return STABILIZER_LINK_RANGE.get(); }
    public static boolean stabilizerDebug() { return STABILIZER_DEBUG.get(); }
}
