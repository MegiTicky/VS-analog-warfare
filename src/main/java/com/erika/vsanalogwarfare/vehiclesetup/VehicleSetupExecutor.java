package com.erika.vsanalogwarfare.vehiclesetup;

import com.erika.vsanalogwarfare.vehiclesetup.compat.TrackworkCompat;
import com.erika.vsanalogwarfare.vehiclesetup.compat.VehicleSetupReflection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.ModList;

import javax.annotation.Nullable;
import java.util.Map;

public final class VehicleSetupExecutor {
    private static final ResourceLocation TWEAKED_CONTROLLER =
            new ResourceLocation("create_tweaked_controllers", "tweaked_linked_controller");

    private VehicleSetupExecutor() { }

    @Nullable
    public static String run(Level level, BlockPos anchor, @Nullable ServerPlayer player,
                             VehicleSetupAction action) {
        return run(level, anchor, player, action, null);
    }

    @Nullable
    public static String run(Level level, BlockPos anchor, @Nullable ServerPlayer player,
                             VehicleSetupAction action, @Nullable Map<Long, Object> ships) {
        return switch (action.type()) {
            case PLACE_BLOCK -> place(level, target(anchor, action, ships), action.blockState());
            case REMOVE_BLOCK -> remove(level, target(anchor, action, ships));
            case LINK_DBW_BACKUPS -> "DBW cross-ship links require VMod placement";
            case CREATE_TWEAKED_CONTROLLER -> controller(level, anchor, player, action, ships);
            case SET_TRACKWORK_STIFFNESS -> TrackworkCompat.setStiffness(level, anchor, action.stiffness());
        };
    }

    private static BlockPos target(BlockPos anchor, VehicleSetupAction action,
                                   @Nullable Map<Long, Object> ships) {
        if (ships != null && action.targetShipId() >= 0L && action.shipOffset() != null) {
            Object ship = ships.get(action.targetShipId());
            BlockPos resolved = ship == null ? null
                    : VehicleSetupReflection.positionOnShip(ship, action.shipOffset());
            if (resolved != null) return resolved;
        }
        return action.targetOffset() == null ? anchor : anchor.offset(action.targetOffset());
    }

    @Nullable
    private static String remove(Level level, BlockPos pos) {
        return level.getBlockState(pos).isAir() || level.removeBlock(pos, false)
                ? null : "could not remove block";
    }

    @Nullable
    private static String place(Level level, BlockPos pos, @Nullable CompoundTag savedState) {
        if (savedState == null) return "recorded block state is missing";
        BlockState state = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), savedState);
        if (state.isAir()) return "recorded block state is invalid";
        return level.getBlockState(pos).equals(state) || level.setBlock(pos, state, 3)
                ? null : "could not place block";
    }

    @Nullable
    private static String controller(Level level, BlockPos anchor, @Nullable ServerPlayer player,
                                     VehicleSetupAction action,
                                     @Nullable Map<Long, Object> ships) {
        if (!ModList.get().isLoaded("create_tweaked_controllers")) {
            return "Create Tweaked Controllers is not installed";
        }
        if (!ModList.get().isLoaded("drivebywire")) return "Drive By Wire is not installed";
        if (player == null) return "the schematic placer is offline";
        CompoundTag savedController = action.controller();
        if (savedController == null || action.targetOffset() == null) {
            return "recorded controller mapping is missing";
        }
        Object ship = ships == null ? VehicleSetupReflection.findShip(level, anchor)
                : ships.get(action.targetShipId());
        BlockPos hub = ship == null ? null
                : VehicleSetupReflection.positionOnShip(ship, action.targetOffset());
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
