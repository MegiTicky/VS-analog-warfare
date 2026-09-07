package com.erika.vsanalogwarfare.registry;

import com.erika.vsanalogwarfare.VSAnalogWarfare;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModCreativeModeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, VSAnalogWarfare.MOD_ID);

    public static final RegistryObject<CreativeModeTab> MAIN = CREATIVE_MODE_TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.vs_analog_warfare.main"))
                    .icon(() -> new ItemStack(ModItems.ANALOG_SCREWDRIVER.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.SCOPE_BLOCK.get());
                        output.accept(ModItems.MOUSE_AIM_BLOCK.get());
                        output.accept(ModItems.VEHICLE_SETUP.get());
                        output.accept(ModItems.GROUND_COLLISION_DISABLER.get());
                        output.accept(ModItems.VEHICLE_MOUNT_HANDLE.get());
                        output.accept(ModItems.DECORATION_BEARING.get());
                        output.accept(ModItems.STABILIZER.get());
                        output.accept(ModItems.INVISIBLE_SEAT.get());
                        output.accept(ModItems.ANALOG_SCREWDRIVER.get());
                    })
                    .build());

    private ModCreativeModeTabs() {
    }
}
