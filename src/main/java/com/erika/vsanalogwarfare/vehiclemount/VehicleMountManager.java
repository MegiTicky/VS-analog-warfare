package com.erika.vsanalogwarfare.vehiclemount;

import com.erika.vsanalogwarfare.VSAnalogWarfare;
import com.erika.vsanalogwarfare.vehiclesetup.AnalogScrewdriverItem;
import com.erika.vsanalogwarfare.vehiclesetup.compat.VehicleSetupShipPosition;
import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = VSAnalogWarfare.MOD_ID)
public final class VehicleMountManager {
    private static final Map<UUID, BlockPos> ACTIVE_MOUNTS = new ConcurrentHashMap<>();

    private VehicleMountManager() { }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof ServerPlayer player)
                || !(event.getItemStack().getItem() instanceof AnalogScrewdriverItem)
                || !(player.level().getBlockEntity(event.getPos()) instanceof VehicleMountHandleBlockEntity)) return;
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        handleSeatLink(event, event.getTarget());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        handleSeatLink(event, event.getTarget());
    }

    private static void handleSeatLink(PlayerInteractEvent event, Entity target) {
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof ServerPlayer player)
                || !player.isShiftKeyDown() || !(target instanceof SeatEntity seat)
                || !(event.getItemStack().getItem() instanceof AnalogScrewdriverItem)) return;
        if (tryOpenSeatRoleName(player, event.getItemStack(), seat.getUUID(), seat.blockPosition())) {
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.CONSUME);
        }
    }

    public static boolean tryOpenSeatRoleName(ServerPlayer player, ItemStack screwdriver, UUID seatUuid, BlockPos seatPos) {
        long handleLong = screwdriver.getOrCreateTag().getLong("VehicleMountHandle");
        if (handleLong == 0L) return false;
        BlockPos handlePos = BlockPos.of(handleLong);
        BlockEntity entity = player.level().getBlockEntity(handlePos);
        if (!(entity instanceof VehicleMountHandleBlockEntity handle)) return false;
        if (handle.locked()) {
            player.displayClientMessage(Component.literal("This vehicle mount handle is locked."), true);
            return true;
        }
        VehicleSetupShipPosition seatPosition = VehicleSetupShipPosition.at(player.level(), seatPos);
        if (seatPosition == null) {
            player.displayClientMessage(Component.literal("The seat must be on a loaded ship."), true);
            return true;
        }
        com.erika.vsanalogwarfare.network.ModNetwork.sendToPlayer(player,
                new com.erika.vsanalogwarfare.network.VehicleMountPacket.OpenRoleName(handlePos, seatUuid, seatPos,
                        seatPosition.shipId(), seatPosition.offset()));
        return true;
    }

    public static boolean tryOpenSeatRoleName(ServerPlayer player, ItemStack screwdriver, BlockPos seatPos) {
        SeatEntity seat = player.level().getEntitiesOfClass(SeatEntity.class, new AABB(seatPos)).stream().findFirst().orElse(null);
        return tryOpenSeatRoleName(player, screwdriver, seat == null ? UUID.randomUUID() : seat.getUUID(), seatPos);
    }

    public static void mount(ServerPlayer player, BlockPos handlePos, int index) {
        if (!(player.level().getBlockEntity(handlePos) instanceof VehicleMountHandleBlockEntity handle)) {
            player.displayClientMessage(Component.literal("That vehicle mount handle is unavailable."), true);
            return;
        }
        if (handle.locked()) {
            player.displayClientMessage(Component.literal("This vehicle mount handle is locked."), true);
            return;
        }
        if (index < 0) {
            if (handle.seats().size() == 1) index = 0;
            else {
                com.erika.vsanalogwarfare.network.ModNetwork.sendToPlayer(player,
                        new com.erika.vsanalogwarfare.network.VehicleMountPacket.OpenSelection(handlePos, handle.revision(),
                                handle.seats().stream().map(VehicleMountSeatLink::role).toList()));
                return;
            }
        }
        if (index < 0 || index >= handle.seats().size()) {
            player.displayClientMessage(Component.literal("That vehicle mount seat is no longer available."), true);
            return;
        }
        VehicleMountSeatLink link = handle.seats().get(index);
        Entity seat = link.resolve(player.level(), handle);
        if (seat == null) seat = link.createSeat(player.level(), handle);
        if (!(seat instanceof SeatEntity) || !seat.isAlive()) {
            player.displayClientMessage(Component.literal("That linked seat is unavailable."), true);
            return;
        }
        if (!seat.getPassengers().isEmpty()) {
            player.displayClientMessage(Component.literal("That seat is occupied."), true);
            return;
        }
        if (!player.startRiding(seat, true)) {
            player.displayClientMessage(Component.literal("Could not mount that seat."), true);
        } else {
            ACTIVE_MOUNTS.put(player.getUUID(), handlePos);
        }
    }

    public static void dismount(ServerPlayer player) {
        Entity vehicle = player.getVehicle();
        if (!(vehicle instanceof SeatEntity seat)) return;
        BlockPos activeHandlePos = ACTIVE_MOUNTS.remove(player.getUUID());
        if (activeHandlePos != null && player.level().getBlockEntity(activeHandlePos) instanceof VehicleMountHandleBlockEntity handle) {
            player.stopRiding();
            teleportToHandle(player, handle);
            return;
        }
        for (VehicleMountSeatLink link : findLinks(player, seat.getUUID())) {
            BlockPos handlePos = link.handlePos();
            if (!(player.level().getBlockEntity(handlePos) instanceof VehicleMountHandleBlockEntity handle)) continue;
            player.stopRiding();
            teleportToHandle(player, handle);
            return;
        }
    }

    private static void teleportToHandle(ServerPlayer player, VehicleMountHandleBlockEntity handle) {
        Direction facing = handle.getBlockState().getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING);
        Vec3 target = handle.currentWorldPosition().add(facing.getStepX() * 0.8, 0.2, facing.getStepZ() * 0.8);
        player.teleportTo(target.x, target.y, target.z);
    }

    private static java.util.List<VehicleMountSeatLink> findLinks(ServerPlayer player, UUID seatUuid) {
        java.util.List<VehicleMountSeatLink> links = new java.util.ArrayList<>();
        for (Entity candidate : player.level().getEntitiesOfClass(Entity.class, player.getBoundingBox().inflate(128.0))) {
            if (!(candidate instanceof SeatEntity seat) || !seat.getUUID().equals(seatUuid)) continue;
            for (VehicleMountHandleBlockEntity handle : nearbyHandles(player)) {
                for (VehicleMountSeatLink link : handle.seats()) if (link.seatUuid().equals(seatUuid)) links.add(link);
            }
        }
        return links;
    }

    private static java.util.List<VehicleMountHandleBlockEntity> nearbyHandles(ServerPlayer player) {
        java.util.List<VehicleMountHandleBlockEntity> handles = new java.util.ArrayList<>();
        BlockPos origin = player.blockPosition();
        for (int x = -128; x <= 128; x += 16) for (int y = -128; y <= 128; y += 16) for (int z = -128; z <= 128; z += 16) {
            BlockPos pos = origin.offset(x, y, z);
            if (player.level().getBlockEntity(pos) instanceof VehicleMountHandleBlockEntity handle) handles.add(handle);
        }
        return handles;
    }
}
