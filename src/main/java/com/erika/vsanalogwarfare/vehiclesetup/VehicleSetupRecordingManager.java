package com.erika.vsanalogwarfare.vehiclesetup;

import com.erika.vsanalogwarfare.VSAnalogWarfare;
import com.erika.vsanalogwarfare.registry.ModBlocks;
import com.erika.vsanalogwarfare.vehiclesetup.compat.TrackworkCompat;
import com.erika.vsanalogwarfare.vehiclesetup.compat.VehicleSetupShipPosition;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = VSAnalogWarfare.MOD_ID)
public final class VehicleSetupRecordingManager {
    private static final Map<UUID, BlockPos> ACTIVE_RECORDINGS = new HashMap<>();

    private VehicleSetupRecordingManager() { }

    public static void toggle(ServerPlayer player, VehicleSetupBlockEntity setup) {
        UUID playerId = player.getUUID();
        BlockPos current = ACTIVE_RECORDINGS.get(playerId);
        if (setup.getBlockPos().equals(current)) {
            ACTIVE_RECORDINGS.remove(playerId);
            player.displayClientMessage(Component.literal("Vehicle setup recording stopped: " + setup.actionSummary() + "."), true);
            return;
        }
        setup.clearActions();
        ACTIVE_RECORDINGS.put(playerId, setup.getBlockPos());
        player.displayClientMessage(Component.literal("Vehicle setup recording started. Place or break blocks normally, then use the recorder on this block again to stop."), true);
    }

    public static void inspect(ServerPlayer player, VehicleSetupBlockEntity setup) {
        player.displayClientMessage(Component.literal("Vehicle setup: " + setup.actionSummary() + "."), true);
    }

    public static void recordDbwLink(ServerPlayer player, BlockPos source, BlockPos target) {
        VehicleSetupBlockEntity setup = activeSetup(player);
        if (setup == null) return;
        VehicleSetupShipPosition sourcePosition = VehicleSetupShipPosition.at(player.level(), source);
        VehicleSetupShipPosition targetPosition = VehicleSetupShipPosition.at(player.level(), target);
        if (sourcePosition == null || targetPosition == null) {
            player.displayClientMessage(Component.literal("Vehicle setup could not record the DBW relink: both backups must be on loaded ships."), true);
            return;
        }
        setup.addAction(VehicleSetupAction.linkDbwBackups(
                sourcePosition.shipId(), sourcePosition.offset(),
                targetPosition.shipId(), targetPosition.offset()));
        player.displayClientMessage(Component.literal("Vehicle setup recorded DBW relink: " + setup.actionSummary() + "."), true);
    }

    public static void recordTrackworkStiffness(net.minecraft.world.entity.player.Player player,
                                                BlockPos clicked, ItemStack stack) {
        if (!(player instanceof ServerPlayer serverPlayer) || !TrackworkCompat.isStiffnessTool(stack)
                || !TrackworkCompat.isStiffnessTarget(serverPlayer.level(), clicked)) return;
        VehicleSetupBlockEntity setup = activeSetup(serverPlayer);
        if (setup == null) return;
        Float stiffness = TrackworkCompat.readStiffness(serverPlayer.level(), clicked);
        if (stiffness == null) return;
        setup.addAction(VehicleSetupAction.setTrackworkStiffness(stiffness));
        serverPlayer.displayClientMessage(Component.literal(
                "Vehicle setup recorded suspension stiffness " + stiffness + "x: " + setup.actionSummary() + "."), true);
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        recordPlace(player, event.getPos(), event.getPlacedBlock());
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || event.getState().isAir()) return;
        recordRemove(player, event.getPos());
    }

    private static void recordPlace(ServerPlayer player, BlockPos pos, BlockState state) {
        VehicleSetupBlockEntity setup = activeSetup(player);
        if (setup == null || state.is(ModBlocks.VEHICLE_SETUP.get())) return;
        setup.addAction(VehicleSetupAction.placeBlock(pos.subtract(setup.getBlockPos()), state));
        player.displayClientMessage(Component.literal("Recorded placement: " + setup.actionSummary() + "."), true);
    }

    private static void recordRemove(ServerPlayer player, BlockPos pos) {
        VehicleSetupBlockEntity setup = activeSetup(player);
        if (setup == null || pos.equals(setup.getBlockPos())) return;
        setup.addAction(VehicleSetupAction.removeBlock(pos.subtract(setup.getBlockPos())));
        player.displayClientMessage(Component.literal("Recorded removal: " + setup.actionSummary() + "."), true);
    }

    private static VehicleSetupBlockEntity activeSetup(ServerPlayer player) {
        BlockPos anchor = ACTIVE_RECORDINGS.get(player.getUUID());
        if (anchor == null) return null;
        Level level = player.level();
        if (level.getBlockEntity(anchor) instanceof VehicleSetupBlockEntity setup) return setup;
        ACTIVE_RECORDINGS.remove(player.getUUID());
        return null;
    }
}
