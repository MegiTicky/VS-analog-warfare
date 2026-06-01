package com.erika.vsanalogwarfare.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class CommonConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.DoubleValue MOUSE_AIM_MIN_SPEED;
    public static final ForgeConfigSpec.IntValue MOUSE_AIM_PACKET_INTERVAL_TICKS;
    public static final ForgeConfigSpec.IntValue MOUSE_AIM_TARGET_TIMEOUT_TICKS;
    public static final ForgeConfigSpec.DoubleValue MOUSE_AIM_RATE_MULTIPLIER;

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
        SPEC = builder.build();
    }

    private CommonConfig() {
    }

    public static double mouseAimMinSpeed() {
        return MOUSE_AIM_MIN_SPEED.get();
    }

    public static int mouseAimPacketIntervalTicks() {
        return MOUSE_AIM_PACKET_INTERVAL_TICKS.get();
    }

    public static int mouseAimTargetTimeoutTicks() {
        return MOUSE_AIM_TARGET_TIMEOUT_TICKS.get();
    }

    public static double mouseAimRateMultiplier() {
        return MOUSE_AIM_RATE_MULTIPLIER.get();
    }
}
