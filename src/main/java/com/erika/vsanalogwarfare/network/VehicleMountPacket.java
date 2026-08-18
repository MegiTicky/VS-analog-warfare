package com.erika.vsanalogwarfare.network;

import com.erika.vsanalogwarfare.vehiclemount.VehicleMountHandleBlockEntity;
import com.erika.vsanalogwarfare.vehiclemount.VehicleMountManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import com.simibubi.create.content.contraptions.actors.seat.SeatBlock;
import com.erika.vsanalogwarfare.vehiclemount.VehicleMountSeatLink;
import java.util.UUID;
import net.minecraftforge.network.NetworkEvent;
import com.erika.vsanalogwarfare.client.VehicleMountRoleScreen;
import com.erika.vsanalogwarfare.client.VehicleMountSelectionScreen;

import java.util.List;
import java.util.function.Supplier;

public final class VehicleMountPacket {
    private VehicleMountPacket() { }

    public record OpenRoleName(BlockPos handle, UUID seat, BlockPos seatPos, long shipId, BlockPos shipOffset) {
        public static void encode(OpenRoleName packet, FriendlyByteBuf buf) { buf.writeBlockPos(packet.handle); buf.writeUUID(packet.seat); buf.writeBlockPos(packet.seatPos); buf.writeLong(packet.shipId); buf.writeBlockPos(packet.shipOffset); }
        public static OpenRoleName decode(FriendlyByteBuf buf) { return new OpenRoleName(buf.readBlockPos(), buf.readUUID(), buf.readBlockPos(), buf.readLong(), buf.readBlockPos()); }
        public static void handle(OpenRoleName packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> Minecraft.getInstance().setScreen(new VehicleMountRoleScreen(packet.handle, packet.seat, packet.seatPos, packet.shipId, packet.shipOffset)));
            context.setPacketHandled(true);
        }
    }

    public record Link(BlockPos handle, UUID seat, BlockPos seatPos, long shipId, BlockPos shipOffset, String role) {
        public static void encode(Link packet, FriendlyByteBuf buf) { buf.writeBlockPos(packet.handle); buf.writeUUID(packet.seat); buf.writeBlockPos(packet.seatPos); buf.writeLong(packet.shipId); buf.writeBlockPos(packet.shipOffset); buf.writeUtf(packet.role, 32); }
        public static Link decode(FriendlyByteBuf buf) { return new Link(buf.readBlockPos(), buf.readUUID(), buf.readBlockPos(), buf.readLong(), buf.readBlockPos(), buf.readUtf(32)); }
        public static void handle(Link packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null || packet.role.isBlank() || packet.role.length() > 32) return;
                if (!(player.level().getBlockEntity(packet.handle) instanceof VehicleMountHandleBlockEntity handle)) {
                    player.displayClientMessage(Component.literal("The selected vehicle mount handle is unavailable."), true);
                    return;
                }
                if (!(player.level().getBlockState(packet.seatPos).getBlock() instanceof SeatBlock)) {
                    player.displayClientMessage(Component.literal("The selected Create seat is unavailable."), true);
                    return;
                }
                handle.addSeat(new VehicleMountSeatLink(packet.role, packet.seat, packet.seatPos, packet.shipId, packet.shipOffset, packet.handle));
                player.getMainHandItem().getOrCreateTag().remove("VehicleMountHandle");
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("Linked " + packet.role + " to the vehicle mount handle."), true);
            });
            context.setPacketHandled(true);
        }
    }

    public record Request(BlockPos handle, int revision, int index) {
        public static void encode(Request packet, FriendlyByteBuf buf) { buf.writeBlockPos(packet.handle); buf.writeVarInt(packet.revision); buf.writeVarInt(packet.index); }
        public static Request decode(FriendlyByteBuf buf) { return new Request(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt()); }
        public static void handle(Request packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null) return;
                if (packet.revision >= 0 && player.level().getBlockEntity(packet.handle) instanceof VehicleMountHandleBlockEntity handle
                        && handle.revision() != packet.revision) {
                    player.displayClientMessage(Component.literal("The vehicle mount seats changed. Open the handle again."), true);
                    return;
                }
                VehicleMountManager.mount(player, packet.handle, packet.index);
            });
            context.setPacketHandled(true);
        }
    }

    public record Dismount() {
        public static void encode(Dismount packet, FriendlyByteBuf buf) { }
        public static Dismount decode(FriendlyByteBuf buf) { return new Dismount(); }
        public static void handle(Dismount packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> { ServerPlayer player = context.getSender(); if (player != null) VehicleMountManager.dismount(player); });
            context.setPacketHandled(true);
        }
    }

    public record OpenSelection(BlockPos handle, int revision, List<String> roles) {
        public static void encode(OpenSelection packet, FriendlyByteBuf buf) { buf.writeBlockPos(packet.handle); buf.writeVarInt(packet.revision); buf.writeCollection(packet.roles, FriendlyByteBuf::writeUtf); }
        public static OpenSelection decode(FriendlyByteBuf buf) { return new OpenSelection(buf.readBlockPos(), buf.readVarInt(), buf.readList(FriendlyByteBuf::readUtf)); }
        public static void handle(OpenSelection packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> Minecraft.getInstance().setScreen(new VehicleMountSelectionScreen(packet.handle, packet.revision, packet.roles)));
            context.setPacketHandled(true);
        }
    }
}
