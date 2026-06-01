package com.erika.vsanalogwarfare.registry;

import com.erika.vsanalogwarfare.VSAnalogWarfare;
import com.erika.vsanalogwarfare.mouseaim.MouseAimBlock;
import com.erika.vsanalogwarfare.scope.ScopeBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, VSAnalogWarfare.MOD_ID);

    public static final RegistryObject<Block> SCOPE_BLOCK = BLOCKS.register("scope_block",
            () -> new ScopeBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(1.5f, 6.0f)
                    .noOcclusion()));

    public static final RegistryObject<Block> MOUSE_AIM_BLOCK = BLOCKS.register("mouse_aim_block",
            () -> new MouseAimBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(1.5f, 6.0f)
                    .noOcclusion()));

    private ModBlocks() {
    }
}
