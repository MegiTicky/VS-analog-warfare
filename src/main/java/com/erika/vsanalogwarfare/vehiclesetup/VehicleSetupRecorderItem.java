package com.erika.vsanalogwarfare.vehiclesetup;

import com.erika.vsanalogwarfare.vehiclesetup.compat.OptionalModCompatibility;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import javax.annotation.Nullable;

public class VehicleSetupRecorderItem extends Item {
    private static final String ANCHOR = "VehicleSetupAnchor";
    public VehicleSetupRecorderItem(Properties properties) { super(properties); }

    @Override public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide || !(context.getPlayer() instanceof ServerPlayer player)) return InteractionResult.SUCCESS;
        ItemStack recorder = context.getItemInHand();
        BlockPos clicked = context.getClickedPos();
        if (level.getBlockEntity(clicked) instanceof VehicleSetupBlockEntity setupBlock) {
            OptionalModCompatibility.warnIfIssues(player);
            recorder.getOrCreateTag().putLong(ANCHOR, clicked.asLong());
            if (player.isShiftKeyDown()) VehicleSetupRecordingManager.inspect(player, setupBlock);
            else VehicleSetupRecordingManager.toggle(player, setupBlock);
            return InteractionResult.CONSUME;
        }
        return fail(player, "Start recording, then use DBW and Trackwork tools normally.");
    }

    private static InteractionResult success(ServerPlayer player, String message) { player.displayClientMessage(Component.literal(message), true); return InteractionResult.CONSUME; }
    private static InteractionResult fail(ServerPlayer player, String message) { player.displayClientMessage(Component.literal(message), true); return InteractionResult.FAIL; }
}
