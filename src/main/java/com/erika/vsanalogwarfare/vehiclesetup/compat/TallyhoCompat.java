package com.erika.vsanalogwarfare.vehiclesetup.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;

import javax.annotation.Nullable;
import java.lang.reflect.Field;

/** Optional reflection bridge for Tallyho's hull-mounted machine gun. */
public final class TallyhoCompat {
    private static final String MOD_ID = "tallyho";
    private static final ResourceLocation HULL_MG = new ResourceLocation(MOD_ID, "hull_mg");
    private static final String HULL_MG_CLASS = "edn.stratodonut.tallyho.camera.entity.HullMachineGunEntity";

    private TallyhoCompat() { }

    public static boolean isHullMachineGun(Entity entity) {
        return isLoaded() && HULL_MG.equals(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
    }

    public static int muzzleOffset(Entity entity) {
        try {
            Field field = entity.getClass().getDeclaredField("muzzle_offset");
            field.setAccessible(true);
            return field.getInt(entity);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return 0;
        }
    }

    @Nullable
    public static String spawnHullMg(Level level, BlockPos position, float yaw, int muzzleOffset) {
        if (!isLoaded()) return "Tallyho is not installed";
        if (!(level instanceof ServerLevel serverLevel)) return "hull MGs can only be spawned on the server";
        try {
            Class<?> type = Class.forName(HULL_MG_CLASS);
            Object entity = VehicleSetupReflection.invokeStatic(type, "spawn", serverLevel, position, yaw, muzzleOffset);
            return entity == null ? "Tallyho hull MG spawn returned no entity" : null;
        } catch (ReflectiveOperationException | LinkageError error) {
            return "Tallyho hull MG integration failed: " + error.getClass().getSimpleName();
        }
    }

    private static boolean isLoaded() {
        return ModList.get() != null && ModList.get().isLoaded(MOD_ID);
    }
}
