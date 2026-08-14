package com.erika.vsanalogwarfare.vehiclesetup;

import com.erika.vsanalogwarfare.vehiclesetup.compat.TrackworkCompat;
import com.erika.vsanalogwarfare.vehiclesetup.compat.VehicleSetupShipPosition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import javax.annotation.Nullable;

public class VehicleSetupRecorderItem extends Item {
    private static final String ANCHOR = "VehicleSetupAnchor";
    private static final String BACKUP = "PendingDbwBackup";
    public VehicleSetupRecorderItem(Properties properties) { super(properties); }

    @Override public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide || !(context.getPlayer() instanceof ServerPlayer player)) return InteractionResult.SUCCESS;
        ItemStack recorder = context.getItemInHand();
        BlockPos clicked = context.getClickedPos();
        if (level.getBlockEntity(clicked) instanceof VehicleSetupBlockEntity setupBlock) {
            recorder.getOrCreateTag().putLong(ANCHOR, clicked.asLong());
            if (player.isShiftKeyDown()) VehicleSetupRecordingManager.inspect(player, setupBlock);
            else VehicleSetupRecordingManager.toggle(player, setupBlock);
            return InteractionResult.CONSUME;
        }
        VehicleSetupBlockEntity setup = selected(level, recorder);
        if (setup == null) return fail(player, "Select a Vehicle Setup Block first.");
        String id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(clicked).getBlock()).toString();
        if ("drivebywire:tweaked_controller_hub".equals(id)) {
            ItemStack controller = player.getOffhandItem();
            if (!"create_tweaked_controllers:tweaked_linked_controller".equals(BuiltInRegistries.ITEM.getKey(controller.getItem()).toString())) {
                return fail(player, "Hold the configured tweaked controller in your offhand.");
            }
            setup.addAction(VehicleSetupAction.createTweakedController(clicked.subtract(setup.getBlockPos()), controller));
            return success(player, "Recorded tweaked controller hub.");
        }
        if ("drivebywire:backup_block".equals(id)) return backup(level, recorder, setup, clicked, player);
        return fail(player, "That block is not a supported vehicle setup target.");
    }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !player.isShiftKeyDown() || !(player instanceof ServerPlayer serverPlayer)) return InteractionResultHolder.pass(stack);
        VehicleSetupBlockEntity setup = selected(level, stack);
        Float stiffness = setup == null ? null : TrackworkCompat.readStiffness(level, setup.getBlockPos());
        if (stiffness == null) return InteractionResultHolder.fail(stack);
        setup.addAction(VehicleSetupAction.setTrackworkStiffness(stiffness));
        serverPlayer.displayClientMessage(Component.literal("Recorded Trackwork stiffness: " + stiffness), true);
        return InteractionResultHolder.consume(stack);
    }

    private InteractionResult backup(Level level, ItemStack recorder, VehicleSetupBlockEntity setup, BlockPos clicked, ServerPlayer player) {
        CompoundTag tag = recorder.getOrCreateTag();
        if (!tag.contains(BACKUP)) { tag.putLong(BACKUP, clicked.asLong()); return success(player, "Selected source DBW backup block."); }
        BlockPos source = BlockPos.of(tag.getLong(BACKUP)); tag.remove(BACKUP);
        VehicleSetupShipPosition first = VehicleSetupShipPosition.at(level, source);
        VehicleSetupShipPosition second = VehicleSetupShipPosition.at(level, clicked);
        if (first == null || second == null) return fail(player, "Both backups must belong to loaded ships.");
        setup.addAction(VehicleSetupAction.linkDbwBackups(first.shipId(), first.offset(), second.shipId(), second.offset()));
        return success(player, "Recorded DBW backup link.");
    }

    @Nullable private static VehicleSetupBlockEntity selected(Level level, ItemStack recorder) {
        if (!recorder.hasTag() || !recorder.getTag().contains(ANCHOR)) return null;
        BlockEntity entity = level.getBlockEntity(BlockPos.of(recorder.getTag().getLong(ANCHOR)));
        return entity instanceof VehicleSetupBlockEntity setup ? setup : null;
    }
    private static InteractionResult success(ServerPlayer player, String message) { player.displayClientMessage(Component.literal(message), true); return InteractionResult.CONSUME; }
    private static InteractionResult fail(ServerPlayer player, String message) { player.displayClientMessage(Component.literal(message), true); return InteractionResult.FAIL; }
}
