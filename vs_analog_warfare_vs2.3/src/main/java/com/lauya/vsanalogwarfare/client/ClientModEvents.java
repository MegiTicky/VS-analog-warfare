package com.lauya.vsanalogwarfare.client;

import com.lauya.vsanalogwarfare.VSAnalogWarfare;
import com.lauya.vsanalogwarfare.registry.ModEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = VSAnalogWarfare.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {
    private ClientModEvents() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.SCOPE_CAMERA.get(), ScopeCameraRenderer::new);
    }

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(ClientKeyMappings.SCOPE_ZOOM);
        event.register(ClientKeyMappings.SCOPE_FREE_LOOK);
    }
}
