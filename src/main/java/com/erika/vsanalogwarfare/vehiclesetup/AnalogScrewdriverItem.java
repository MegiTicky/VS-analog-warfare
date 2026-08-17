package com.erika.vsanalogwarfare.vehiclesetup;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.erika.vsanalogwarfare.vehiclesetup.compat.EnderTransmissionCompat;

public class AnalogScrewdriverItem extends Item {
    private static final String ANCHOR = "VehicleSetupAnchor";
    private static final String ENDER_PAIR_SOURCE = "VSAWEnderPairSource";

    public AnalogScrewdriverItem(Properties properties) { super(properties); }

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
        if (EnderTransmissionCompat.isEnergyTransmitter(level.getBlockState(clicked))) {
            CompoundTag tag = recorder.getOrCreateTag();
            if (player.isShiftKeyDown()) {
                tag.putLong(ENDER_PAIR_SOURCE, clicked.asLong());
                player.displayClientMessage(Component.literal(
                        "Ender transmitter selected. Right-click another transmitter to pair it."), true);
                return InteractionResult.CONSUME;
            }
            if (tag.contains(ENDER_PAIR_SOURCE)) {
                BlockPos source = BlockPos.of(tag.getLong(ENDER_PAIR_SOURCE));
                String error = EnderTransmissionCompat.pair(level, source, clicked);
                tag.remove(ENDER_PAIR_SOURCE);
                player.displayClientMessage(Component.literal(error == null
                        ? "Ender transmitters paired."
                        : "Ender transmitter pairing failed: " + error), true);
                return error == null ? InteractionResult.CONSUME : InteractionResult.FAIL;
            }
            player.displayClientMessage(Component.literal(
                    "Sneak-right-click an Ender transmitter first to select it for pairing."), true);
            return InteractionResult.FAIL;
        }
        player.displayClientMessage(Component.literal(
                "Start recording, then use DBW and Trackwork tools normally."), true);
        return InteractionResult.FAIL;
    }
}
