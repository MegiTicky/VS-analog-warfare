package com.erika.vsanalogwarfare.vehiclesetup.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
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
    public static boolean isStiffnessTool(ItemStack stack) {
        if (!ModList.get().isLoaded("trackwork")) return false;
        try {
            net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.get(new net.minecraft.resources.ResourceLocation("trackwork", "track_tool_kit"));
            return stack.is(item) && stack.hasTag() && stack.getTag().getInt("Tool") == 0;
        } catch (LinkageError ignored) { return false; }
    }
    public static boolean isStiffnessTarget(Level level, BlockPos pos) {
        if (!ModList.get().isLoaded("trackwork")) return false;
        try {
            Class<?> trackBase = Class.forName("edn.stratodonut.trackwork.tracks.blocks.TrackBaseBlock");
            return trackBase.isInstance(level.getBlockState(pos).getBlock());
        } catch (ClassNotFoundException | LinkageError ignored) { return false; }
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
