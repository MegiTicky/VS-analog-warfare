package com.lauya.vsanalogwarfare;

import com.lauya.vsanalogwarfare.client.ScopeDebug;
import com.lauya.vsanalogwarfare.config.ClientConfig;
import com.lauya.vsanalogwarfare.config.CommonConfig;
import com.lauya.vsanalogwarfare.network.ModNetwork;
import com.lauya.vsanalogwarfare.registry.ModBlockEntities;
import com.lauya.vsanalogwarfare.registry.ModBlocks;
import com.lauya.vsanalogwarfare.registry.ModEntities;
import com.lauya.vsanalogwarfare.registry.ModItems;
import net.minecraftforge.api.distmarker.Dist;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

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
}
