package com.lauya.vsanalogwarfare.debug;

import com.lauya.vsanalogwarfare.VSAnalogWarfare;
import com.lauya.vsanalogwarfare.scope.ScopeSession;
import com.lauya.vsanalogwarfare.scope.ScopeSessionManager;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Locale;
import java.util.Optional;

@Mod.EventBusSubscriber(modid = VSAnalogWarfare.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AccuracyOverrideDebug {
    private static boolean enabled = false;
    private static double searchRadius = 96.0;

    private AccuracyOverrideDebug() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("vsaw_accuracy_override")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("enable").executes(ctx -> {
                    enabled = true;
                    ctx.getSource().sendSuccess(() -> statusComponent(), true);
                    return 1;
                }))
                .then(Commands.literal("disable").executes(ctx -> {
                    enabled = false;
                    ctx.getSource().sendSuccess(() -> statusComponent(), true);
                    return 1;
                }))
                .then(Commands.literal("status").executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> statusComponent(), false);
                    return enabled ? 1 : 0;
                }))
                .then(Commands.literal("radius")
                        .then(Commands.argument("blocks", DoubleArgumentType.doubleArg(1.0, 512.0)).executes(ctx -> {
                            searchRadius = DoubleArgumentType.getDouble(ctx, "blocks");
                            ctx.getSource().sendSuccess(() -> statusComponent(), true);
                            return 1;
                        }))));
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!enabled || event.getLevel().isClientSide()) {
            return;
        }
        Entity entity = event.getEntity();
        if (!isCannonProjectile(entity)) {
            return;
        }

        Vec3 velocity = entity.getDeltaMovement();
        double speed = velocity.length();
        if (speed < 1.0e-5) {
            return;
        }

        Optional<ScopeSession> session = ScopeSessionManager.nearestSession(event.getLevel(), entity.position(), searchRadius);
        if (session.isEmpty()) {
            VSAnalogWarfare.LOGGER.debug("[AccuracyOverride] no active scope session near projectile {} at {}", entity.getType(), entity.blockPosition());
            return;
        }

        Vec3 aim = directionFromPose(session.get().currentPose().yaw(), session.get().currentPose().pitch()).normalize();
        if (aim.lengthSqr() < 1.0e-8) {
            return;
        }
        entity.setDeltaMovement(aim.scale(speed));
        entity.hasImpulse = true;
        VSAnalogWarfare.LOGGER.info("[AccuracyOverride] corrected {} speed={} aim=({}, {}, {}) nearScope={} nearMount={}",
                entity.getType(), String.format(Locale.ROOT, "%.4f", speed),
                String.format(Locale.ROOT, "%.4f", aim.x), String.format(Locale.ROOT, "%.4f", aim.y), String.format(Locale.ROOT, "%.4f", aim.z),
                session.get().scopePos(), session.get().mountPos());
    }

    private static Component statusComponent() {
        return Component.literal("VSAW temporary accuracy override: " + (enabled ? "enabled" : "disabled")
                + ", radius=" + String.format(Locale.ROOT, "%.1f", searchRadius) + " blocks");
    }

    private static Vec3 directionFromPose(float yaw, float pitch) {
        double yawRad = Math.toRadians(yaw + 90.0f);
        double pitchRad = Math.toRadians(pitch);
        double horizontal = Math.cos(pitchRad);
        return new Vec3(Math.cos(yawRad) * horizontal, -Math.sin(pitchRad), Math.sin(yawRad) * horizontal);
    }

    private static boolean isCannonProjectile(Entity entity) {
        String name = entity.getClass().getName().toLowerCase(Locale.ROOT);
        if (!name.contains("projectile")) {
            return false;
        }
        return name.contains("createbigcannons")
                || name.contains("cbcmoreshells")
                || name.contains("cbcmodernwarfare")
                || name.contains("riftyboi");
    }
}
