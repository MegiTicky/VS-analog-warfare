package com.erika.vsanalogwarfare.mixin.compat;

import com.erika.vsanalogwarfare.vehiclesetup.VehicleSetupRecordingManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "edn.stratodonut.drivebywire.blocks.TweakedControllerHubBlock", remap = false)
public class DbwTweakedControllerHubMixin {
    @Inject(method = "m_6227_", at = @At("RETURN"), remap = false)
    private void vsaw$recordController(BlockState state, Level level, BlockPos pos, Player player,
                                       InteractionHand hand, BlockHitResult hit,
                                       CallbackInfoReturnable<InteractionResult> callback) {
        if (!level.isClientSide && callback.getReturnValue() == InteractionResult.SUCCESS) {
            ItemStack controller = player.getItemInHand(hand);
            if (controller.hasTag() && controller.getTag().contains("Hub")
                    && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                VehicleSetupRecordingManager.recordControllerLink(serverPlayer, pos, controller);
            }
        }
    }
}
