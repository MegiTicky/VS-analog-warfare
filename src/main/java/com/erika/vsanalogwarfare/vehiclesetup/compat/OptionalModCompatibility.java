package com.erika.vsanalogwarfare.vehiclesetup.compat;

import com.erika.vsanalogwarfare.VSAnalogWarfare;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class OptionalModCompatibility {
    private static final Set<UUID> WARNED = ConcurrentHashMap.newKeySet();

    private OptionalModCompatibility() { }

    public static void warnIfIssues(ServerPlayer player) {
        List<String> warnings = new ArrayList<>();
        if (!ModList.get().isLoaded("drivebywire")) {
            warnings.add("Drive By Wire is not installed; DBW links and controllers will not work.");
        }
        if (!ModList.get().isLoaded("trackwork")) {
            warnings.add("Trackwork is not installed; suspension settings will not work.");
        }
        if (!ModList.get().isLoaded("valkyrien_mod")) {
            warnings.add("VMod is not installed; automatic setup after schematic placement is unavailable.");
        }
        if (!ModList.get().isLoaded("create_tweaked_controllers")
                || BuiltInRegistries.ITEM.get(new ResourceLocation(
                "create_tweaked_controllers", "tweaked_linked_controller")) == Items.AIR) {
            warnings.add("Create Tweaked Controllers is not installed; controller links will not work.");
        }
        if (warnings.isEmpty() || !WARNED.add(player.getUUID())) return;
        player.displayClientMessage(Component.literal("[Vehicle Setup]").withStyle(ChatFormatting.GOLD), false);
        for (String warning : warnings) {
            player.displayClientMessage(Component.literal("- " + warning).withStyle(ChatFormatting.YELLOW), false);
        }
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
