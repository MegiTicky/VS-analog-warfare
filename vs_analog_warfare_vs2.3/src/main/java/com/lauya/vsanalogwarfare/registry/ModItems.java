package com.lauya.vsanalogwarfare.registry;

import com.lauya.vsanalogwarfare.VSAnalogWarfare;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, VSAnalogWarfare.MOD_ID);

    public static final RegistryObject<Item> SCOPE_BLOCK = ITEMS.register("scope_block",
            () -> new BlockItem(ModBlocks.SCOPE_BLOCK.get(), new Item.Properties()));

    public static final RegistryObject<Item> MOUSE_AIM_BLOCK = ITEMS.register("mouse_aim_block",
            () -> new BlockItem(ModBlocks.MOUSE_AIM_BLOCK.get(), new Item.Properties()));

    private ModItems() {
    }

    @Mod.EventBusSubscriber(modid = VSAnalogWarfare.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class CreativeTabs {
        @SubscribeEvent
        public static void addToTabs(BuildCreativeModeTabContentsEvent event) {
            if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
                event.accept(SCOPE_BLOCK);
                event.accept(MOUSE_AIM_BLOCK);
            }
        }
    }
}
