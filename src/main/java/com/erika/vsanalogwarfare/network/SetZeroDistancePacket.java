package com.erika.vsanalogwarfare.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record SetZeroDistancePacket(BlockPos scopePos, int zeroDistance, boolean highAngleZero) {

    public static void encode(SetZeroDistancePacket packet, FriendlyByteBuf buf) {
        buf.writeBlockPos(packet.scopePos);
        buf.writeInt(packet.zeroDistance);
        buf.writeBoolean(packet.highAngleZero);
    }

    public static SetZeroDistancePacket decode(FriendlyByteBuf buf) {
        return new SetZeroDistancePacket(buf.readBlockPos(), buf.readInt(), buf.readBoolean());
    }

    public static void handle(SetZeroDistancePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
            BlockEntity be = sender.level().getBlockEntity(packet.scopePos);
            if (be instanceof com.erika.vsanalogwarfare.scope.ScopeBlockEntity scope) {
                scope.setZeroDistance(packet.zeroDistance());
                scope.setHighAngleZero(packet.highAngleZero());
            }
        });
        context.setPacketHandled(true);
    }
}