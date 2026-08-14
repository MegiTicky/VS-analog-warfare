package com.erika.vsanalogwarfare.vehiclesetup.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;
import javax.annotation.Nullable;
import java.lang.reflect.Field;

public final class TrackworkCompat {
    private TrackworkCompat() { }
    @Nullable public static Float readStiffness(Level level, BlockPos pos) {
        if (!ModList.get().isLoaded("trackwork")) return null;
        try { Object controller = controller(level, pos); if (controller == null) return null; Field field = controller.getClass().getDeclaredField("suspensionStiffness"); field.setAccessible(true); return field.getFloat(controller); }
        catch (ReflectiveOperationException | LinkageError ignored) { return null; }
    }
    @Nullable public static String setStiffness(Level level, BlockPos pos, float stiffness) {
        if (!ModList.get().isLoaded("trackwork")) return "Trackwork is not installed";
        try { Object controller = controller(level, pos); if (controller == null) return "no ship at setup block"; VehicleSetupReflection.invoke(controller, "setDamperCoefficient", stiffness); return null; }
        catch (ReflectiveOperationException | LinkageError error) { return "Trackwork integration failed: " + error.getClass().getSimpleName(); }
    }
    @Nullable private static Object controller(Level level, BlockPos pos) throws ReflectiveOperationException {
        Object ship = VehicleSetupReflection.findShip(level, pos); if (ship == null) return null;
        return VehicleSetupReflection.invokeStatic(Class.forName("edn.stratodonut.trackwork.tracks.forces.PhysicsTrackController"), "getOrCreate", ship);
    }
}
