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
    private static final String BASE_YAW_FIELD = "BASE_YAW";
    private static final String MUZZLE_OFFSET_FIELD = "muzzle_offset";

    private TallyhoCompat() { }

    /** Placement data needed to recreate a hull MG: its support seat, fixed base yaw, and muzzle offset. */
    public record CapturedHullMg(BlockPos supportPosition, float baseYaw, int muzzleOffset) { }

    public static boolean isHullMachineGun(Entity entity) {
        return isLoaded() && HULL_MG.equals(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
    }

    /**
     * Captures the placement data of a hull MG. Tallyho anchors the MG to a
     * FlexibleSeatEntity whose block position is the placement point, so the
     * support position is taken from that seat rather than the MG itself. The
     * fixed yaw comes from the persisted BASE_YAW instead of the player-aimed
     * current rotation. Returns null when the MG is not supported or is not
     * mounted, so recording is skipped.
     */
    @Nullable
    public static CapturedHullMg capture(Entity weapon) {
        Entity seat = weapon.getVehicle();
        if (!isHullMachineGun(weapon) || seat == null) return null;
        Integer muzzleOffset = readIntField(weapon, MUZZLE_OFFSET_FIELD);
        if (muzzleOffset == null) return null;
        Float baseYaw = readFloatField(weapon, BASE_YAW_FIELD);
        return new CapturedHullMg(seat.blockPosition(), baseYaw == null ? weapon.getYRot() : baseYaw, muzzleOffset);
    }

    @Nullable
    public static String spawnHullMg(Level level, BlockPos position, float yaw, int muzzleOffset) {
        if (!isLoaded()) return "Tallyho is not installed";
        if (!(level instanceof ServerLevel serverLevel)) return "hull MGs can only be spawned on the server";
        try {
            Class<?> type = Class.forName(HULL_MG_CLASS);
            Object spawned = VehicleSetupReflection.invokeStatic(type, "spawn", serverLevel, position, yaw, muzzleOffset);
            if (!(spawned instanceof Entity entity) || !entity.isAlive() || entity.getVehicle() == null) {
                return "Tallyho hull MG spawn did not leave a mounted weapon";
            }
            return null;
        } catch (ReflectiveOperationException | LinkageError error) {
            return "Tallyho hull MG integration failed: " + error.getClass().getSimpleName();
        }
    }

    @Nullable private static Integer readIntField(Entity entity, String name) {
        try {
            Field field = findField(entity.getClass(), name);
            field.setAccessible(true);
            return field.getInt(entity);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    @Nullable private static Float readFloatField(Entity entity, String name) {
        try {
            Field field = findField(entity.getClass(), name);
            field.setAccessible(true);
            return field.getFloat(entity);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> current = type; current != null && current != Entity.class; current = current.getSuperclass()) {
            try { return current.getDeclaredField(name); } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }

    private static boolean isLoaded() {
        return ModList.get() != null && ModList.get().isLoaded(MOD_ID);
    }
}