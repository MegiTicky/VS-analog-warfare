package com.erika.vsanalogwarfare.mixin.compat;

import com.erika.vsanalogwarfare.vehiclesetup.VehicleSetupRecordingManager;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

@Pseudo
@Mixin(targets = "edn.stratodonut.trackwork.items.TrackToolkit", remap = false)
public class TrackworkTrackToolkitMixin {
    @WrapOperation(
            method = "onItemUseFirst",
            at = @At(
                    value = "INVOKE",
                    target = "Ledn/stratodonut/trackwork/tracks/forces/PhysicsTrackController;setDamperCoefficient(F)F",
                    remap = false),
            remap = false)
    private float vsaw$recordStiffness(@Coerce Object controller, float stiffness, Operation<Float> original,
                                       ItemStack stack, UseOnContext context) {
        float applied = original.call(controller, stiffness);
        recordStiffness(stack, context, stiffness);
        return applied;
    }

    @WrapOperation(
            method = "onItemUseFirst",
            at = @At(
                    value = "INVOKE",
                    target = "Ledn/stratodonut/trackwork/tracks/forces/SimpleWheelController;setDamperCoefficient(F)F",
                    remap = false),
            remap = false)
    private float vsaw$recordWheelStiffness(@Coerce Object controller, float stiffness, Operation<Float> original,
                                            ItemStack stack, UseOnContext context) {
        float applied = original.call(controller, stiffness);
        recordStiffness(stack, context, stiffness);
        return applied;
    }

    private static void recordStiffness(ItemStack stack, UseOnContext context, float stiffness) {
        if (context.getPlayer() != null) {
            VehicleSetupRecordingManager.recordTrackworkStiffness(
                    context.getPlayer(), context.getClickedPos(), stack, stiffness);
        }
    }
}
