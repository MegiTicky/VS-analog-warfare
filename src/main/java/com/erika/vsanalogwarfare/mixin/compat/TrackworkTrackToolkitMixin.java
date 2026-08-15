package com.erika.vsanalogwarfare.mixin.compat;

import com.erika.vsanalogwarfare.vehiclesetup.VehicleSetupRecordingManager;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "edn.stratodonut.trackwork.items.TrackToolkit", remap = false)
public class TrackworkTrackToolkitMixin {
    @Inject(method = "onItemUseFirst", at = @At("RETURN"), remap = false)
    private void vsaw$recordStiffness(ItemStack stack, UseOnContext context,
                                      CallbackInfoReturnable<InteractionResult> callback) {
        if (callback.getReturnValue() == InteractionResult.SUCCESS && context.getPlayer() != null
                && context.getPlayer() instanceof net.minecraft.server.level.ServerPlayer player) {
            VehicleSetupRecordingManager.recordTrackworkStiffness(player, context.getClickedPos(), stack);
        }
    }
}
