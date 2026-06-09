package com.erika.vsanalogwarfare;

import com.erika.vsanalogwarfare.client.ScopeDebug;
import com.erika.vsanalogwarfare.config.ClientConfig;
import com.erika.vsanalogwarfare.config.CommonConfig;
import com.erika.vsanalogwarfare.network.ModNetwork;
import com.erika.vsanalogwarfare.registry.ModBlockEntities;
import com.erika.vsanalogwarfare.registry.ModBlocks;
import com.erika.vsanalogwarfare.registry.ModEntities;
import com.erika.vsanalogwarfare.registry.ModItems;
import net.minecraftforge.api.distmarker.Dist;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

//just for debugging a modpack issue
/*import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
@Mod.EventBusSubscriber(modid = "vs_analog_warfare", bus = Mod.EventBusSubscriber.Bus.MOD)*/

@Mod(VSAnalogWarfare.MOD_ID)
public class VSAnalogWarfare {
    public static final String MOD_ID = "vs_analog_warfare";
    public static final Logger LOGGER = LogUtils.getLogger();

    public VSAnalogWarfare() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        ModEntities.ENTITIES.register(modBus);
        ModNetwork.register();
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, CommonConfig.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ScopeDebug::logLoaded);
    }

    /*@SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        System.out.println("[DEBUG] Checking for null blocks in BlockItems...");
        BuiltInRegistries.ITEM.forEach(item -> {
            if (item instanceof BlockItem blockItem) {
                if (blockItem.getBlock() == null) {
                    System.err.println("[BAD BLOCKITEM FOUND]: " + BuiltInRegistries.ITEM.getKey(item));
                }
            }
        });
    }*/
}
