package com.erika.vsanalogwarfare.registry;

import com.erika.vsanalogwarfare.VSAnalogWarfare;
import com.erika.vsanalogwarfare.mouseaim.MouseAimBlockEntity;
import com.erika.vsanalogwarfare.scope.ScopeBlockEntity;
import com.erika.vsanalogwarfare.vehiclesetup.VehicleSetupBlockEntity;
import com.erika.vsanalogwarfare.vehiclesetup.GroundCollisionDisablerBlockEntity;
import com.erika.vsanalogwarfare.vehiclemount.VehicleMountHandleBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, VSAnalogWarfare.MOD_ID);

    public static final RegistryObject<BlockEntityType<ScopeBlockEntity>> SCOPE =
            BLOCK_ENTITIES.register("scope",
                    () -> BlockEntityType.Builder.of(ScopeBlockEntity::new, ModBlocks.SCOPE_BLOCK.get()).build(null));

    public static final RegistryObject<BlockEntityType<MouseAimBlockEntity>> MOUSE_AIM =
            BLOCK_ENTITIES.register("mouse_aim",
                    () -> BlockEntityType.Builder.of(MouseAimBlockEntity::new, ModBlocks.MOUSE_AIM_BLOCK.get()).build(null));

    public static final RegistryObject<BlockEntityType<VehicleSetupBlockEntity>> VEHICLE_SETUP =
            BLOCK_ENTITIES.register("vehicle_setup",
                    () -> BlockEntityType.Builder.of(VehicleSetupBlockEntity::new, ModBlocks.VEHICLE_SETUP.get()).build(null));

    public static final RegistryObject<BlockEntityType<GroundCollisionDisablerBlockEntity>> GROUND_COLLISION_DISABLER =
            BLOCK_ENTITIES.register("ground_collision_disabler",
                    () -> BlockEntityType.Builder.of(GroundCollisionDisablerBlockEntity::new,
                            ModBlocks.GROUND_COLLISION_DISABLER.get()).build(null));

    public static final RegistryObject<BlockEntityType<VehicleMountHandleBlockEntity>> VEHICLE_MOUNT_HANDLE =
            BLOCK_ENTITIES.register("vehicle_mount_handle", () -> BlockEntityType.Builder.of(
                    VehicleMountHandleBlockEntity::new, ModBlocks.VEHICLE_MOUNT_HANDLE.get()).build(null));

    private ModBlockEntities() {
    }
}
