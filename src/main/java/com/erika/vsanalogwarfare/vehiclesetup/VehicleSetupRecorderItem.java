package com.erika.vsanalogwarfare.vehiclesetup;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
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
        return fail(player, "That block is not a supported vehicle setup target.");
    }

    @Nullable private static VehicleSetupBlockEntity selected(Level level, ItemStack recorder) {
        if (!recorder.hasTag() || !recorder.getTag().contains(ANCHOR)) return null;
        BlockEntity entity = level.getBlockEntity(BlockPos.of(recorder.getTag().getLong(ANCHOR)));
        return entity instanceof VehicleSetupBlockEntity setup ? setup : null;
    }
    private static InteractionResult success(ServerPlayer player, String message) { player.displayClientMessage(Component.literal(message), true); return InteractionResult.CONSUME; }
    private static InteractionResult fail(ServerPlayer player, String message) { player.displayClientMessage(Component.literal(message), true); return InteractionResult.FAIL; }
}
