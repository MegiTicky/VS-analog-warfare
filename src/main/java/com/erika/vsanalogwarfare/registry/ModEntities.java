package com.erika.vsanalogwarfare.registry;

import com.erika.vsanalogwarfare.VSAnalogWarfare;
import com.erika.vsanalogwarfare.scope.ScopeCameraEntity;
import com.erika.vsanalogwarfare.decorationbearing.DecorationBearingContraptionEntity;
import com.erika.vsanalogwarfare.seat.InvisibleSeatEntity;
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

    public static final RegistryObject<EntityType<DecorationBearingContraptionEntity>> DECORATION_BEARING_CONTRAPTION =
            ENTITIES.register("decoration_bearing_contraption", () -> EntityType.Builder
                    .<DecorationBearingContraptionEntity>of(DecorationBearingContraptionEntity::new, MobCategory.MISC)
                    .sized(1.0f, 1.0f)
                    .clientTrackingRange(256)
                    .updateInterval(1)
                    .build(VSAnalogWarfare.MOD_ID + ":decoration_bearing_contraption"));

    public static final RegistryObject<EntityType<InvisibleSeatEntity>> INVISIBLE_SEAT =
            ENTITIES.register("invisible_seat", () -> EntityType.Builder
                    .<InvisibleSeatEntity>of(InvisibleSeatEntity::new, MobCategory.MISC)
                    .sized(0.25f, 0.35f)
                    .clientTrackingRange(5)
                    .updateInterval(Integer.MAX_VALUE)
                    .fireImmune()
                    .build(VSAnalogWarfare.MOD_ID + ":invisible_seat"));

    private ModEntities() {
    }
}
