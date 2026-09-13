package com.erika.vsanalogwarfare.vehiclesetup.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;
import javax.annotation.Nullable;

public final class TrackworkCompat {
    private TrackworkCompat() { }
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
            Class<?> wheel = Class.forName("edn.stratodonut.trackwork.tracks.blocks.WheelBlock");
            Object block = level.getBlockState(pos).getBlock();
            return trackBase.isInstance(block) || wheel.isInstance(block);
        } catch (ClassNotFoundException | LinkageError ignored) { return false; }
    }
    @Nullable public static String setStiffness(Level level, BlockPos pos, float stiffness) {
        if (!ModList.get().isLoaded("trackwork")) return "Trackwork is not installed";
        try { Object controller = controller(level, pos); if (controller == null) return "no ship at setup block"; VehicleSetupReflection.invoke(controller, "setDamperCoefficient", stiffness); return null; }
        catch (ReflectiveOperationException | LinkageError error) { return "Trackwork integration failed: " + error.getClass().getSimpleName(); }
    }
    @Nullable private static Object controller(Level level, BlockPos pos) throws ReflectiveOperationException {
        Object ship = VehicleSetupReflection.findShip(level, pos); if (ship == null) return null;
        Object block = level.getBlockState(pos).getBlock();
        Class<?> controller = Class.forName("edn.stratodonut.trackwork.tracks.forces.PhysicsTrackController");
        if (Class.forName("edn.stratodonut.trackwork.tracks.blocks.WheelBlock").isInstance(block)) {
            controller = Class.forName("edn.stratodonut.trackwork.tracks.forces.SimpleWheelController");
        }
        return VehicleSetupReflection.invokeStatic(controller, "getOrCreate", ship);
    }
}
