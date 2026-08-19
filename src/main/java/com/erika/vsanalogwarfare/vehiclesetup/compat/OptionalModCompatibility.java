package com.erika.vsanalogwarfare.vehiclesetup.compat;

import com.erika.vsanalogwarfare.VSAnalogWarfare;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class OptionalModCompatibility {
    private OptionalModCompatibility() { }

    public enum State { OK, MISSING, UNTESTED, BROKEN }

    public static final class Integration {
        final String modId;
        final String displayName;
        final String testedVersion;
        final String description;
        final Supplier<Boolean> apiCheck;
        volatile State state = State.MISSING;
        @Nullable volatile String installedVersion;

        Integration(String modId, String displayName, String testedVersion, String description, Supplier<Boolean> apiCheck) {
            this.modId = modId;
            this.displayName = displayName;
            this.testedVersion = testedVersion;
            this.description = description;
            this.apiCheck = apiCheck;
        }
    }

    private static final List<Integration> INTEGRATIONS = List.of(
            new Integration("drivebywire", "Drive By Wire", "0.0.6b",
                    "DBW backup relinks and controller links cannot be recorded or replayed.",
                    () -> {
                        try {
                            Class<?> manager = Class.forName("edn.stratodonut.drivebywire.wire.ShipWireNetworkManager");
                            if (!hasMethod(manager, "linkNetwork", 3)) return false;
                            Class<?> hub = Class.forName("edn.stratodonut.drivebywire.blocks.ControllerHubBlock");
                            return hasUseMethod(hub);
                        } catch (Throwable ignored) { return false; }
                    }),
            new Integration("trackwork", "Trackwork", "1.0.2c",
                    "suspension stiffness cannot be recorded or applied.",
                    () -> {
                        try {
                            Class.forName("edn.stratodonut.trackwork.items.TrackToolkit");
                            Class.forName("edn.stratodonut.trackwork.tracks.blocks.TrackBaseBlock");
                            Class.forName("edn.stratodonut.trackwork.tracks.forces.PhysicsTrackController");
                            return true;
                     } catch (Throwable ignored) { return false; }
                     }),
            new Integration("createendertransmission", "Create Ender Transmission", "2.0.7-1.20.1",
                    "recorded energy transmitters cannot be isolated when vehicle schematics are pasted.",
                    () -> {
                        try {
                            Class<?> transmitter = Class.forName(
                                    "com.forsteri.createendertransmission.blocks.energyTransmitter.EnergyTransmitterBlockEntity");
                            return hasMethod(transmitter, "reloadSettings", 0)
                                    && hasMethod(transmitter, "afterReload", 0);
                        } catch (Throwable ignored) { return false; }
                    }),
            new Integration("valkyrien_mod", "Valkyrien Mod (schematics)", "0.1.3",
                    "automatic vehicle setup after schematic placement is disabled.",
                    () -> {
                        try {
                            return hasMethod(Class.forName("net.spaceeye.vmod.schematic.SchematicActionsQueue"),
                                    "queueShipsCreationEvent", 5);
                        } catch (Throwable ignored) { return false; }
                    }),
            new Integration("create_tweaked_controllers", "Create Tweaked Controllers", "1.20.1-1.2.4",
                    "recorded tweaked controllers cannot be re-linked after placement.",
                    () -> {
                        try {
                            return !BuiltInRegistries.ITEM.get(
                                    new ResourceLocation("create_tweaked_controllers", "tweaked_linked_controller"))
                                    .equals(Items.AIR);
                        } catch (Throwable ignored) { return false; }
                    })
    );

    private static final Set<UUID> WARNED = ConcurrentHashMap.newKeySet();
    private static volatile boolean evaluated = false;

    /** Sends a single compatibility warning block to the player if any integration is missing, untested, or broken. */
    public static void warnIfIssues(ServerPlayer player) {
        List<String> warnings = warnings();
        if (warnings.isEmpty() || !WARNED.add(player.getUUID())) return;
        player.displayClientMessage(Component.literal("[Vehicle Setup]").withStyle(ChatFormatting.GOLD), false);
        for (String warning : warnings) {
            player.displayClientMessage(Component.literal("- " + warning).withStyle(ChatFormatting.YELLOW), false);
        }
        player.displayClientMessage(Component.literal(
                "Update the listed mods to the validated versions, or leave the vehicle setup block out of your schematic.").withStyle(ChatFormatting.YELLOW), false);
    }

    public static List<String> warnings() {
        ensureEvaluated();
        List<String> warnings = new ArrayList<>();
        for (Integration integration : INTEGRATIONS) {
            switch (integration.state) {
                case MISSING -> warnings.add(integration.displayName + " is not installed — " + integration.description);
                case UNTESTED -> warnings.add(integration.displayName + " " + integration.installedVersion
                        + " is untested (validated against " + integration.testedVersion + ") — features may not work.");
                case BROKEN -> warnings.add(integration.displayName + " is incompatible with this build — " + integration.description);
                case OK -> { }
            }
        }
        return warnings;
    }

    private static synchronized void ensureEvaluated() {
        if (evaluated) return;
        ModList modList = ModList.get();
        for (Integration integration : INTEGRATIONS) {
            String version = modList.isLoaded(integration.modId)
                    ? modList.getModContainerById(integration.modId)
                            .map(container -> container.getModInfo().getVersion().toString()).orElse(null)
                    : null;
            if (version == null) { integration.state = State.MISSING; continue; }
            integration.installedVersion = version;
            boolean apiOk;
            try { apiOk = Boolean.TRUE.equals(integration.apiCheck.get()); }
            catch (Throwable ignored) { apiOk = false; }
            if (!apiOk) integration.state = State.BROKEN;
            else if (!versionMatches(version, integration.testedVersion)) integration.state = State.UNTESTED;
            else integration.state = State.OK;
        }
        evaluated = true;
    }

    private static boolean versionMatches(String installed, String tested) {
        return installed != null && (installed.equals(tested)
                || installed.startsWith(tested + "-") || installed.startsWith(tested + "+"));
    }

    private static boolean hasMethod(Class<?> type, String name, int parameterCount) {
        for (Method method : type.getDeclaredMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == parameterCount) return true;
        }
        return false;
    }

    private static boolean hasUseMethod(Class<?> type) {
        for (Method method : type.getDeclaredMethods()) {
            if (method.getParameterCount() == 6 && method.getReturnType() == InteractionResult.class) return true;
        }
        return false;
    }

    @Mod.EventBusSubscriber(modid = VSAnalogWarfare.MOD_ID)
    public static final class Events {
        private Events() { }

        @SubscribeEvent
        public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) warnIfIssues(player);
        }
    }
}
