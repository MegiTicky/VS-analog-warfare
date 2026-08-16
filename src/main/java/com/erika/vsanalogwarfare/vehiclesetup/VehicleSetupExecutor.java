package com.erika.vsanalogwarfare.vehiclesetup;

import com.erika.vsanalogwarfare.vehiclesetup.compat.TrackworkCompat;
import com.erika.vsanalogwarfare.vehiclesetup.compat.TallyhoCompat;
import com.erika.vsanalogwarfare.vehiclesetup.compat.VehicleSetupReflection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.ModList;
import java.util.Map;

import javax.annotation.Nullable;

public final class VehicleSetupExecutor {
    private static final ResourceLocation TWEAKED_CONTROLLER = new ResourceLocation("create_tweaked_controllers", "tweaked_linked_controller");
    private VehicleSetupExecutor() { }

    @Nullable
    public static String run(Level level, BlockPos anchor, @Nullable ServerPlayer player, VehicleSetupAction action) {
        return run(level, anchor, player, action, null);
    }

    @Nullable
    public static String run(Level level, BlockPos anchor, @Nullable ServerPlayer player, VehicleSetupAction action,
                             @Nullable Map<Long, Object> ships) {
        return switch (action.type()) {
            case PLACE_BLOCK -> place(level, target(level, anchor, action, ships), action.blockState());
            case REMOVE_BLOCK -> remove(level, target(level, anchor, action, ships));
            case LINK_DBW_BACKUPS -> "DBW cross-ship links require VMod placement";
            case CREATE_TWEAKED_CONTROLLER -> controller(player, action, ships);
            case SET_TRACKWORK_STIFFNESS -> TrackworkCompat.setStiffness(level, anchor, action.stiffness());
            case SPAWN_TALLYHO_HULL_MG -> TallyhoCompat.spawnHullMg(level, target(level, anchor, action, ships),
                    action.yaw(), action.muzzleOffset());
            case SPAWN_TALLYHO_ENTITY -> TallyhoCompat.spawnEntity(level, target(level, anchor, action, ships),
                    action.positionOffset(), action.tallyhoEntity(), action.yaw(), action.tallyhoVariant(),
                    action.tallyhoState());
            case GENERIC_BLOCK_INTERACTION -> interact(level, anchor, player, action, ships);
            case GENERIC_BLOCK_LEFT_CLICK -> leftClick(level, anchor, player, action, ships);
        };
    }

    private static BlockPos target(Level level, BlockPos anchor, VehicleSetupAction action,
                                   @Nullable Map<Long, Object> ships) {
        if (ships != null && action.targetShipId() >= 0L && action.shipOffset() != null) {
            Object ship = ships.get(action.targetShipId());
            BlockPos resolved = ship == null ? null : VehicleSetupReflection.positionOnShip(ship, action.shipOffset());
            if (resolved != null) return resolved;
        }
        return anchor.offset(action.targetOffset());
    }

    @Nullable private static String remove(Level level, BlockPos pos) {
        return level.getBlockState(pos).isAir() || level.removeBlock(pos, false) ? null : "could not remove block";
    }

