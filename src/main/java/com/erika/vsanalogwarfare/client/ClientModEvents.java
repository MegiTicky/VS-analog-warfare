package com.erika.vsanalogwarfare.client;

import com.erika.vsanalogwarfare.VSAnalogWarfare;
import com.erika.vsanalogwarfare.ponder.VehicleSetupPonder;
import com.erika.vsanalogwarfare.registry.ModEntities;
import com.erika.vsanalogwarfare.registry.ModBlockEntities;
import com.erika.vsanalogwarfare.seat.InvisibleSeatEntity;
import com.jozufozu.flywheel.backend.instancing.InstancedRenderRegistry;
import com.simibubi.create.content.contraptions.bearing.BearingInstance;
import com.simibubi.create.content.contraptions.bearing.BearingRenderer;
import com.simibubi.create.content.contraptions.render.ContraptionEntityRenderer;
import com.simibubi.create.content.kinetics.base.ShaftInstance;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = VSAnalogWarfare.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {
    private ClientModEvents() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.SCOPE_CAMERA.get(), ScopeCameraRenderer::new);
        event.registerEntityRenderer(ModEntities.DECORATION_BEARING_CONTRAPTION.get(), ContraptionEntityRenderer::new);
        event.registerEntityRenderer(ModEntities.INVISIBLE_SEAT.get(), InvisibleSeatEntity.Render::new);
        event.registerBlockEntityRenderer(ModBlockEntities.DECORATION_BEARING.get(), BearingRenderer::new);
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

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        VehicleSetupPonder.register();
        InstancedRenderRegistry.configure(ModBlockEntities.DECORATION_BEARING.get())
                .factory(BearingInstance::new)
                .apply();
        // Rotating shaft visual through the Mouse Aim Controller's casing
        // (same machinery that renders the decoration bearing's shaft).
        InstancedRenderRegistry.configure(ModBlockEntities.MOUSE_AIM.get())
                .factory(ShaftInstance::new)
                .apply();
    }
}
