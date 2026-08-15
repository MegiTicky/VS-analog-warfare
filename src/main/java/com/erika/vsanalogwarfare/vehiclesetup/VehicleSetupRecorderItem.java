package com.erika.vsanalogwarfare.vehiclesetup;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public class VehicleSetupRecorderItem extends Item {
    private static final String ANCHOR = "VehicleSetupAnchor";

    public VehicleSetupRecorderItem(Properties properties) { super(properties); }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }
        ItemStack recorder = context.getItemInHand();
        BlockPos clicked = context.getClickedPos();
        BlockEntity entity = level.getBlockEntity(clicked);
        if (entity instanceof VehicleSetupBlockEntity setup) {
            recorder.getOrCreateTag().putLong(ANCHOR, clicked.asLong());
            if (player.isShiftKeyDown()) VehicleSetupRecordingManager.inspect(player, setup);
            else VehicleSetupRecordingManager.toggle(player, setup);
            return InteractionResult.CONSUME;
        }
        player.displayClientMessage(Component.literal(
                "Start recording, then use DBW and Trackwork tools normally."), true);
        return InteractionResult.FAIL;
    }
}