    @Nullable private static String place(Level level, BlockPos pos, @Nullable CompoundTag savedState) {
        if (savedState == null) return "recorded block state is missing";
        BlockState state = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), savedState);
        if (state.isAir()) return "recorded block state is invalid";
        return level.getBlockState(pos).equals(state) || level.setBlock(pos, state, 3) ? null : "could not place shaft";
    }

    @Nullable private static String interact(Level level, BlockPos anchor, @Nullable ServerPlayer player,
                                             VehicleSetupAction action, @Nullable Map<Long, Object> ships) {
        if (player == null) return "block interaction requires the schematic placer to be online";
        CompoundTag savedItem = action.interactionItem();
        if (savedItem == null || action.targetOffset() == null) return "recorded block interaction is missing data";
        ItemStack stack = ItemStack.of(savedItem);
        BlockPos pos = target(level, anchor, action, ships);
        if (level.getBlockState(pos).isAir()) return "interaction target block is missing";
        Vec3 hitLocation = Vec3.atLowerCornerOf(pos).add(action.positionOffset());
        BlockHitResult hit = new BlockHitResult(hitLocation, action.interactionFace(), pos, false);
        InteractionHand hand = action.interactionHand();
        ItemStack original = player.getItemInHand(hand);
        boolean originalSneaking = player.isShiftKeyDown();
        player.setItemInHand(hand, stack);
        player.setShiftKeyDown(action.interactionSneaking());
        VehicleSetupRecordingManager.beginInteractionReplay(player);
        try {
            PlayerInteractEvent.RightClickBlock interaction = new PlayerInteractEvent.RightClickBlock(
                    player, hand, pos, hit);
            boolean canceled = MinecraftForge.EVENT_BUS.post(interaction);
            InteractionResult result = canceled ? interaction.getCancellationResult() : InteractionResult.PASS;
            if (!result.consumesAction()) result = level.getBlockState(pos).use(level, player, hand, hit);
            if (!result.consumesAction()) result = stack.useOn(new net.minecraft.world.item.context.UseOnContext(player, hand, hit));
            return result.consumesAction() ? null : "recorded block interaction was not accepted";
        } catch (Throwable throwable) {
            return "recorded block interaction failed: " + throwable.getClass().getSimpleName();
        } finally {
            VehicleSetupRecordingManager.endInteractionReplay(player);
            player.setItemInHand(hand, original);
            player.setShiftKeyDown(originalSneaking);
        }
    }

    @Nullable private static String leftClick(Level level, BlockPos anchor, @Nullable ServerPlayer player,
                                              VehicleSetupAction action, @Nullable Map<Long, Object> ships) {
        if (player == null) return "block left-click requires the schematic placer to be online";
        CompoundTag savedItem = action.interactionItem();
        if (savedItem == null || action.targetOffset() == null) return "recorded block left-click is missing data";
        ItemStack stack = ItemStack.of(savedItem);
        BlockPos pos = target(level, anchor, action, ships);
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return "left-click target block is missing";
        ItemStack original = player.getMainHandItem();
        boolean originalSneaking = player.isShiftKeyDown();
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        player.setShiftKeyDown(action.interactionSneaking());
        VehicleSetupRecordingManager.beginInteractionReplay(player);
        try {
            PlayerInteractEvent.LeftClickBlock interaction = new PlayerInteractEvent.LeftClickBlock(
                    player, pos, action.interactionFace(), PlayerInteractEvent.LeftClickBlock.Action.START);
            boolean canceled = MinecraftForge.EVENT_BUS.post(interaction);
            if (canceled) return null;
            if (interaction.getUseBlock() != net.minecraftforge.eventbus.api.Event.Result.DENY) {
                state.attack(level, pos, player);
            }
            if (interaction.getUseItem() != net.minecraftforge.eventbus.api.Event.Result.DENY) {
                stack.onBlockStartBreak(pos, player);
            }
            return level.getBlockState(pos).equals(state) ? null : "left-click changed or removed the target block";
        } catch (Throwable throwable) {
            return "recorded block left-click failed: " + throwable.getClass().getSimpleName();
        } finally {
            VehicleSetupRecordingManager.endInteractionReplay(player);
            player.setItemInHand(InteractionHand.MAIN_HAND, original);
            player.setShiftKeyDown(originalSneaking);
        }
    }

    @Nullable private static String controller(@Nullable ServerPlayer player, VehicleSetupAction action,
                                                @Nullable Map<Long, Object> ships) {
        if (!ModList.get().isLoaded("create_tweaked_controllers")) return "Create Tweaked Controllers is not installed";
        if (!ModList.get().isLoaded("drivebywire")) return "Drive By Wire is not installed";
        if (player == null) return "the schematic placer is offline";
        CompoundTag savedController = action.controller();
        if (savedController == null || action.targetOffset() == null || ships == null) return "recorded controller mapping is missing";
        Object ship = ships.get(action.targetShipId());
        BlockPos hub = ship == null ? null : VehicleSetupReflection.positionOnShip(ship, action.targetOffset());
        if (hub == null) return "controller hub ship could not be resolved";
        Item item = BuiltInRegistries.ITEM.get(TWEAKED_CONTROLLER);
        if (item == Items.AIR) return "tweaked controller item is unavailable";
        ItemStack stack = ItemStack.of(savedController);
        if (!stack.is(item)) return "recorded controller is incompatible";
        stack.getOrCreateTag().putLong("Hub", hub.asLong());
        if (!player.getInventory().add(stack)) player.drop(stack, false);
        return null;
    }
}
