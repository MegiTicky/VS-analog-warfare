package com.erika.vsanalogwarfare.client;

import com.erika.vsanalogwarfare.VSAnalogWarfare;
import com.erika.vsanalogwarfare.registry.ModEntities;
import com.erika.vsanalogwarfare.registry.ModBlockEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = VSAnalogWarfare.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {
    private ClientModEvents() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.SCOPE_CAMERA.get(), ScopeCameraRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.VEHICLE_MOUNT_HANDLE.get(), VehicleMountHandleRenderer::new);
    }

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(ClientKeyMappings.SCOPE_ZOOM);
        event.register(ClientKeyMappings.SCOPE_FREE_LOOK);
        event.register(ClientKeyMappings.SCOPE_RANGEFINDER);
        event.register(ClientKeyMappings.SCOPE_ZEROING);
        event.register(ClientKeyMappings.VEHICLE_MOUNT);
    }

    @SubscribeEvent
    public static void registerGuiOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "analog_screwdriver",
                AnalogScrewdriverOverlay::render);
    }
}
