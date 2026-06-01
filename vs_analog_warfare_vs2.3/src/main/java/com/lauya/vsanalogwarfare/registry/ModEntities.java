package com.lauya.vsanalogwarfare.registry;

import com.lauya.vsanalogwarfare.VSAnalogWarfare;
import com.lauya.vsanalogwarfare.scope.ScopeCameraEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, VSAnalogWarfare.MOD_ID);

    public static final RegistryObject<EntityType<ScopeCameraEntity>> SCOPE_CAMERA =
            ENTITIES.register("scope_camera",
                    () -> EntityType.Builder.<ScopeCameraEntity>of(ScopeCameraEntity::new, MobCategory.MISC)
                            .sized(0.1f, 0.1f)
                            .clientTrackingRange(256)
                            .updateInterval(1)
                            .noSave()
                            .build(VSAnalogWarfare.MOD_ID + ":scope_camera"));

    private ModEntities() {
    }
}
