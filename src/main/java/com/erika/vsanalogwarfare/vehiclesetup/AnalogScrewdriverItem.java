package com.erika.vsanalogwarfare.vehiclesetup;

import com.erika.vsanalogwarfare.vehiclesetup.compat.OptionalModCompatibility;
import com.erika.vsanalogwarfare.vehiclesetup.compat.EnderTransmissionCompat;
import com.erika.vsanalogwarfare.vehiclemount.VehicleMountHandleBlockEntity;
import com.erika.vsanalogwarfare.vehiclemount.VehicleMountManager;
import com.simibubi.create.content.contraptions.actors.seat.SeatBlock;
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

import javax.annotation.Nullable;

public class AnalogScrewdriverItem extends Item {
    private static final String ANCHOR = "VehicleSetupAnchor";
    private static final String ENDER_PAIR_SOURCE = "VSAWEnderPairSource";
    private static final String REMOVAL_MODE = "VSAWRemovalMode";
    public AnalogScrewdriverItem(Properties properties) { super(properties); }

    @Override public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide || !(context.getPlayer() instanceof ServerPlayer player)) return InteractionResult.SUCCESS;
        ItemStack recorder = context.getItemInHand();
        BlockPos clicked = context.getClickedPos();
        if (player.isShiftKeyDown() && recorder.getOrCreateTag().getLong("VehicleMountHandle") != 0L) {
            if (level.getBlockState(clicked).getBlock() instanceof SeatBlock
                    && VehicleMountManager.tryOpenSeatRoleName(player, recorder, clicked)) return InteractionResult.CONSUME;
        }
        if (level.getBlockEntity(clicked) instanceof GroundCollisionDisablerBlockEntity collisionDisabler) {
            return collisionDisabler.enableGroundCollision((net.minecraft.server.level.ServerLevel) level, player)
                    ? InteractionResult.CONSUME : InteractionResult.FAIL;
        }
        if (level.getBlockEntity(clicked) instanceof VehicleSetupBlockEntity setupBlock) {
            OptionalModCompatibility.warnIfIssues(player);
            recorder.getOrCreateTag().putLong(ANCHOR, clicked.asLong());
            if (removalMode(recorder)) VehicleSetupRecordingManager.toggleRemovalRecording(player, setupBlock);
            else if (player.isShiftKeyDown()) VehicleSetupRecordingManager.inspect(player, setupBlock);
            else VehicleSetupRecordingManager.toggle(player, setupBlock);
            return InteractionResult.CONSUME;
        }
        if (level.getBlockEntity(clicked) instanceof VehicleMountHandleBlockEntity) {
            if (((VehicleMountHandleBlockEntity) level.getBlockEntity(clicked)).locked()) {
                player.displayClientMessage(Component.literal("This vehicle mount handle is locked."), true);
                return InteractionResult.FAIL;
            }
            recorder.getOrCreateTag().putLong("VehicleMountHandle", clicked.asLong());
            player.displayClientMessage(Component.literal("Handle selected. Right-click a Create seat with the screwdriver to link it."), true);
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
            return fail(player, "Sneak-right-click an Ender transmitter first to select it for pairing.");
        }
        return InteractionResult.PASS;
    }

    public static boolean removalMode(ItemStack stack) { return stack.getOrCreateTag().getBoolean(REMOVAL_MODE); }
    public static void setRemovalMode(ItemStack stack, boolean removalMode) { stack.getOrCreateTag().putBoolean(REMOVAL_MODE, removalMode); }

    private static InteractionResult success(ServerPlayer player, String message) { player.displayClientMessage(Component.literal(message), true); return InteractionResult.CONSUME; }
    private static InteractionResult fail(ServerPlayer player, String message) { player.displayClientMessage(Component.literal(message), true); return InteractionResult.FAIL; }
}
