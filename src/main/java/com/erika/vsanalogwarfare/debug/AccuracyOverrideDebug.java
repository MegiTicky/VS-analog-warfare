package com.erika.vsanalogwarfare.debug;

import com.erika.vsanalogwarfare.VSAnalogWarfare;
import com.erika.vsanalogwarfare.scope.compat.CbcCompat;
import com.erika.vsanalogwarfare.scope.compat.CbcCompat.MountMatchResult;
import com.erika.vsanalogwarfare.scope.compat.VsCompat;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = VSAnalogWarfare.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AccuracyOverrideDebug {
    private static final Map<CannonMod, Boolean> ENABLED_MODS = new ConcurrentHashMap<>();
    private static double searchRadius = 96.0;
    private static boolean requireScope = true;

    private AccuracyOverrideDebug() {}

    public static boolean isEnabled() {
        return ENABLED_MODS.values().stream().anyMatch(Boolean::booleanValue);
    }

    public static boolean isEnabled(CannonMod mod) {
        return ENABLED_MODS.getOrDefault(mod, false);
    }

    public static void setEnabled(CannonMod mod, boolean enabled) {
        ENABLED_MODS.put(mod, enabled);
    }

    public static boolean requireScope() {
        return requireScope;
    }

    public static void setRequireScope(boolean value) {
        requireScope = value;
    }

    public static double searchRadius() {
        return searchRadius;
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("vsaw_accuracy_override")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("enable")
                        .then(Commands.argument("mod", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    for (CannonMod mod : CannonMod.values()) {
                                        builder.suggest(mod.name().toLowerCase(Locale.ROOT));
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    String modName = StringArgumentType.getString(ctx, "mod");
                                    CannonMod mod = CannonMod.fromString(modName);
                                    if (mod == null) {
                                        ctx.getSource().sendFailure(Component.literal("Unknown mod: " + modName));
                                        return 0;
                                    }
                                    setEnabled(mod, true);
                                    ctx.getSource().sendSuccess(() -> statusComponent(), true);
                                    return 1;
                                })))
                .then(Commands.literal("disable")
                        .then(Commands.argument("mod", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    for (CannonMod mod : CannonMod.values()) {
                                        builder.suggest(mod.name().toLowerCase(Locale.ROOT));
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    String modName = StringArgumentType.getString(ctx, "mod");
                                    CannonMod mod = CannonMod.fromString(modName);
                                    if (mod == null) {
                                        ctx.getSource().sendFailure(Component.literal("Unknown mod: " + modName));
                                        return 0;
                                    }
                                    setEnabled(mod, false);
                                    ctx.getSource().sendSuccess(() -> statusComponent(), true);
                                    return 1;
                                })))
                .then(Commands.literal("all")
                        .then(Commands.literal("enable")
                                .executes(ctx -> {
                                    for (CannonMod mod : CannonMod.values()) {
                                        setEnabled(mod, true);
                                    }
                                    ctx.getSource().sendSuccess(() -> statusComponent(), true);
                                    return 1;
                                }))
                        .then(Commands.literal("disable")
                                .executes(ctx -> {
                                    for (CannonMod mod : CannonMod.values()) {
                                        setEnabled(mod, false);
                                    }
                                    ctx.getSource().sendSuccess(() -> statusComponent(), true);
                                    return 1;
                                })))
                .then(Commands.literal("status")
                        .executes(ctx -> {
                            ctx.getSource().sendSuccess(() -> statusComponent(), false);
                            return isEnabled() ? 1 : 0;
                        }))
                .then(Commands.literal("list")
                        .executes(ctx -> {
                            ctx.getSource().sendSuccess(() -> listComponent(), false);
                            return 1;
                        }))
                .then(Commands.literal("radius")
                        .then(Commands.argument("blocks", DoubleArgumentType.doubleArg(1.0, 512.0))
                                .executes(ctx -> {
                                    searchRadius = DoubleArgumentType.getDouble(ctx, "blocks");
                                    ctx.getSource().sendSuccess(() -> statusComponent(), true);
                                    return 1;
                                })))
                .then(Commands.literal("global")
                        .then(Commands.argument("value", BoolArgumentType.bool())
                                .executes(ctx -> {
                                    requireScope = BoolArgumentType.getBool(ctx, "value");
                                    ctx.getSource().sendSuccess(() -> statusComponent(), true);
                                    return 1;
                                }))));
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        Entity entity = event.getEntity();
        CannonMod mod = getCannonMod(entity);
        if (mod == null) {
            return;
        }
        if (!isEnabled(mod)) {
            return;
        }
        Level level = event.getLevel();
        Vec3 velocity = entity.getDeltaMovement();
        double speed = velocity.length();
        if (speed < 1.0e-5) {
            return;
        }
        
        Vec3 velocityDir = velocity.normalize();
        Vec3 projectileWorldPos = entity.position();
        VSAnalogWarfare.LOGGER.info("[AccuracyOverride] projectile {} at pos {} velocity=({}, {}, {}) dir=({}, {}, {})",
                entity.getType(), entity.blockPosition(),
                String.format(Locale.ROOT, "%.4f", velocity.x), String.format(Locale.ROOT, "%.4f", velocity.y), String.format(Locale.ROOT, "%.4f", velocity.z),
                String.format(Locale.ROOT, "%.4f", velocityDir.x), String.format(Locale.ROOT, "%.4f", velocityDir.y), String.format(Locale.ROOT, "%.4f", velocityDir.z));
        
        Optional<MountMatchResult> matchResult = CbcCompat.findMountByAimDirectionGlobal(level, velocityDir, projectileWorldPos);
        if (matchResult.isEmpty()) {
            VSAnalogWarfare.LOGGER.info("[AccuracyOverride] No cannon mount found matching projectile velocity direction");
            return;
        }
        
        MountMatchResult result = matchResult.get();
        BlockPos mountPos = result.mountPos();
        Vec3 aim = result.aimDirection().normalize();
        
        Optional<Long> shipId = VsCompat.findShipId(level, mountPos);
        boolean isOnShip = shipId.isPresent();
        
        if (aim.lengthSqr() < 1.0e-8) {
            return;
        }
        entity.setDeltaMovement(aim.scale(speed));
        entity.hasImpulse = true;
        VSAnalogWarfare.LOGGER.info("[AccuracyOverride] corrected {} speed={} aim=({}, {}, {}) mountPos={} onShip={} matchScore={}",
                entity.getType(), String.format(Locale.ROOT, "%.4f", speed),
                String.format(Locale.ROOT, "%.4f", aim.x), String.format(Locale.ROOT, "%.4f", aim.y), String.format(Locale.ROOT, "%.4f", aim.z),
                mountPos, isOnShip, String.format(Locale.ROOT, "%.4f", result.matchScore()));
    }

    private static Component statusComponent() {
        StringBuilder sb = new StringBuilder("VSAW accuracy override:\n");
        sb.append("  Mods: ");
        boolean anyEnabled = false;
        for (CannonMod mod : CannonMod.values()) {
            boolean enabled = isEnabled(mod);
            if (enabled) {
                anyEnabled = true;
            }
            sb.append(mod.name().toLowerCase(Locale.ROOT)).append("=").append(enabled ? "ON" : "OFF").append(" ");
        }
        sb.append("\n");
        sb.append("  Mode: ").append(requireScope ? "requires scope" : "global (no scope needed)");
        sb.append("\n");
        sb.append("  Radius: ").append(String.format(Locale.ROOT, "%.1f", searchRadius)).append(" blocks");
        sb.append("\n");
        sb.append("  Status: ").append(anyEnabled ? "ENABLED" : "DISABLED");
        return Component.literal(sb.toString());
    }

    private static Component listComponent() {
        StringBuilder sb = new StringBuilder("VSAW accuracy override - available mods:\n");
        for (CannonMod mod : CannonMod.values()) {
            sb.append("  ").append(mod.name().toLowerCase(Locale.ROOT)).append(" - ").append(mod.description()).append(" [").append(isEnabled(mod) ? "ON" : "OFF").append("]\n");
        }
        sb.append("\nUsage:\n");
        sb.append("  /vsaw_accuracy_override enable <mod>\n");
        sb.append("  /vsaw_accuracy_override disable <mod>\n");
        sb.append("  /vsaw_accuracy_override all enable\n");
        sb.append("  /vsaw_accuracy_override global true/false\n");
        return Component.literal(sb.toString());
    }

    private static CannonMod getCannonMod(Entity entity) {
        String name = entity.getClass().getName().toLowerCase(Locale.ROOT);
        if (!name.contains("projectile")) {
            return null;
        }
        if (name.contains("createbigcannons")) {
            return CannonMod.CREATE_BIG_CANNONS;
        }
        if (name.contains("cbcmoreshells")) {
            return CannonMod.CBC_MORE_SHELLS;
        }
        if (name.contains("cbcmodernwarfare")) {
            return CannonMod.CBC_MODERN_WARFARE;
        }
        if (name.contains("riftyboi")) {
            return CannonMod.RIFTY_BOI;
        }
        return null;
    }

    static {
        for (CannonMod mod : CannonMod.values()) {
            ENABLED_MODS.put(mod, false);
        }
    }

    public enum CannonMod {
        CREATE_BIG_CANNONS("Create Big Cannons"),
        CBC_MORE_SHELLS("CBC More Shells"),
        CBC_MODERN_WARFARE("CBC Modern Warfare"),
        RIFTY_BOI("Rifty Boi");

        private final String description;

        CannonMod(String description) {
            this.description = description;
        }

        public String description() {
            return description;
        }

        public static CannonMod fromString(String name) {
            for (CannonMod mod : CannonMod.values()) {
                if (mod.name().equalsIgnoreCase(name)) {
                    return mod;
                }
            }
            return null;
        }
    }
}